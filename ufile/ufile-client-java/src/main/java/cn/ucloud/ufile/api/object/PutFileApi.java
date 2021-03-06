package cn.ucloud.ufile.api.object;

import cn.ucloud.ufile.UfileConstants;
import cn.ucloud.ufile.api.object.policy.PutPolicy;
import cn.ucloud.ufile.auth.ObjectAuthorizer;
import cn.ucloud.ufile.auth.ObjectOptAuthParam;
import cn.ucloud.ufile.bean.PutObjectResultBean;
import cn.ucloud.ufile.bean.UfileErrorBean;
import cn.ucloud.ufile.exception.*;
import cn.ucloud.ufile.http.BaseHttpCallback;
import cn.ucloud.ufile.http.HttpClient;
import cn.ucloud.ufile.http.OnProgressListener;
import cn.ucloud.ufile.http.ProgressConfig;
import cn.ucloud.ufile.http.request.PutFileRequestBuilder;
import cn.ucloud.ufile.util.*;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import okhttp3.MediaType;
import okhttp3.Response;

import java.io.*;
import java.security.NoSuchAlgorithmException;
import java.util.Date;

/**
 * API-Put上传小文件
 *
 * @author: joshua
 * @E-mail: joshua.yin@ucloud.cn
 * @date: 2018/11/12 19:08
 */
public class PutFileApi extends UfileObjectApi<PutObjectResultBean> {
    /**
     * Required
     * 云端对象名称
     */
    protected String keyName;
    /**
     * Required
     * 要上传的文件
     */
    private File file;
    /**
     * Required
     * 要上传的文件mimeType
     */
    protected String mimeType;
    /**
     * Required
     * 根据MimeType解析成okhttp可用的mediaType，解析失败则代表mimeType无效
     */
    protected MediaType mediaType;
    /**
     * Required
     * Bucket空间名称
     */
    protected String bucketName;
    /**
     * 是否需要上传MD5校验码
     */
    private boolean isVerifyMd5 = true;

    /**
     * 进度回调设置
     */
    private ProgressConfig progressConfig;

    /**
     * 流写入的buffer大小，Default = 256 KB
     */
    private int bufferSize = UfileConstants.DEFAULT_BUFFER_SIZE;

    /**
     * UFile上传回调策略
     */
    private PutPolicy putPolicy;

    /**
     * 构造方法
     *
     * @param authorizer Object授权器
     * @param host       API域名
     * @param httpClient Http客户端
     */
    protected PutFileApi(ObjectAuthorizer authorizer, String host, HttpClient httpClient) {
        super(authorizer, host, httpClient);
        progressConfig = ProgressConfig.callbackDefault();
    }

    /**
     * 设置上传到云端的对象名称
     *
     * @param keyName 对象名称
     * @return {@link PutFileApi}
     */
    public PutFileApi nameAs(String keyName) {
        this.keyName = keyName;
        return this;
    }

    /**
     * 设置要上传的文件和类型
     *
     * @param file     需上传的文件
     * @param mimeType 需上传文件的MIME类型，可以通过MimeTypeUtil.getMimeType(File)来获取，也可用户自定义输入
     * @return {@link PutFileApi}
     */
    public PutFileApi from(File file, String mimeType) {
        this.file = file;
        this.mimeType = mimeType;
        this.mediaType = MediaType.parse(mimeType);
        return this;
    }

    /**
     * 设置要上传到的Bucket名称
     *
     * @param bucketName bucket名称
     * @return {@link PutFileApi}
     */
    public PutFileApi toBucket(String bucketName) {
        this.bucketName = bucketName;
        return this;
    }

    /**
     * 设置是否需要MD5校验
     *
     * @param isVerifyMd5 是否校验MD5
     * @return {@link PutFileApi}
     */
    public PutFileApi withVerifyMd5(boolean isVerifyMd5) {
        this.isVerifyMd5 = isVerifyMd5;
        return this;
    }

    /**
     * 配置进度回调设置
     *
     * @param config 进度回调设置
     * @return {@link PutFileApi}
     */
    public PutFileApi withProgressConfig(ProgressConfig config) {
        progressConfig = config == null ? this.progressConfig : config;
        return this;
    }

    /**
     * 配置签名可选参数
     *
     * @param authOptionalData 签名可选参数
     * @return
     */
    public PutFileApi withAuthOptionalData(JsonElement authOptionalData) {
        this.authOptionalData = authOptionalData;
        return this;
    }

    /**
     * 设置流读写的Buffer大小，默认 256 KB
     *
     * @param bufferSize Buffer大小
     * @return {@link PutFileApi}
     */
    public PutFileApi setBufferSize(int bufferSize) {
        this.bufferSize = bufferSize;
        return this;
    }

    /**
     * 设置上传回调策略
     *
     * @param putPolicy 上传回调策略
     * @return {@link PutFileApi}
     */
    public PutFileApi withPutPolicy(PutPolicy putPolicy) {
        this.putPolicy = putPolicy;
        return this;
    }

    @Override
    protected void prepareData() throws UfileClientException {
        parameterValidat();
        if (!file.exists())
            throw new UfileFileException("File is inexistent!");

        if (!file.isFile())
            throw new UfileFileException("It is not a file!");

        if (!file.canRead())
            throw new UfileFileException("File is not readable!");

        String contentType = mediaType.toString();
        String contentMD5 = "";
        String date = dateFormat.format(new Date(System.currentTimeMillis()));

        PutFileRequestBuilder builder = (PutFileRequestBuilder) new PutFileRequestBuilder(onProgressListener)
                .setBufferSize(bufferSize)
                .setConnTimeOut(connTimeOut).setReadTimeOut(readTimeOut).setWriteTimeOut(writeTimeOut)
                .baseUrl(generateFinalHost(bucketName, keyName))
                .addHeader("Content-Type", contentType)
                .addHeader("Accpet", "*/*")
                .addHeader("Content-Length", String.valueOf(file.length()))
                .addHeader("Date", date)
                .mediaType(mediaType);

        if (isVerifyMd5) {
            try {
                contentMD5 = HexFormatter.formatByteArray2HexString(Encoder.md5(file), false);
                builder.addHeader("Content-MD5", contentMD5);
            } catch (IOException | NoSuchAlgorithmException e) {
                e.printStackTrace();
            }
        }

        String authorization = authorizer.authorization((ObjectOptAuthParam) new ObjectOptAuthParam(HttpMethod.PUT, bucketName, keyName,
                contentType, contentMD5, date).setPutPolicy(putPolicy).setOptional(authOptionalData));

        builder.addHeader("authorization", authorization);

        builder.params(file);
        builder.setProgressConfig(progressConfig);

        call = builder.build(httpClient.getOkHttpClient());
    }

    @Override
    protected void parameterValidat() throws UfileParamException {
        if (file == null)
            throw new UfileRequiredParamNotFoundException(
                    "The required param 'file' can not be null");

        if (keyName == null || keyName.isEmpty())
            throw new UfileRequiredParamNotFoundException(
                    "The required param 'keyName' can not be null or empty");

        if (mimeType == null || mimeType.isEmpty())
            throw new UfileRequiredParamNotFoundException(
                    "The required param 'mimeType' can not be null or empty");

        if (mediaType == null)
            throw new UfileParamException(
                    "The required param 'mimeType' is invalid");

        if (bucketName == null || bucketName.isEmpty())
            throw new UfileRequiredParamNotFoundException(
                    "The required param 'bucketName' can not be null or empty");
    }

    private OnProgressListener onProgressListener;

    /**
     * 配置进度监听器
     * 该配置可供execute()同步接口回调进度使用，若使用executeAsync({@link BaseHttpCallback})，则后配置的会覆盖新配置的
     *
     * @param onProgressListener 进度监听器
     * @return {@link PutFileApi}
     */
    public PutFileApi setOnProgressListener(OnProgressListener onProgressListener) {
        this.onProgressListener = onProgressListener;
        return this;
    }

    @Override
    public void executeAsync(BaseHttpCallback<PutObjectResultBean, UfileErrorBean> callback) {
        onProgressListener = callback;
        super.executeAsync(callback);
    }

    @Override
    public PutObjectResultBean parseHttpResponse(Response response) {
        PutObjectResultBean result = new PutObjectResultBean();
        String eTag = response.header("ETag");
        eTag = eTag == null ? null : eTag.replace("\"", "");
        result.seteTag(eTag);

        if (putPolicy != null) {
            result.setCallbackRet(readResponseBody(response));
        }

        return result;
    }

    @Override
    public UfileErrorBean parseErrorResponse(Response response) throws UfileClientException {
        UfileErrorBean errorBean = null;
        if (putPolicy != null) {
            String content = readResponseBody(response);
            response.body().close();
            try {
                errorBean = new Gson().fromJson((content == null || content.length() == 0) ? "{}" : content, UfileErrorBean.class);
            } catch (Exception e) {
                errorBean = new UfileErrorBean();
            }
            errorBean.setResponseCode(response.code());
            errorBean.setxSessionId(response.header("X-SessionId"));
            errorBean.setCallbackRet(content);
            return errorBean;
        } else {
            errorBean = super.parseErrorResponse(response);
        }
        return errorBean;
    }
}

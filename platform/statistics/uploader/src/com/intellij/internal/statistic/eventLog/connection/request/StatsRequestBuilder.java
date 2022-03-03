// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.connection.request;

import com.intellij.internal.statistic.config.StatisticsStringUtil;
import com.intellij.internal.statistic.eventLog.connection.EventLogConnectionSettings;
import org.jetbrains.annotations.NotNull;

import javax.net.ssl.SSLContext;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.*;
import java.util.zip.GZIPOutputStream;

public class StatsRequestBuilder {
  private static final int SUCCESS_CODE = 200;
  private static final List<Integer> CAN_RETRY_CODES = Arrays.asList(
    // Standard status codes
    408, // Request Timeout
    429, // Too Many Requests (RFC 6585)
    500, // Internal Server Error
    502, // Bad Gateway
    503, // Service Unavailable
    504, // Gateway Timeout
    // Unofficial codes
    598 // Some HTTP Proxies timeout
  );
  private static final int MAX_RETRIES = 1;
  private static final int RETRY_INTERVAL = 500;

  private final String myUserAgent;
  private final StatsProxyInfo myProxyInfo;
  private final SSLContext mySSLContext;
  private final Map<String, String> myExtraHeaders;

  private final String myUrl;
  private final String myMethod;
  private String myContent;
  private String myContentType;
  private Charset myCharset;

  private StatsResponseHandler onSucceed;
  private StatsResponseHandler onFail;

  public StatsRequestBuilder(@NotNull String method, @NotNull String url, @NotNull EventLogConnectionSettings settings) {
    myMethod = method;
    myUrl = url;
    myUserAgent = settings.getUserAgent();
    myProxyInfo = settings.selectProxy(myUrl);
    mySSLContext = settings.getSSLContext();
    myExtraHeaders = settings.getExtraHeaders();
  }

  @NotNull
  public StatsRequestBuilder withBody(@NotNull String body, @NotNull String contentType, @NotNull Charset charset) {
    if (StatisticsStringUtil.isEmptyOrSpaces(body)) {
      throw new EmptyHttpRequestBody();
    }
    myContent = body;
    myContentType = contentType;
    myCharset = charset;
    return this;
  }

  @NotNull
  public StatsRequestBuilder fail(@NotNull StatsResponseHandler processor) {
    onFail = processor;
    return this;
  }

  @NotNull
  public StatsRequestBuilder succeed(@NotNull StatsResponseHandler processor) {
    onSucceed = processor;
    return this;
  }

  public void send() throws IOException, StatsResponseException {
    send(response -> {
      if (onSucceed != null) {
        onSucceed.handle(response, SUCCESS_CODE);
      }
      return true;
    });
  }

  public <T> StatsRequestResult<T> send(StatsResponseProcessor<? extends T> processor) throws IOException, StatsResponseException {
    HttpClient client = newClient();
    HttpRequest request = newRequest();

    HttpResponse<String> response = trySend(client, request);
    int code = response.statusCode();
    if (code == SUCCESS_CODE) {
      T result = processor.onSucceed(new StatsHttpResponse(response, code));
      return StatsRequestResult.succeed(result);
    }
    else {
      if (onFail != null) {
        onFail.handle(new StatsHttpResponse(response, code), code);
      }
      return StatsRequestResult.error(code);
    }
  }

  private HttpResponse<String> trySend(HttpClient client, HttpRequest request) throws IOException, StatsResponseException {
    return trySend(client, request, 0);
  }

  private HttpResponse<String> trySend(HttpClient client, HttpRequest request, int retryCounter) throws IOException, StatsResponseException {
    try {
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (CAN_RETRY_CODES.contains(response.statusCode()) && retryCounter < MAX_RETRIES) {
        Thread.sleep(RETRY_INTERVAL);
        response = trySend(client, request, ++retryCounter);
      }
      return response;
    }
    catch (InterruptedException e) {
      throw new StatsResponseException(e);
    }
  }

  private HttpClient newClient() {
    HttpClient.Builder builder = HttpClient.newBuilder();
    builder.followRedirects(HttpClient.Redirect.NORMAL);
    if (myProxyInfo != null && !myProxyInfo.isNoProxy()) {
      configureProxy(builder, myProxyInfo);
    }
    if (mySSLContext != null) {
      builder.sslContext(mySSLContext);
    }
    return builder.build();
  }

  private static void configureProxy(HttpClient.Builder builder, @NotNull StatsProxyInfo info) {
    Proxy proxy = info.getProxy();
    if (proxy.type() == Proxy.Type.HTTP || proxy.type() == Proxy.Type.SOCKS) {
      SocketAddress proxyAddress = proxy.address();
      if (proxyAddress instanceof InetSocketAddress) {
        InetSocketAddress address = (InetSocketAddress)proxyAddress;
        String hostName = address.getHostString();
        int port = address.getPort();
        builder.proxy(ProxySelector.of(new InetSocketAddress(hostName, port)));

        StatsProxyInfo.StatsProxyAuthProvider auth = info.getProxyAuth();
        if (auth != null) {
          String login = auth.getProxyLogin();
          if (login != null) {
            // This Implementation require -Djdk.http.auth.tunneling.disabledSchemes="" in vm options
            // In other cases PasswordAuthentication will be ignored and authentication will fail
            builder.authenticator(new Authenticator() {
              @Override
              protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(login, Objects.requireNonNullElse(auth.getProxyPassword(), "").toCharArray());
              }
            });
          }
        }
      }
    }
  }

  @NotNull
  private HttpRequest newRequest() {
    HttpRequest.Builder builder = HttpRequest.newBuilder().
      setHeader("User-Agent", myUserAgent).
      timeout(Duration.ofSeconds(10)).
      uri(URI.create(myUrl));

    if ("HEAD".equals(myMethod)) {
      builder.method(myMethod, HttpRequest.BodyPublishers.noBody());
    }
    else if ("POST".equals(myMethod)) {
      if (myContent == null || myContent.isBlank()) {
        throw new EmptyHttpRequestBody();
      }
      builder.setHeader("Chunked", Boolean.toString(false));
      builder.setHeader("Content-Type", String.format(Locale.ENGLISH, "%s; charset=%s", myContentType, myCharset));
      builder.setHeader("Content-Encoding", "gzip");
      builder.POST(HttpRequest.BodyPublishers.ofByteArray(getCompressedContent()));
    }
    else if ("GET".equals(myMethod)) {
      builder.GET();
    } else {
      throw new IllegalHttpRequestTypeException();
    }

    myExtraHeaders
      .forEach((k, v) -> builder.setHeader(k,v));

    return builder.build();
  }

  private byte[] getCompressedContent() {
    try (ByteArrayOutputStream gZippedBody = new ByteArrayOutputStream()) {
      GZIPOutputStream gZipper = new GZIPOutputStream(gZippedBody);
      gZipper.write(myContent.getBytes(myCharset));
      gZippedBody.flush();
      gZipper.close();
      return gZippedBody.toByteArray();
    } catch (IOException e) {
      throw new HttpRequestBodyGzipException(e);
    }
  }

  public static class EmptyHttpRequestBody extends InvalidHttpRequest {
    public EmptyHttpRequestBody() {
      super(51);
    }
  }

  public static class IllegalHttpRequestTypeException extends InvalidHttpRequest {
    public IllegalHttpRequestTypeException() {
      super(52);
    }
  }

  public static class HttpRequestBodyGzipException extends InvalidHttpRequest {
    public HttpRequestBodyGzipException(IOException ioException) {
      super(53, ioException);
    }
  }

  public static class InvalidHttpRequest extends RuntimeException {
    private final int myCode;

    public InvalidHttpRequest(int code) {
      this(code, null);
    }

    public InvalidHttpRequest(int code, Throwable throwable) {
      super(throwable);
      myCode = code;
    }

    public int getCode() {
      return myCode;
    }
  }
}

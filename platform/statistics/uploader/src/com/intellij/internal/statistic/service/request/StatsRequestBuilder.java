// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.request;

import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.entity.GzipCompressingEntity;
import org.apache.http.client.methods.*;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class StatsRequestBuilder {
  private final String myUserAgent;
  private final String myUrl;
  private final String myMethod;
  private StringEntity myBody;

  private StatsResponseHandler onSucceed;
  private StatsResponseHandler onFail;

  public StatsRequestBuilder(@NotNull String method, @NotNull String url, @NotNull String userAgent) {
    myMethod = method;
    myUrl = url;
    myUserAgent = userAgent;
  }

  @NotNull
  public StatsRequestBuilder withBody(@NotNull String body, @NotNull ContentType contentType) {
    myBody = new StringEntity(body, contentType);
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
        onSucceed.handle(response, HttpStatus.SC_OK);
      }
      return true;
    });
  }

  public <T> StatsRequestResult<T> send(StatsResponseProcessor<? extends T> processor) throws IOException, StatsResponseException {
    HttpRequestBase request = newRequest();
    try (CloseableHttpClient client = newClient(myUserAgent);
         CloseableHttpResponse response = client.execute(request)) {
      StatusLine statusLine = response.getStatusLine();
      int code = statusLine != null ? statusLine.getStatusCode() : -1;
      if (code == HttpStatus.SC_OK) {
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
  }

  private static CloseableHttpClient newClient(@NotNull String userAgent) {
    return HttpClientBuilder.create().setUserAgent(userAgent).build();
  }

  @NotNull
  private HttpRequestBase newRequest() {
    if ("HEAD".equals(myMethod)) {
      return new HttpHead(myUrl);
    }
    else if ("POST".equals(myMethod)) {
      HttpPost post = new HttpPost(myUrl);
      if (myBody != null) {
        post.setEntity(new GzipCompressingEntity(myBody));
      }
      return post;
    }
    else if ("GET".equals(myMethod)) {
      return new HttpGet(myUrl);
    }
    throw new IllegalArgumentException();
  }
}

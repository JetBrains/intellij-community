// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.request;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.utils.DateUtils;
import org.apache.http.util.EntityUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.stream.Stream;

public class StatsHttpResponse {
  @NonNls private static final String UTF8 = "UTF-8";

  private final HttpResponse myResponse;
  private final int myCode;

  public StatsHttpResponse(@Nullable HttpResponse response, int code) {
    myResponse = response;
    myCode = code;
  }

  public int getStatusCode() {
    return myCode;
  }

  @Nullable
  public Long lastModified() {
    Header[] headers = myResponse.getHeaders(HttpHeaders.LAST_MODIFIED);
    return Stream.of(headers).
      map(header -> header.getValue()).
      filter(Objects::nonNull).
      map(value -> DateUtils.parseDate(value).getTime()).
      max(Long::compareTo).orElse(null);
  }

  @Nullable
  public String readAsString() throws IOException {
    HttpEntity entity = myResponse.getEntity();
    return entity != null ? EntityUtils.toString(entity, UTF8) : null;
  }

  @Nullable
  public InputStream read() throws IOException {
    HttpEntity entity = myResponse.getEntity();
    return entity != null ? entity.getContent() : null;
  }
}

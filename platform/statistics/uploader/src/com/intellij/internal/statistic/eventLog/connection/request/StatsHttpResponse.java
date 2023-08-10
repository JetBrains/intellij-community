// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.connection.request;

import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;

public class StatsHttpResponse {
  private static final SimpleDateFormat RFC1123_FORMAT = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");
  private static final SimpleDateFormat RFC1036_FORMAT = new SimpleDateFormat("EEE, dd-MMM-yy HH:mm:ss zzz");
  private static final SimpleDateFormat ASCTIME_FORMAT = new SimpleDateFormat("EEE MMM d HH:mm:ss yyyy");
  private static final SimpleDateFormat[] DATE_FORMATS = new SimpleDateFormat[]{RFC1123_FORMAT, RFC1036_FORMAT, ASCTIME_FORMAT};

  private final HttpResponse<String> myHttpResponse;
  private final int myCode;
  public StatsHttpResponse(@Nullable HttpResponse<String> httpResponse, int code) {
    myHttpResponse = httpResponse;
    myCode = code;
  }

  public int getStatusCode() {
    return myCode;
  }

  @Nullable
  public Long lastModified() {
    return myHttpResponse == null ? null : myHttpResponse.headers().
      allValues("Last-Modified").stream().
      map(value -> parseDate(value)).filter(date -> date != null).map(date -> date.getTime()).
      max(Long::compareTo).orElse(null);
  }

  @Nullable
  public String readAsString() throws IOException {
    return  myHttpResponse != null && myHttpResponse.body() != null ? myHttpResponse.body() : null;
  }

  @Nullable
  public InputStream read() throws IOException {
    return myHttpResponse != null && myHttpResponse.body() != null
           ? new ByteArrayInputStream(myHttpResponse.body().getBytes(StandardCharsets.UTF_8)) : null;
  }

  private static @Nullable Date parseDate(String string) {
    for (SimpleDateFormat format : DATE_FORMATS) {
      Date date = format.parse(string, new ParsePosition(0));
      if (date != null) {
        return date;
      }
    }
    return null;
  }
}

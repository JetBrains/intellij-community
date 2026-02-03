// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.connection.request;

import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class StatsHttpResponse {
  private static final DateTimeFormatter RFC1123_FORMAT = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz");
  private static final DateTimeFormatter RFC1036_FORMAT = DateTimeFormatter.ofPattern("EEE, dd-MMM-yy HH:mm:ss zzz");
  private static final DateTimeFormatter ASCTIME_FORMAT = DateTimeFormatter.ofPattern("EEE MMM d HH:mm:ss yyyy");
  private static final DateTimeFormatter[] DATE_FORMATS = new DateTimeFormatter[]{RFC1123_FORMAT, RFC1036_FORMAT, ASCTIME_FORMAT};

  private final HttpResponse<String> myHttpResponse;
  private final int myCode;
  public StatsHttpResponse(@Nullable HttpResponse<String> httpResponse, int code) {
    myHttpResponse = httpResponse;
    myCode = code;
  }

  public int getStatusCode() {
    return myCode;
  }

  public @Nullable Long lastModified() {
    return myHttpResponse == null ? null : myHttpResponse.headers().
      allValues("Last-Modified").stream().
      map(value -> parseDate(value)).filter(date -> date != null).
      max(Long::compareTo).orElse(null);
  }

  public @Nullable String readAsString() throws IOException {
    return  myHttpResponse != null && myHttpResponse.body() != null ? myHttpResponse.body() : null;
  }

  public @Nullable InputStream read() throws IOException {
    return myHttpResponse != null && myHttpResponse.body() != null
           ? new ByteArrayInputStream(myHttpResponse.body().getBytes(StandardCharsets.UTF_8)) : null;
  }

  private static @Nullable Long parseDate(String string) {
    for (DateTimeFormatter format : DATE_FORMATS) {
      try {
        return ZonedDateTime.parse(string, format).toInstant().toEpochMilli();
      }
      catch (DateTimeParseException ignored) {
      }
    }
    return null;
  }
}

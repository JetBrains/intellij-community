// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic;

import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.jetbrains.annotations.NotNull;

public class EventLogHttpClientUtils {

  @NotNull
  public static HttpClient create() {
    return HttpClientBuilder.create().setUserAgent("IntelliJ").build();
  }
}

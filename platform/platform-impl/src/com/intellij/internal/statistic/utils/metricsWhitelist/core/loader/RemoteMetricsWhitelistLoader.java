// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.utils.metricsWhitelist.core.loader;

import com.intellij.internal.statistic.utils.metricsWhitelist.core.MetricsWhitelist;
import com.intellij.internal.statistic.utils.metricsWhitelist.core.MetricsWhitelistFactory;
import com.intellij.internal.statistic.utils.metricsWhitelist.core.MetricsWhitelistHeader;
import com.intellij.util.io.HttpRequests;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public abstract class RemoteMetricsWhitelistLoader<T extends MetricsWhitelist> implements MetricsWhitelistLoader<T> {
  private final MetricsWhitelistFactory<T> myFactory;

  public RemoteMetricsWhitelistLoader(@NotNull MetricsWhitelistFactory<T> factory) {
    myFactory = factory;
  }

  @NotNull
  @Override
  public MetricsWhitelistHeader loadHeader(@NotNull String whitelistId) throws Exception {
    String getWhitelistHeaderUrl = getWhitelistHeaderUrl(whitelistId);
    if (getWhitelistHeaderUrl == null) {
      throw new IOException("Failed to obtain url for metrics whitelist header");
    }
    String content;
    try {
      content = HttpRequests.request(getWhitelistHeaderUrl).productNameAsUserAgent().readString(null);
    }
    catch (IOException e) {
      throw new IOException("Failed to download metrics whitelist header", e);
    }
    MetricsWhitelistHeader header = myFactory.createHeader(content);
    if (header == null) {
      throw new IOException("Failed to parse metrics whitelist header");
    }
    return header;
  }

  @NotNull
  @Override
  public T loadWhitelist(@NotNull String id) throws IOException {
    String getDictionaryUrl = getWhitelistUrl(id);
    if (getDictionaryUrl == null) {
      throw new IOException("Failed to obtain url for metrics whitelist");
    }
    String content;
    try {
      content = HttpRequests.request(getDictionaryUrl).productNameAsUserAgent().readString(null);
    }
    catch (IOException e) {
      throw new IOException("Failed to download metrics whitelist", e);
    }
    T dictionary = myFactory.createWhitelist(content);
    if (dictionary == null) {
      throw new IOException("Failed to parse metrics whitelist");
    }
    return dictionary;
  }

  @Nullable
  protected abstract String getWhitelistHeaderUrl(@NotNull String whitelistId);

  @Nullable
  protected abstract String getWhitelistUrl(@NotNull String whitelistId);
}


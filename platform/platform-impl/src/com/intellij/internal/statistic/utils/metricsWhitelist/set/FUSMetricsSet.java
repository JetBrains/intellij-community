// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.utils.metricsWhitelist.set;

import com.google.gson.GsonBuilder;
import com.intellij.internal.statistic.utils.metricsWhitelist.core.DefaultMetricsWhitelistHeader;
import com.intellij.internal.statistic.utils.metricsWhitelist.core.MetricsWhitelist;
import com.intellij.internal.statistic.utils.metricsWhitelist.core.MetricsWhitelistFactory;
import com.intellij.internal.statistic.utils.metricsWhitelist.core.MetricsWhitelistHeader;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class FUSMetricsSet implements MetricsWhitelist {
  private final MetricsWhitelistHeader myHeader;
  private final Set<String> mySet;

  public FUSMetricsSet(boolean deprecated, @NotNull String version, @NotNull Set<String> set) {
    myHeader = new DefaultMetricsWhitelistHeader(deprecated, version);
    mySet = Collections.unmodifiableSet(set);
  }

  @NotNull
  @Override
  public MetricsWhitelistHeader getHeader() {
    return myHeader;
  }

  public boolean contains(@NotNull String metric) {
    return mySet.contains(metric);
  }

  public static class Factory implements MetricsWhitelistFactory<FUSMetricsSet> {
    @Nullable
    @Override
    public MetricsWhitelistHeader createHeader(@NotNull String rawHeader) {
      try {
        Header header = new GsonBuilder().create().fromJson(rawHeader, Header.class);
        return header != null && header.version != null ? new DefaultMetricsWhitelistHeader(header.deprecated, header.version) : null;
      }
      catch (Exception e) {
        return null;
      }
    }

    @Nullable
    @Override
    public FUSMetricsSet createWhitelist(@NotNull String rawMetricsSet) {
      try {
        MetricsSet metricsSet = new GsonBuilder().create().fromJson(rawMetricsSet, MetricsSet.class);
        if (metricsSet == null || metricsSet.header == null || metricsSet.header.version == null || metricsSet.entries == null) return null;
        return new FUSMetricsSet(metricsSet.header.deprecated, metricsSet.header.version, ContainerUtil.newHashSet(metricsSet.entries));
      }
      catch (Exception e) {
        return null;
      }
    }

    // Fields of these classes are used during json unmarshalling via Gson.
    @SuppressWarnings({"unused", "MismatchedQueryAndUpdateOfCollection"})
    private static class MetricsSet {
      private Header header;
      private List<String> entries;
    }

    @SuppressWarnings("unused")
    private static class Header {
      private boolean deprecated;
      private String version;
    }
  }
}

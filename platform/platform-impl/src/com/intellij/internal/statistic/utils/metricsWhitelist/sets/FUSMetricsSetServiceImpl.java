// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.utils.metricsWhitelist.sets;

import com.intellij.internal.statistic.utils.metricsWhitelist.core.cache.InMemoryMetricsWhitelistCache;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class FUSMetricsSetServiceImpl implements FUSMetricsSetService {
  private final InMemoryMetricsWhitelistCache<FUSMetricsSet> myCache;

  private FUSMetricsSetServiceImpl() {
    myCache = new InMemoryMetricsWhitelistCache<>(new FUSMetricsSetLoader(new FUSMetricsSet.Factory()));
  }

  @Override
  public boolean containsMetric(@NotNull String metricsSetId, @NotNull String metric) {
    FUSMetricsSet metricsSet = myCache.get(metricsSetId);
    return metricsSet != null && metricsSet.contains(metric);
  }

  @Override
  public void preloadSets(@NotNull Set<String> ids) {
    ids.forEach(myCache::get);
  }
}

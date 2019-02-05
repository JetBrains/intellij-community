// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.utils.metricsWhitelist.dictionaries;

import com.intellij.internal.statistic.utils.metricsWhitelist.core.cache.InMemoryMetricsWhitelistCache;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class FUSRegexDictionaryServiceImpl implements FUSRegexDictionaryService {
  private final InMemoryMetricsWhitelistCache<FUSRegexDictionary> myCache;

  // Used by ServiceManager.
  @SuppressWarnings("unused")
  private FUSRegexDictionaryServiceImpl() {
    myCache = new InMemoryMetricsWhitelistCache<>(new FUSRegexDictionaryLoader(new FUSRegexDictionary.Factory()));
  }

  @Nullable
  @Override
  public String lookupMetric(@NotNull String dictionaryId, @NotNull String rawMetric) {
    FUSRegexDictionary dictionary = myCache.get(dictionaryId);
    return dictionary != null ? dictionary.lookupMetric(rawMetric) : null;
  }

  @Override
  public void preloadDictionaries(@NotNull Set<String> ids) {
    for (String dictionaryId : ids) {
      myCache.get(dictionaryId);
    }
  }
}
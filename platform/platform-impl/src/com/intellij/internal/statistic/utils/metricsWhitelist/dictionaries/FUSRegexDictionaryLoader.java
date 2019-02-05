// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.utils.metricsWhitelist.dictionaries;

import com.intellij.internal.statistic.eventLog.EventLogExternalSettingsService;
import com.intellij.internal.statistic.utils.metricsWhitelist.core.loader.RemoteMetricsWhitelistLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FUSRegexDictionaryLoader extends RemoteMetricsWhitelistLoader<FUSRegexDictionary> {
  public FUSRegexDictionaryLoader(@NotNull FUSRegexDictionary.Factory factory) {
    super(factory);
  }

  @Nullable
  @Override
  protected String getWhitelistHeaderUrl(@NotNull String dictionaryId) {
    String serviceUrl = EventLogExternalSettingsService.getInstance().getDictionaryServiceUrl();
    return serviceUrl != null ? serviceUrl + "header/" + dictionaryId + ".json" : null;
  }

  @Nullable
  @Override
  protected String getWhitelistUrl(@NotNull String dictionaryId) {
    String serviceUrl = EventLogExternalSettingsService.getInstance().getDictionaryServiceUrl();
    return serviceUrl != null ? serviceUrl + dictionaryId + ".json" : null;
  }
}

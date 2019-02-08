// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.utils.metricsWhitelist.sets;

import com.intellij.internal.statistic.eventLog.EventLogExternalSettingsService;
import com.intellij.internal.statistic.utils.metricsWhitelist.core.loader.RemoteMetricsWhitelistLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FUSMetricsSetLoader extends RemoteMetricsWhitelistLoader<FUSMetricsSet> {
  public FUSMetricsSetLoader(@NotNull FUSMetricsSet.Factory factory) {
    super(factory);
  }

  @Nullable
  @Override
  protected String getWhitelistHeaderUrl(@NotNull String metricsSetId) {
    String serviceUrl = EventLogExternalSettingsService.getInstance().getMetricsSetServiceUrl();
    return serviceUrl != null ? serviceUrl + "header/" + metricsSetId + ".json" : null;
  }

  @Nullable
  @Override
  protected String getWhitelistUrl(@NotNull String metricsSetId) {
    String serviceUrl = EventLogExternalSettingsService.getInstance().getMetricsSetServiceUrl();
    return serviceUrl != null ? serviceUrl + metricsSetId + ".json" : null;
  }
}

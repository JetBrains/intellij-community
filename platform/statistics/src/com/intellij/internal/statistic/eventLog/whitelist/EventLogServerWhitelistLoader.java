// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.whitelist;

import com.intellij.internal.statistic.eventLog.EventLogUploadSettingsService;
import com.intellij.internal.statistic.service.fus.EventLogMetadataLoadException;
import com.intellij.internal.statistic.service.fus.StatisticsWhitelistLoader;
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant;
import org.jetbrains.annotations.NotNull;

public class EventLogServerWhitelistLoader implements EventLogMetadataLoader {
  @NotNull
  private final EventLogUploadSettingsService mySettingsService;

  public EventLogServerWhitelistLoader(@NotNull String recorderId) {
    mySettingsService = StatisticsUploadAssistant.createExternalSettings(recorderId, false);
  }

  @Override
  public long getLastModifiedOnServer() {
    String userAgent = mySettingsService.getApplicationInfo().getUserAgent();
    return StatisticsWhitelistLoader.lastModifiedWhitelist(mySettingsService.getWhiteListProductUrl(), userAgent);
  }

  @Override
  @NotNull
  public String loadMetadataFromServer() throws EventLogMetadataLoadException {
    String userAgent = mySettingsService.getApplicationInfo().getUserAgent();
    return StatisticsWhitelistLoader.loadWhiteListFromServer(mySettingsService.getWhiteListProductUrl(), userAgent);
  }
}

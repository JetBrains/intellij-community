// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.whitelist;

import com.intellij.internal.statistic.eventLog.EventLogUploadSettingsService;
import com.intellij.internal.statistic.service.fus.EventLogWhitelistLoadException;
import com.intellij.internal.statistic.service.fus.FUStatisticsWhiteListGroupsService;
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant;
import org.jetbrains.annotations.NotNull;

public class EventLogServerWhitelistLoader implements EventLogWhitelistLoader {
  @NotNull
  private final EventLogUploadSettingsService mySettingsService;

  public EventLogServerWhitelistLoader(@NotNull String recorderId) {
    mySettingsService = StatisticsUploadAssistant.createExternalSettings(recorderId, false);
  }

  @Override
  public long getLastModifiedOnServer() {
    return FUStatisticsWhiteListGroupsService.lastModifiedWhitelist(mySettingsService);
  }

  @Override
  @NotNull
  public String loadWhiteListFromServer() throws EventLogWhitelistLoadException {
    return FUStatisticsWhiteListGroupsService.loadWhiteListFromServer(mySettingsService);
  }
}

// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.whitelist;

import com.intellij.internal.statistic.eventLog.EventLogConnectionSettings;
import com.intellij.internal.statistic.eventLog.EventLogUploadSettingsService;
import com.intellij.internal.statistic.service.fus.EventLogMetadataLoadException;
import com.intellij.internal.statistic.service.fus.StatisticsWhitelistLoader;
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

public class EventLogServerWhitelistLoader implements EventLogMetadataLoader {
  @NotNull
  private final EventLogUploadSettingsService mySettingsService;

  public EventLogServerWhitelistLoader(@NotNull String recorderId) {
    mySettingsService = StatisticsUploadAssistant.createExternalSettings(recorderId, false, TimeUnit.HOURS.toMillis(1));
  }

  @Override
  public long getLastModifiedOnServer() {
    EventLogConnectionSettings settings = mySettingsService.getApplicationInfo().getConnectionSettings();
    return StatisticsWhitelistLoader.lastModifiedWhitelist(mySettingsService.getWhiteListProductUrl(), settings);
  }

  @Override
  @NotNull
  public String loadMetadataFromServer() throws EventLogMetadataLoadException {
    EventLogConnectionSettings settings = mySettingsService.getApplicationInfo().getConnectionSettings();
    return StatisticsWhitelistLoader.loadWhiteListFromServer(mySettingsService.getWhiteListProductUrl(), settings);
  }
}

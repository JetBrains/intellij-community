// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.validator.storage;

import com.intellij.internal.statistic.eventLog.connection.EventLogConnectionSettings;
import com.intellij.internal.statistic.eventLog.connection.EventLogUploadSettingsService;
import com.intellij.internal.statistic.eventLog.connection.metadata.EventLogMetadataLoadException;
import com.intellij.internal.statistic.eventLog.connection.metadata.EventLogMetadataUtils;
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public class EventLogServerMetadataLoader implements EventLogMetadataLoader {
  private final @NotNull EventLogUploadSettingsService mySettingsService;

  public EventLogServerMetadataLoader(@NotNull String recorderId) {
    mySettingsService = StatisticsUploadAssistant.createExternalSettings(
      recorderId,
      StatisticsUploadAssistant.isUseTestStatisticsConfig(),
      StatisticsUploadAssistant.isUseTestStatisticsSendEndpoint(),
      TimeUnit.HOURS.toMillis(1));
  }

  @Override
  public long getLastModifiedOnServer() {
    EventLogConnectionSettings settings = mySettingsService.getApplicationInfo().getConnectionSettings();
    return EventLogMetadataUtils.lastModifiedMetadata(mySettingsService.getMetadataProductUrl(), settings);
  }

  @Override
  public @NotNull String loadMetadataFromServer() throws EventLogMetadataLoadException {
    EventLogConnectionSettings settings = mySettingsService.getApplicationInfo().getConnectionSettings();
    return EventLogMetadataUtils.loadMetadataFromServer(mySettingsService.getMetadataProductUrl(), settings);
  }

  @Override
  public @NotNull Map<String, String> getOptionValues() {
    return mySettingsService.getOptions();
  }
}

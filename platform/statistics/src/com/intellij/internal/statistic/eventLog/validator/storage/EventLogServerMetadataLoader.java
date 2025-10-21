// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.validator.storage;

import com.intellij.internal.statistic.eventLog.connection.EventLogSettingsClient;
import com.intellij.internal.statistic.eventLog.connection.metadata.EventLogMetadataLoadException;
import com.intellij.internal.statistic.eventLog.connection.metadata.EventLogMetadataUtils;
import com.intellij.internal.statistic.eventLog.connection.metadata.StatsConnectionSettings;
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public class EventLogServerMetadataLoader implements EventLogMetadataLoader {
  private final @NotNull EventLogSettingsClient mySettingsClient;

  public EventLogServerMetadataLoader(@NotNull String recorderId) {
    mySettingsClient = StatisticsUploadAssistant.createExternalSettings(
      recorderId,
      StatisticsUploadAssistant.isUseTestStatisticsConfig(),
      StatisticsUploadAssistant.isUseTestStatisticsSendEndpoint(),
      TimeUnit.HOURS.toMillis(1));
  }

  @Override
  public long getLastModifiedOnServer() {
    StatsConnectionSettings settings = mySettingsClient.getApplicationInfo().getConnectionSettings();
    return EventLogMetadataUtils.lastModifiedMetadata(mySettingsClient.provideMetadataProductUrl(), settings);
  }

  @Override
  public @NotNull String loadMetadataFromServer() throws EventLogMetadataLoadException {
    StatsConnectionSettings settings = mySettingsClient.getApplicationInfo().getConnectionSettings();
    return EventLogMetadataUtils.loadMetadataFromServer(mySettingsClient.provideMetadataProductUrl(), settings);
  }

  @Override
  public @NotNull Map<String, Long> getDictionariesLastModifiedOnServer(String recorderId) throws EventLogMetadataLoadException {
    StatsConnectionSettings settings = mySettingsClient.getApplicationInfo().getConnectionSettings();
    return EventLogMetadataUtils.dictionariesLastModified(mySettingsClient.provideDictionaryServiceUrl(), recorderId, settings);
  }

  @Override
  public @NotNull String loadDictionaryFromServer(String recorderId, String dictionaryName) throws EventLogMetadataLoadException {
    StatsConnectionSettings settings = mySettingsClient.getApplicationInfo().getConnectionSettings();
    return EventLogMetadataUtils.loadDictionaryFromServer(mySettingsClient.provideDictionaryServiceUrl(), recorderId, dictionaryName, settings);
  }

  @Override
  public @NotNull Map<String, String> getOptionValues() {
    return mySettingsClient.provideOptions();
  }
}

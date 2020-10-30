// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator.storage;

import com.intellij.internal.statistic.eventLog.connection.EventLogConnectionSettings;
import com.intellij.internal.statistic.eventLog.connection.EventLogUploadSettingsService;
import com.intellij.internal.statistic.eventLog.connection.metadata.EventLogMetadataLoadException;
import com.intellij.internal.statistic.eventLog.connection.metadata.EventLogMetadataUtils;
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.TimeUnit;

public class EventLogServerMetadataLoader implements EventLogMetadataLoader {
  @NotNull
  private final EventLogUploadSettingsService mySettingsService;

  public EventLogServerMetadataLoader(@NotNull String recorderId) {
    mySettingsService = StatisticsUploadAssistant.createExternalSettings(recorderId, false, TimeUnit.HOURS.toMillis(1));
  }

  @Override
  public long getLastModifiedOnServer() {
    EventLogConnectionSettings settings = mySettingsService.getApplicationInfo().getConnectionSettings();
    return EventLogMetadataUtils.lastModifiedMetadata(mySettingsService.getMetadataProductUrl(), settings);
  }

  @Override
  @NotNull
  public String loadMetadataFromServer() throws EventLogMetadataLoadException {
    EventLogConnectionSettings settings = mySettingsService.getApplicationInfo().getConnectionSettings();
    return EventLogMetadataUtils.loadMetadataFromServer(mySettingsService.getMetadataProductUrl(), settings);
  }

  @Override
  public @Nullable String getOptionValue(@NotNull String name) {
    return mySettingsService.getOptionValue(name);
  }
}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.validator.storage.persistence;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * EventLogMetadataPersistence is only used for {@link com.intellij.internal.statistic.eventLog.validator.storage.ValidationTestRulesPersistedStorage}
 */
public class EventLogMetadataPersistence extends BaseEventLogMetadataPersistence {
  private static final String DEPRECATED_EVENTS_SCHEME_FILE = "white-list.json";
  public static final String EVENTS_SCHEME_FILE = "events-scheme.json";

  private static final Logger LOG = Logger.getInstance(EventLogMetadataPersistence.class);
  private final @NotNull String myRecorderId;

  public EventLogMetadataPersistence(@NotNull String recorderId) {
    myRecorderId = recorderId;
  }

  @Override
  public @Nullable String getCachedEventsScheme() {
    try {
      Path file = getEventsSchemeFile();
      if (Files.exists(file) && Files.isRegularFile(file)) {
        return Files.readString(file);
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }
    return null;
  }

  private @NotNull Path getEventsSchemeFile() throws IOException {
    EventsSchemePathSettings settings = EventLogMetadataSettingsPersistence.getInstance().getPathSettings(myRecorderId);
    if (settings != null && settings.isUseCustomPath()) {
      return Path.of(settings.getCustomPath());
    }
    else {
      return getDefaultFile();
    }
  }

  public @NotNull Path getDefaultFile() throws IOException {
    return getDefaultMetadataFile(myRecorderId, EVENTS_SCHEME_FILE, DEPRECATED_EVENTS_SCHEME_FILE);
  }
}
// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator.storage.persistence;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class EventLogMetadataPersistence extends BaseEventLogMetadataPersistence {
  private static final String DEPRECATED_EVENTS_SCHEME_FILE = "white-list.json";
  public static final String EVENTS_SCHEME_FILE = "events-scheme.json";

  private static final Logger LOG = Logger.getInstance(EventLogMetadataPersistence.class);
  @NotNull
  private final String myRecorderId;

  public EventLogMetadataPersistence(@NotNull String recorderId) {
    myRecorderId = recorderId;
  }

  @Override
  @Nullable
  public String getCachedEventsScheme() {
    try {
      Path file = getEventsSchemeFile();
      if (Files.exists(file)) {
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
      Path file = getDefaultFile();
      if (!Files.exists(file)) {
        initBuiltinMetadata(file);
      }
      return file;
    }
  }

  public void cacheEventsScheme(@NotNull String eventsSchemeJson, long lastModified) {
    try {
      Path file = getDefaultFile();
      Files.createDirectories(file.getParent());
      Files.writeString(file, eventsSchemeJson);
      EventLogMetadataSettingsPersistence.getInstance().setLastModified(myRecorderId, lastModified);
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  private void initBuiltinMetadata(@NotNull Path file) throws IOException {
    try (InputStream stream = getClass().getClassLoader().getResourceAsStream(builtinEventSchemePath())) {
      if (stream == null) {
        return;
      }

      Files.createDirectories(file.getParent());
      Files.copy(stream, file, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private String builtinEventSchemePath() {
    return "resources/" + FUS_METADATA_DIR + "/" + myRecorderId + "/" + EVENTS_SCHEME_FILE;
  }

  public long getLastModified() {
    return EventLogMetadataSettingsPersistence.getInstance().getLastModified(myRecorderId);
  }

  public @NotNull Path getDefaultFile() throws IOException {
    return getDefaultMetadataFile(myRecorderId, EVENTS_SCHEME_FILE, DEPRECATED_EVENTS_SCHEME_FILE);
  }
}

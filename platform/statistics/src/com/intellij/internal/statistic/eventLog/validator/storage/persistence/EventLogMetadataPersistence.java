// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.validator.storage.persistence;

import com.intellij.internal.statistic.eventLog.connection.metadata.EventLogMetadataParseException;
import com.intellij.internal.statistic.eventLog.connection.metadata.EventLogMetadataUtils;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.BuildNumber;
import com.jetbrains.fus.reporting.model.metadata.EventGroupRemoteDescriptors;
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
      Path file = getDefaultFile();
      if (shouldBeRewrittenByBuiltinGroups(file)) {
        Files.createDirectories(file.getParent());
        initBuiltinMetadata(file);
      }
      return file;
    }
  }

  /**
   *  Use bundled metadata instead of the default local one if its version is newer,
   *  the persistence build number is null or less than the current build number.
   */
  private boolean shouldBeRewrittenByBuiltinGroups(Path defaultFile) {
    try {
      if (!Files.exists(defaultFile)) {
        return true;
      }

      BuildNumber currentBuild = ApplicationInfo.getInstance().getBuild();
      String previousBuildNumberStr = EventLogMetadataSettingsPersistence.getInstance().getBuildNumber(myRecorderId);
      BuildNumber previousBuildNumber = BuildNumber.fromString(previousBuildNumberStr);

      if (previousBuildNumber != null && previousBuildNumber.compareTo(currentBuild) >= 0) {
        return false;
      }

      EventLogMetadataSettingsPersistence.getInstance().setBuildNumber(myRecorderId, currentBuild.asString());
      Path builtinFile = Files.createTempFile("builtin-events-scheme", ".json");
      initBuiltinMetadata(builtinFile);

      String builtinEventsScheme = readEventScheme(builtinFile);
      EventGroupRemoteDescriptors builtinEventGroupRemoteDescriptors = getEventGroupRemoteDescriptors(builtinEventsScheme);
      if (builtinEventGroupRemoteDescriptors == null || builtinEventGroupRemoteDescriptors.version == null) {
        return false;
      }

      String defaultEventScheme = readEventScheme(defaultFile);
      EventGroupRemoteDescriptors defaultEventGroupRemoteDescriptors = getEventGroupRemoteDescriptors(defaultEventScheme);
      if (defaultEventGroupRemoteDescriptors == null || defaultEventGroupRemoteDescriptors.version == null) {
        return true;
      }

      return builtinEventGroupRemoteDescriptors.version.compareTo(defaultEventGroupRemoteDescriptors.version) > 0;
    }
    catch (IOException e) {
      LOG.error(e);
    }
    return false;
  }

  private static @Nullable String readEventScheme(@NotNull Path file) {
    try {
      if (Files.exists(file) && Files.isRegularFile(file)) {
        return Files.readString(file);
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }
    return null;
  }

  private static @Nullable EventGroupRemoteDescriptors getEventGroupRemoteDescriptors(@Nullable String data) {
    try {
      return EventLogMetadataUtils.parseGroupRemoteDescriptors(data);
    }
    catch (EventLogMetadataParseException e) {
      LOG.info(e);
    }
    return null;
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
// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.validator.storage.persistence;

import com.intellij.internal.statistic.eventLog.connection.metadata.EventLogMetadataParseException;
import com.intellij.internal.statistic.eventLog.connection.metadata.EventLogMetadataUtils;
import com.intellij.internal.statistic.eventLog.dictionary.Dictionary;
import com.intellij.internal.statistic.eventLog.dictionary.FixedSizedBlockDictionary;
import com.intellij.internal.statistic.eventLog.validator.SimpleDictionaryStorage;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.internal.statistic.eventLog.validator.DictionaryStorage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.BuildNumber;
import com.jetbrains.fus.reporting.jvm.JvmFileStorage;
import com.jetbrains.fus.reporting.model.dictionaries.RemoteDictionaryList;
import com.jetbrains.fus.reporting.model.metadata.EventGroupRemoteDescriptors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

public class EventLogMetadataPersistence extends BaseEventLogMetadataPersistence {
  private static final String DEPRECATED_EVENTS_SCHEME_FILE = "white-list.json";
  public static final String EVENTS_SCHEME_FILE = "events-scheme.json";
  private static final String DICTIONARIES_DIR = "dictionaries";
  private static final String DICTIONARIES_LIST_FILE = "dictionaries.json";

  private static final Logger LOG = Logger.getInstance(EventLogMetadataPersistence.class);
  private final @NotNull String myRecorderId;

  private @Nullable DictionaryStorage dictionaryStorage;

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

      EventGroupRemoteDescriptors builtinEventGroupRemoteDescriptors = getBuildinEventGroupRemoteDescriptors(currentBuild);
      if (builtinEventGroupRemoteDescriptors == null || builtinEventGroupRemoteDescriptors.version == null) {
        return false;
      }

      String defaultEventScheme = readJsonFileContent(defaultFile);
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

  private @Nullable EventGroupRemoteDescriptors getBuildinEventGroupRemoteDescriptors(@NotNull BuildNumber currentBuild)
    throws IOException {
    EventLogMetadataSettingsPersistence.getInstance().setBuildNumber(myRecorderId, currentBuild.asString());
    Path builtinFile = Files.createTempFile("builtin-events-scheme", ".json");
    if (initBuiltinMetadata(builtinFile)) {
      String builtinEventsScheme = readJsonFileContent(builtinFile);
      return getEventGroupRemoteDescriptors(builtinEventsScheme);
    }
    return null;
  }

  private static @Nullable String readJsonFileContent(@NotNull Path file) {
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

  private static @Nullable RemoteDictionaryList getRemoteDictionaryList(@Nullable String data) {
    try {
      return EventLogMetadataUtils.parseRemoteDictionaryList(data);
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

  private boolean initBuiltinMetadata(@NotNull Path file) throws IOException {
    try (InputStream stream = getClass().getClassLoader().getResourceAsStream(builtinEventSchemePath())) {
      if (stream == null) {
        return false;
      }
      Files.copy(stream, file, StandardCopyOption.REPLACE_EXISTING);
    }
    return true;
  }

  private RemoteDictionaryList readBuiltinDictionaryList() throws IOException {
    try (InputStream stream = getClass().getClassLoader().getResourceAsStream(builtinDictionariesPath() + DICTIONARIES_LIST_FILE)) {
      if (stream == null) {
        return null;
      }
      String content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      return getRemoteDictionaryList(content);
    }
  }

  private void initAllBuiltinDictionaries(@NotNull Path file) throws IOException {
    RemoteDictionaryList dictionaryList = readBuiltinDictionaryList();
    if (dictionaryList == null) {
      LOG.trace("Cannot load builtin dictionaries list");
      return;
    }

    for (String dictionary : dictionaryList.dictionaries) {
      initSingleBuiltinDictionary(file, dictionary);
    }
  }

  private void initSingleBuiltinDictionary(@NotNull Path dictionariesDir, @NotNull String dictionaryName) throws IOException {
    try (InputStream stream = getClass().getClassLoader().getResourceAsStream(builtinDictionariesPath() + dictionaryName)) {
      if (stream == null) {
        return;
      }
      Files.copy(stream, dictionariesDir.resolve(dictionaryName), StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private String builtinEventSchemePath() {
    return "resources/" + FUS_METADATA_DIR + "/" + myRecorderId + "/" + EVENTS_SCHEME_FILE;
  }

  private String builtinDictionariesPath() {
    return "resources/" + FUS_METADATA_DIR + "/" + myRecorderId + "/" + DICTIONARIES_DIR + "/";
  }

  public long getLastModified() {
    return EventLogMetadataSettingsPersistence.getInstance().getLastModified(myRecorderId);
  }

  public Map<String, Long> getDictionariesLastModified() {
    return EventLogMetadataSettingsPersistence.getInstance().getDictionariesLastModified(myRecorderId);
  }

  public void setDictionaryLastModified(String dictionaryName, long lastModified) {
    EventLogMetadataSettingsPersistence.getInstance().setDictionaryLastModified(myRecorderId, dictionaryName, lastModified);
  }

  public @NotNull Path getDefaultFile() throws IOException {
    return getDefaultMetadataFile(myRecorderId, EVENTS_SCHEME_FILE, DEPRECATED_EVENTS_SCHEME_FILE);
  }

  public @NotNull Path getDefaultDictionariesDir() throws IOException {
    return getDefaultFile().getParent().resolve(DICTIONARIES_DIR);
  }

  private Path getDictionariesPath(boolean shouldInit) throws IOException {
    EventsSchemePathSettings settings = EventLogMetadataSettingsPersistence.getInstance().getPathSettings(myRecorderId);
    if (settings != null && settings.isUseCustomPath()) {
      return Path.of(settings.getCustomPath()).getParent().resolve(DICTIONARIES_DIR);
    }
    else {
      var path = getDefaultDictionariesDir();
      if (shouldInit) {
        initDictionaries(path);
      } else if (!Files.exists(path)) {
        // create directory in case it was deleted manually for some reason
        Files.createDirectories(path);
      }
      return path;
    }
  }

  public boolean dictionaryAvailable(String dictionaryName) {
    try {
      var path = this.getDictionariesPath(false);
      var dictionaryFile = path.resolve(dictionaryName);
      return Files.exists(dictionaryFile);
    } catch (Exception e) {
      return false;
    }
  }

  // has to be synchronized in case dictionary update and first validation run at the same time
  @Override
  public synchronized DictionaryStorage getDictionaryStorage() throws IOException {
    if (dictionaryStorage != null) {
      return dictionaryStorage;
    }
    Path dictionariesDir = getDictionariesPath(true);

    dictionaryStorage = new SimpleDictionaryStorage(new JvmFileStorage(dictionariesDir.getParent()), Dictionary.AccessMode.RANDOM_FILE_ACCESS);
    return dictionaryStorage;
  }

  private void initDictionaries(@NotNull Path dictionariesDir) throws IOException {
    if (Files.notExists(dictionariesDir)) {
      Files.createDirectories(dictionariesDir);
      initAllBuiltinDictionaries(dictionariesDir);
      return;
    }

    overwriteOutdatedLocalDictionaries(dictionariesDir);
  }

  private void overwriteOutdatedLocalDictionaries(@NotNull Path dictionariesDir) throws IOException {
    Path tempDictionaryDir = Files.createTempDirectory(DICTIONARIES_DIR);
    initAllBuiltinDictionaries(tempDictionaryDir);

    DictionaryStorage builtinDictionaryStorage = new SimpleDictionaryStorage(
      new JvmFileStorage(tempDictionaryDir.getParent()), Dictionary.AccessMode.RANDOM_FILE_ACCESS
    );
    RemoteDictionaryList builtinRemoteDictionaryList = readBuiltinDictionaryList();

    // no builtin dictionaries, so we can not initialize them
    if (builtinRemoteDictionaryList == null) {
      return;
    }

    DictionaryStorage localDictionaryStorage = new SimpleDictionaryStorage(
      new JvmFileStorage(dictionariesDir.getParent()), Dictionary.AccessMode.RANDOM_FILE_ACCESS
    );

    for (String dictionary : builtinRemoteDictionaryList.dictionaries) {
      String builtinVersion = getDictionaryVersion(builtinDictionaryStorage, dictionary);
      if (builtinVersion == null) {
        continue;
      }
      String localVersion = getDictionaryVersion(localDictionaryStorage, dictionary);
      if (localVersion == null || builtinVersion.compareTo(localVersion) > 0) {
        initSingleBuiltinDictionary(dictionariesDir, dictionary);
      }
    }
  }

  private static String getDictionaryVersion(DictionaryStorage storage, String dictionaryName) {
    try {
      FixedSizedBlockDictionary dictionary = (FixedSizedBlockDictionary)storage.getDictionaryByName(dictionaryName);
      return dictionary.getVersion();
    } catch (Exception e) {
      LOG.error(e);
      return null;
    }
  }
}
// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator.persistence;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class EventLogWhitelistPersistence extends BaseEventLogWhitelistPersistence {
  public static final String WHITE_LIST_DATA_FILE = "white-list.json";
  private static final Logger LOG =
    Logger.getInstance(EventLogWhitelistPersistence.class);
  @NotNull
  private final String myRecorderId;

  public EventLogWhitelistPersistence(@NotNull String recorderId) {
    myRecorderId = recorderId;
  }

  @Override
  @Nullable
  public String getCachedWhitelist() {
    try {
      File file = getWhitelistFile();
      if (file.exists()) return FileUtil.loadFile(file);
    }
    catch (IOException e) {
      LOG.error(e);
    }
    return null;
  }

  @NotNull
  private File getWhitelistFile() throws IOException {
    WhitelistPathSettings settings = EventLogWhitelistSettingsPersistence.getInstance().getPathSettings(myRecorderId);
    if (settings != null && settings.isUseCustomPath()) {
      return new File(settings.getCustomPath());
    }
    else {
      File file = getDefaultFile();
      if (!file.exists()) initBuiltinWhiteList(file);
      return file;
    }
  }

  public void cacheWhiteList(@NotNull String gsonWhiteListContent, long lastModified) {
    try {
      final File file = getDefaultFile();
      FileUtil.writeToFile(file, gsonWhiteListContent);
      EventLogWhitelistSettingsPersistence.getInstance().setLastModified(myRecorderId, lastModified);
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  private void initBuiltinWhiteList(File file) throws IOException {
    try (InputStream stream = getClass().getClassLoader().getResourceAsStream(builtinWhiteListPath())) {
      if (stream == null) return;
      if (!file.getParentFile().exists() && !file.getParentFile().mkdirs()) {
        throw new IOException("Unable to create " + file.getParentFile().getAbsolutePath());
      }
      Files.copy(stream, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private String builtinWhiteListPath() {
    return "resources/" + FUS_WHITELIST_PATH + "/" + myRecorderId + "/" + WHITE_LIST_DATA_FILE;
  }

  public long getLastModified() {
    return EventLogWhitelistSettingsPersistence.getInstance().getLastModified(myRecorderId);
  }

  @NotNull
  private File getDefaultFile() throws IOException {
    return getDefaultWhitelistFile(myRecorderId, WHITE_LIST_DATA_FILE);
  }
}

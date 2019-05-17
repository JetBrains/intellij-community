// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator.persistence;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;


public class EventLogWhitelistPersistence {
  private static final Logger
    LOG = Logger.getInstance("com.intellij.internal.statistic.eventLog.validator.persistence.EventLogWhitelistPersistence");

  private static final String WHITE_LIST_DATA_FILE = "white-list.json";
  public static final String FUS_WHITELIST_PATH = "event-log-whitelist";

  @NotNull
  private final String myRecorderId;

  public EventLogWhitelistPersistence(@NotNull String recorderId) {
    myRecorderId = recorderId;
  }

  @NotNull
  private File getWhiteListCacheDirectory() {
    return Paths.get(PathManager.getConfigPath()).resolve(FUS_WHITELIST_PATH + "/" + StringUtil.toLowerCase(myRecorderId) + "/").toFile();
  }

  @NotNull
  private File getFileInWhiteListCacheDirectory(@NotNull String fileName) {
    return new File(getWhiteListCacheDirectory(), "/" + fileName);
  }

  @NotNull
  File getWhiteListFile() {
    return getFileInWhiteListCacheDirectory(WHITE_LIST_DATA_FILE);
  }

  public void cacheWhiteList(@NotNull String gsonWhiteListContent) {
    File file = getWhiteListFile();
    try {
      FileUtil.writeToFile(file, gsonWhiteListContent);
    } catch (IOException e) {
      LOG.error(e);
    }
  }

  @Nullable
  public String getCachedWhiteList() {
    File file = getWhiteListFile();
    try {
      if (!file.exists()) {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("resources/" + WHITE_LIST_DATA_FILE)) {
          if (stream == null) return null;
          Files.copy(stream, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
      }
      return FileUtil.loadFile(file);
    }
    catch (IOException e) {
      LOG.error(e);
    }
    return null;
  }
}

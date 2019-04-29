// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator.persistence;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;


public class FUSWhiteListPersistence {
  private static final Logger
    LOG = Logger.getInstance("com.intellij.internal.statistic.eventLog.validator.persistence.FUSWhiteListPersistence");

  private static final String WHITE_LIST_DATA_FILE = "fus-white-list.json";
  public static final String FUS_WHITELIST_PATH = "fus-whitelist";

  @Nullable
  private static File getWhiteListCacheDirectory() {
    return Paths.get(PathManager.getConfigPath()).resolve(FUS_WHITELIST_PATH + "/").toFile();
  }

  @NotNull
  private static File getFileInWhiteListCacheDirectory(@NotNull String fileName) {
    return new File(getWhiteListCacheDirectory(), "/" + fileName);
  }

  @NotNull
  private static File getWhiteListFile() {
    return getFileInWhiteListCacheDirectory(WHITE_LIST_DATA_FILE);
  }

  public static void cacheWhiteList(@NotNull String gsonWhiteListContent) {
    File file = getWhiteListFile();
    try {
      FileUtil.writeToFile(file, gsonWhiteListContent);
    } catch (IOException e) {
      LOG.error(e);
    }
  }

  @Nullable
  public static String getCachedWhiteList() {
    File file = getWhiteListFile();
    if (file.exists()) {
      try {
        return FileUtil.loadFile(file);
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
    return null;
  }
}

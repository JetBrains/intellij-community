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

abstract public class BaseEventLogWhitelistPersistence {
  private static final Logger
    LOG = Logger.getInstance("com.intellij.internal.statistic.eventLog.validator.persistence.BaseEventLogWhiteListPersistence");
  public static final String FUS_WHITELIST_PATH = "event-log-whitelist";

  @NotNull
  protected final String myRecorderId;
  private final String myWhitelistFile;

  protected BaseEventLogWhitelistPersistence(@NotNull String id, String whitelistFile) {
    myRecorderId = id;
    myWhitelistFile = whitelistFile;
  }

  @NotNull
  File getWhiteListFile() {
    return getFileInWhiteListCacheDirectory(myWhitelistFile);
  }

  @NotNull
  private File getWhiteListCacheDirectory() {
    return Paths.get(PathManager.getConfigPath()).resolve(FUS_WHITELIST_PATH + "/" + StringUtil.toLowerCase(myRecorderId) + "/").toFile();
  }

  @NotNull
  private File getFileInWhiteListCacheDirectory(@NotNull String fileName) {
    return new File(getWhiteListCacheDirectory(), "/" + fileName);
  }

  @Nullable
  public String getCachedWhiteList() {
    File file = getWhiteListFile();
    try {
      if (!file.exists()) initBuiltinWhiteList(file);
      if (file.exists()) return FileUtil.loadFile(file);
    }
    catch (IOException e) {
      LOG.error(e);
    }
    return null;
  }

  private void initBuiltinWhiteList(File file) throws IOException {
    try (InputStream stream = getClass().getClassLoader().getResourceAsStream(builtinWhiteListPath())) {
      if (stream == null) return;
      if (!file.getParentFile().mkdirs()) {
        throw new IOException("Unable to create " + file.getParentFile().getAbsolutePath());
      }
      Files.copy(stream, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private String builtinWhiteListPath() {
    return "resources/" + FUS_WHITELIST_PATH + "/" + myRecorderId + "/" + myWhitelistFile;
  }

}

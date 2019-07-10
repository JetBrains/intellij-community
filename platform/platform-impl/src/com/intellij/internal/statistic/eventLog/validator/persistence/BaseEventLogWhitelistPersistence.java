// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator.persistence;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Paths;

abstract public class BaseEventLogWhitelistPersistence {
  public static final String FUS_WHITELIST_PATH = "event-log-whitelist";

  @NotNull
  protected final String myRecorderId;
  @NotNull
  protected final String myWhitelistFileName;

  protected BaseEventLogWhitelistPersistence(@NotNull String id, @NotNull String whitelistFileName) {
    myRecorderId = id;
    myWhitelistFileName = whitelistFileName;
  }

  @NotNull
  File getWhiteListFile() {
    return getFileInWhiteListCacheDirectory(myWhitelistFileName);
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
  public abstract String getCachedWhiteList();
}

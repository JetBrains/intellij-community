// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator.persistence;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

abstract public class BaseEventLogWhitelistPersistence {
  public static final String FUS_WHITELIST_PATH = "event-log-whitelist";

  @Nullable
  public abstract String getCachedWhitelist();

  @NotNull
  public static File getDefaultWhitelistFile(@NotNull String recorderId, @NotNull String whitelistFileName) throws IOException {
    final Path configPath = Paths.get(PathManager.getConfigPath());
    final Path whitelistDir = configPath
      .resolve(FUS_WHITELIST_PATH)
      .resolve(StringUtil.toLowerCase(recorderId));
    return whitelistDir.
      resolve(whitelistFileName).
      toFile().getCanonicalFile();
  }
}

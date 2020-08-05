// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator.persistence;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

abstract public class BaseEventLogWhitelistPersistence {
  private static final Logger LOG = Logger.getInstance(BaseEventLogWhitelistPersistence.class);

  public static final String DEPRECATED_FUS_METADATA_DIR = "event-log-whitelist";
  public static final String FUS_METADATA_DIR = "event-log-metadata";

  @Nullable
  public abstract String getCachedMetadata();

  public static File getDefaultMetadataFile(@NotNull String recorderId,
                                            @NotNull String fileName,
                                            @Nullable String deprecatedFileName) throws IOException {
    File file = getMetadataByDir(FUS_METADATA_DIR, recorderId, fileName);
    if (!file.exists() && StringUtil.isNotEmpty(deprecatedFileName)) {
      File deprecated = getMetadataByDir(DEPRECATED_FUS_METADATA_DIR, recorderId, deprecatedFileName);
      if (deprecated.exists()) {
        if (LOG.isTraceEnabled()) {
          LOG.trace("Moving deprecated metadata file to new directory: " + fileName);
        }

        if (!FileUtil.createParentDirs(file)) {
          LOG.info("Cannot create directories for event log metadata");
          return file;
        }

        FileUtil.copy(deprecated, file);
        if (!FileUtil.delete(deprecated)) {
          LOG.info("Failed deleting deprecated metadata file");
        }
      }
    }
    return file;
  }

  @NotNull
  public static Path getDeprecatedMetadataDir() {
    return getMetadataConfigRoot(DEPRECATED_FUS_METADATA_DIR);
  }

  @NotNull
  private static File getMetadataByDir(@NotNull String dir, @NotNull String recorderId, @NotNull String fileName) throws IOException {
    Path metadataDir = getMetadataConfigRoot(dir);
    return metadataDir.
      resolve(StringUtil.toLowerCase(recorderId)).
      resolve(fileName).
      toFile().getCanonicalFile();
  }

  @NotNull
  private static Path getMetadataConfigRoot(@NotNull String dir) {
    return PathManager.getConfigDir().resolve(dir);
  }
}

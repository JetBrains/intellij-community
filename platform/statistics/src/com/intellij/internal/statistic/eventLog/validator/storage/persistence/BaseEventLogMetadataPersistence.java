// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator.storage.persistence;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

abstract public class BaseEventLogMetadataPersistence {
  private static final Logger LOG = Logger.getInstance(BaseEventLogMetadataPersistence.class);

  public static final String DEPRECATED_FUS_METADATA_DIR = "event-log-whitelist";
  public static final String FUS_METADATA_DIR = "event-log-metadata";

  @Nullable
  public abstract String getCachedEventsScheme();

  public static Path getDefaultMetadataFile(@NotNull String recorderId,
                                            @NotNull String fileName,
                                            @Nullable String deprecatedFileName) throws IOException {
    Path file = getMetadataByDir(FUS_METADATA_DIR, recorderId, fileName);
    if (!Files.exists(file) && Strings.isNotEmpty(deprecatedFileName)) {
      Path deprecated = getMetadataByDir(DEPRECATED_FUS_METADATA_DIR, recorderId, deprecatedFileName);
      if (Files.exists(deprecated)) {
        if (LOG.isTraceEnabled()) {
          LOG.trace("Moving deprecated metadata file to new directory: " + fileName);
        }

        try {
          Files.createDirectories(file.getParent());
        }
        catch (IOException ignored) {
          LOG.info("Cannot create directories for event log metadata");
          return file;
        }

        Files.copy(deprecated, file);
        Files.deleteIfExists(deprecated);
      }
    }
    return file;
  }

  @NotNull
  public static Path getDeprecatedMetadataDir() {
    return getMetadataConfigRoot(DEPRECATED_FUS_METADATA_DIR);
  }

  private static @NotNull Path getMetadataByDir(@NotNull String dir, @NotNull String recorderId, @NotNull String fileName) throws IOException {
    Path metadataDir = getMetadataConfigRoot(dir);
    return metadataDir
      .resolve(StringUtil.toLowerCase(recorderId))
      .resolve(fileName)
      .normalize().toAbsolutePath();
  }

  @NotNull
  private static Path getMetadataConfigRoot(@NotNull String dir) {
    return PathManager.getConfigDir().resolve(dir);
  }
}

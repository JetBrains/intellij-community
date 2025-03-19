// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

public abstract class BaseEventLogMetadataPersistence {
  private static final Logger LOG = Logger.getInstance(BaseEventLogMetadataPersistence.class);

  public static final String DEPRECATED_FUS_METADATA_DIR = "event-log-whitelist";
  public static final String FUS_METADATA_DIR = "event-log-metadata";

  /**
   * This property should be only used in cases when it's impossible to set up a custom schema in another way,
   * e.g., via the Statistics Event Log tool window.
   * In particular, this allows applying a custom schema during the initial IDE startup.
   */
  private static final String CUSTOM_FUS_SCHEMA_DIR_PROPERTY = "intellij.fus.custom.schema.dir";

  public abstract @Nullable String getCachedEventsScheme();

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

  public static @NotNull Path getDeprecatedMetadataDir() {
    return getMetadataConfigRoot(DEPRECATED_FUS_METADATA_DIR);
  }

  private static @NotNull Path getMetadataByDir(@NotNull String dir, @NotNull String recorderId, @NotNull String fileName) throws IOException {
    Path metadataDir = getMetadataConfigRoot(dir);
    return metadataDir
      .resolve(StringUtil.toLowerCase(recorderId))
      .resolve(fileName)
      .normalize().toAbsolutePath();
  }

  private static @NotNull Path getMetadataConfigRoot(@NotNull String dir) {
    String customFusPath = System.getProperty(CUSTOM_FUS_SCHEMA_DIR_PROPERTY);
    if (!StringUtil.isEmpty(customFusPath)) {
      return Path.of(customFusPath);
    }

    return PathManager.getConfigDir().resolve(dir);
  }
}

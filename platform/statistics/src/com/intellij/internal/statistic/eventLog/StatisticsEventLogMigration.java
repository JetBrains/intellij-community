// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog;

import com.intellij.internal.statistic.eventLog.validator.storage.persistence.BaseEventLogMetadataPersistence;
import com.intellij.internal.statistic.service.fus.collectors.FUStatisticsPersistence;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.NioFiles;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class StatisticsEventLogMigration {

  public static void performMigration() {
    moveLogsToNewFolder();

    clearDeprecatedMetadataFolder();
    FUStatisticsPersistence.clearLegacyStates();
  }

  private static void clearDeprecatedMetadataFolder() {
    deleteDir(BaseEventLogMetadataPersistence.getDeprecatedMetadataDir());
  }

  private static void moveLogsToNewFolder() {
    Path newEventLogDir = EventLogConfiguration.getInstance().getEventLogDataPath().resolve("logs");

    Path systemPath = Paths.get(PathManager.getSystemPath());
    Path legacyDirectory = systemPath.resolve("event-log");
    if (Files.exists(legacyDirectory)) {
      copyDirContent(legacyDirectory.toFile(), newEventLogDir.resolve("FUS").toFile());
      deleteDir(legacyDirectory);
    }

    Path oldPluginsLogs = systemPath.resolve("plugins-event-log");
    if (Files.exists(oldPluginsLogs)) {
      File[] pluginLogDirectories = oldPluginsLogs.toFile().listFiles();
      if (pluginLogDirectories != null) {
        for (File pluginsDirectory : pluginLogDirectories) {
          copyDirContent(pluginsDirectory, newEventLogDir.resolve(pluginsDirectory.getName()).toFile());
        }
        deleteDir(oldPluginsLogs);
      }
    }
  }

  private static void deleteDir(Path path) {
    try {
      NioFiles.deleteRecursively(path);
    }
    catch (IOException ignored) {
    }
  }

  private static void copyDirContent(File fromDir, File toDir) {
    try {
      FileUtil.copyDirContent(fromDir, toDir);
    }
    catch (IOException ignored) {
    }
  }
}

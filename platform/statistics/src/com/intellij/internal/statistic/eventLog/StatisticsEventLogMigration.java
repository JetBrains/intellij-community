// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog;

import com.intellij.concurrency.JobScheduler;
import com.intellij.internal.statistic.eventLog.validator.persistence.BaseEventLogWhitelistPersistence;
import com.intellij.internal.statistic.service.fus.collectors.FUStatisticsPersistence;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.io.FileUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

public final class StatisticsEventLogMigration {

  public static void performMigration() {
    JobScheduler.getScheduler().schedule(() -> {
      moveLogsToNewFolder();

      clearDeprecatedMetadataFolder();
      FUStatisticsPersistence.clearLegacyStates();
    }, 5, TimeUnit.MINUTES);
  }

  private static void clearDeprecatedMetadataFolder() {
    Path deprecated = BaseEventLogWhitelistPersistence.getDeprecatedMetadataDir();
    if (Files.exists(deprecated)) {
      deleteDir(deprecated);
    }
  }

  private static void moveLogsToNewFolder() {
    Path newEventLogDir = EventLogConfiguration.INSTANCE.getEventLogDataPath().resolve("logs");

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
      FileUtil.delete(path);
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

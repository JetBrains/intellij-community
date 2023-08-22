// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus.collectors;

import com.intellij.internal.statistic.eventLog.EventLogConfiguration;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Paths;

public final class FUStatisticsPersistence {
  private static final Logger LOG = Logger.getInstance(FUStatisticsPersistence.class);

  private static final String LEGACY_PERSISTENCE_STATE_FILE = "fus-previous-state.data";
  private static final String PERSISTENCE_STATE_FILE = "fus-state.data";
  private static final String SENT_DATA_FILE = "fus-sent-data.json";
  public static final String FUS_CACHE_PATH = "fus-sessions";

  public static void clearLegacyStates() {
    deleteCaches(getSentDataFile());
    deleteCaches(getLegacyStateFile());
    deleteCaches(getPersistenceStateFile());
    deleteCaches(getStatisticsCacheDirectory());
    deleteCaches(getStatisticsLegacyCacheDirectory());
    deleteSystemEventIdsFiles();
  }

  private static void deleteSystemEventIdsFiles() {
    try {
      File[] files = EventLogConfiguration.getInstance().getEventLogSettingsPath().toFile()
        .listFiles((directory, name) -> name.endsWith("_system_event_id"));
      if (files != null) {
        for (File file : files) {
          FileUtil.delete(file);
        }
      }
    }
    catch (Exception e) {
      LOG.info(e);
    }
  }

  private static void deleteCaches(@Nullable File dir) {
    if (dir != null && dir.exists()) {
      try {
        final boolean delete = FileUtil.delete(dir);
        if (!delete) {
          LOG.info("Failed deleting legacy caches");
        }
      }
      catch (Exception e) {
        LOG.info(e);
      }
    }
  }

  @NotNull
  public static File getStatisticsCacheDirectory() {
    return PathManager.getConfigDir().resolve(FUS_CACHE_PATH + "/").toFile();
  }

  @NotNull
  public static File getStatisticsLegacyCacheDirectory() {
    return Paths.get(PathManager.getSystemPath()).resolve(FUS_CACHE_PATH).toFile();
  }

  @NotNull
  public static File getPersistenceStateFile() {
    return getFileInStatisticsCacheDirectory(PERSISTENCE_STATE_FILE);
  }

  @NotNull
  private static File getLegacyStateFile() {
    return getFileInStatisticsCacheDirectory(LEGACY_PERSISTENCE_STATE_FILE);
  }

  @NotNull
  private static File getFileInStatisticsCacheDirectory(@NotNull String fileName) {
    return new File(getStatisticsCacheDirectory(), "/" + fileName);
  }

  @NotNull
  public static File getSentDataFile() {
    return getFileInStatisticsCacheDirectory(SENT_DATA_FILE);
  }

}

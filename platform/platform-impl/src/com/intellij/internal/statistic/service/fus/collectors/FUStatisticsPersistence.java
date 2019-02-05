// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus.collectors;

import com.intellij.internal.statistic.eventLog.EventLogExternalSettingsService;
import com.intellij.internal.statistic.service.fus.beans.FSContent;
import com.intellij.internal.statistic.service.fus.beans.FSSession;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;

/**
 * Persists ProjectUsagesCollector data between user sessions (project + IJ build)
 */
public class FUStatisticsPersistence {
  private static final Logger
    LOG = Logger.getInstance("com.intellij.internal.statistic.service.fus.collectors.FUStatisticsPersistence");

  private static final String FILE_EXTENSION = "json";
  private static final String LEGACY_PERSISTENCE_STATE_FILE = "fus-previous-state.data";
  private static final String PERSISTENCE_STATE_FILE = "fus-state.data";
  private static final String SENT_DATA_FILE = "fus-sent-data.json";
  public static final String FUS_CACHE_PATH = "fus-sessions";

  /**
   * This method is regularly invoked by the statistics scheduler (see StatisticsJobsScheduler) to persist statistics data for current project.
   * Persisted data will be used by statistics service if this project isn't available at the statistics sending time
   * Method requests actual "approved" usages collectors (see FUStatisticsWhiteListGroupsService) to be invoked.
   * if FUStatisticsWhiteListGroupsService is OFFLINE the data will NOT collected.
   * Collected data are persisted in system cache. One file for one project session. The session is pair: project + IJ build number
   */
  public static void persistProjectUsages(@NotNull Project project) {
    recordProjectUsages(project);
  }

  private static void recordProjectUsages(@NotNull Project project) {
    final Set<String> groups = EventLogExternalSettingsService.getInstance().getApprovedGroups();
    if (!groups.isEmpty()  || ApplicationManagerEx.getApplicationEx().isInternal()) {
      FUStatisticsAggregator.create(true).getProjectUsages(project, groups);
    }
  }

  /**
   * Iterates system cache persisted session files and converts json file content to FSSession format
   */
  @NotNull
  public static Set<FSSession> getPersistedSessions() {
    Set<FSSession> persistedSessions = ContainerUtil.newHashSet();
    File statisticsCacheDir = getStatisticsCacheDirectory();
    if (statisticsCacheDir != null) {
      File[] children = statisticsCacheDir.listFiles();
      if (children != null) {
        for (File child : children) {
          if (PERSISTENCE_STATE_FILE.equals(child.getName())) continue;
          if (SENT_DATA_FILE.equals(child.getName())) continue;
          if (isSessionCacheName(child.getName())) {
            try {
              mergeContent(persistedSessions, FileUtil.loadFile(child));
            }
            catch (IOException e) {
              LOG.info(e);
            }
          }
        }
      }
    }
    return persistedSessions;
  }

  /**
   * Statistics service (FUStatisticsService) collects and sends data.
   * If this data is accepted by online JB statistics service (response status is "ok")
   * persisted sessions cache must be cleaned to avoid  repeatable sending.
   * This method cleans obsolete statistics persisted data (files).
   */
  public static void clearSessionPersistence(long dataTime) {
    File statisticsCacheDir = getStatisticsCacheDirectory();
    if (statisticsCacheDir != null) {
      File[] children = statisticsCacheDir.listFiles();
      if (children != null) {
        for (File child : children) {
          if (PERSISTENCE_STATE_FILE.equals(child.getName())) continue;
          if (SENT_DATA_FILE.equals(child.getName())) continue;
          try {
            BasicFileAttributes attr = Files.readAttributes(child.toPath(), BasicFileAttributes.class);
            if (dataTime > attr.creationTime().toMillis()) {
              child.delete();
            }
          }
          catch (IOException e) {
            LOG.info(e);
          }
        }
      }
    }
  }

  public static void clearLegacyStates() {
    deleteCaches(getSentDataFile());
    deleteCaches(getLegacyStateFile());
    deleteCaches(getPersistenceStateFile());
    deleteCaches(getStatisticsCacheDirectory());
    deleteCaches(getStatisticsLegacyCacheDirectory());
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

  @Nullable
  public static File getStatisticsCacheDirectory() {
    return Paths.get(PathManager.getConfigPath()).resolve(FUS_CACHE_PATH + "/").toFile();
  }

  @Nullable
  public static File getStatisticsLegacyCacheDirectory() {
    return Paths.get(PathManager.getSystemPath()).resolve(FUS_CACHE_PATH).toFile();
  }

  private static void mergeContent(@NotNull Set<FSSession> allSessions, @Nullable String content) {
    if (StringUtil.isEmptyOrSpaces(content)) return;
    try {
      FSContent sessionContent = FSContent.fromJson(content);
      if (sessionContent == null) return;
      Set<FSSession> sessions = sessionContent.getSessions();
      if (sessions != null) {
        allSessions.addAll(sessions);
      }
    }
    catch (Exception e) {
      LOG.info(e);
    }
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

  @Nullable
  public static String getPreviousStateContent() {
    return getStateContent(PERSISTENCE_STATE_FILE);
  }

  @Nullable
  public static String getLegacyStateContent() {
    return getStateContent(LEGACY_PERSISTENCE_STATE_FILE);
  }

  @Nullable
  private static String getStateContent(@NotNull String fileName) {
    return null;
  }

  private static boolean isSessionCacheName(@NotNull String fileName) {
    return FILE_EXTENSION.equals(FileUtilRt.getExtension(fileName));
  }
}

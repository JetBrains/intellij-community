// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus.collectors;

import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.service.fus.FUStatisticsSettingsService;
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
import java.util.Map;
import java.util.Set;

// persists ProjectUsagesCollector data between user sessions (project + IJ build)
public class FUStatisticsPersistence {
  private static final Logger
    LOG = Logger.getInstance("com.intellij.internal.statistic.service.fus.collectors.FUStatisticsPersistence");

  private static final String FILE_EXTENSION = "json";
  private static final String PERSISTENCE_STATE_FILE = "fus-previous-state.data";
  private static final String SENT_DATA_FILE = "fus-sent-data.json";
  public static final String FUS_CACHE_PATH = "fus-sessions";

  // 1. this method is regularly  invoked by the statistics scheduler (see StatisticsJobsScheduler) to persist statistics data  for current project.
  // 2. persisted data will be used by statistics service if this project isn't available at the statistics sending time
  // 3.  method requests actual "approved" usages collectors (see FUStatisticsWhiteListGroupsService) to be invoked.
  //     if FUStatisticsWhiteListGroupsService is OFFLINE the data will NOT collected.
  // 4. collected data are persisted in system cache. one file for one project session. the session is pair: project + IJ build number
  public static String persistProjectUsages(@NotNull Project project) {
    Set<String> groups = FUStatisticsSettingsService.getInstance().getApprovedGroups();
    if (groups.isEmpty() && !ApplicationManagerEx.getApplicationEx().isInternal()) return null;
    FUStatisticsAggregator aggregator = FUStatisticsAggregator.create();
    Map<String, Set<UsageDescriptor>> usages = aggregator.getProjectUsages(project, groups);
    if (usages.isEmpty()) return null;

    FUSession fuSession = FUSession.create(project);

    FSContent content = FSContent.create();
    FUStatisticsAggregator.writeContent(content, fuSession, usages);

    String gsonContent = content.asJsonString();

    String fileName = getFileName(fuSession);
    File directory = getStatisticsSystemCacheDirectory();

    persistToFile(gsonContent, new File(directory, "/" + fileName));
    return fileName;
  }

  @NotNull
  // this method iterates system cache persisted session files and convert json file content to FSSession format
  public static Set<FSSession> getPersistedSessions() {
    Set<FSSession> persistedSessions = ContainerUtil.newHashSet();
    File statisticsCacheDir = getStatisticsSystemCacheDirectory();
    if (statisticsCacheDir != null) {
      File[] children = statisticsCacheDir.listFiles();
      if (children != null) {
        for (File child : children) {
          if(PERSISTENCE_STATE_FILE.equals(child.getName())) continue;
          if(SENT_DATA_FILE.equals(child.getName())) continue;
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

  // Statistics service (FUStatisticsService) collects and sends data.
  // if this data is accepted by online JB statistics service (response status is "ok")
  // persisted sessions cache must be cleaned to avoid  repeatable sending.
  // This method cleans obsolete statistics persisted data (files)
  public static void clearSessionPersistence(long dataTime) {
    File statisticsCacheDir = getStatisticsSystemCacheDirectory();
    if (statisticsCacheDir != null) {
      File[] children = statisticsCacheDir.listFiles();
      if (children != null) {
        for (File child : children) {
          if(PERSISTENCE_STATE_FILE.equals(child.getName())) continue;
          if(SENT_DATA_FILE.equals(child.getName())) continue;
          try {
            BasicFileAttributes attr = Files.readAttributes(child.toPath(), BasicFileAttributes.class);
            if (dataTime > attr.creationTime().toMillis()) {
              child.delete();
            }
          } catch (IOException e) {
            LOG.info(e);
          }
        }
      }
    }
  }

  @NotNull
  private static String getFileName(@NotNull FUSession session) {
     return session.hashCode() +"."+ FILE_EXTENSION;
  }

  @Nullable
  public static File getStatisticsSystemCacheDirectory() {
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
    }  catch (Exception e) {
      LOG.info(e);
    }
  }
  public static void persistDataFromCollectors(@NotNull String content) {
    persistToFile(content, getPersistenceStateFile());
  }

  public static void persistSentData(@NotNull String sentContent) {
    persistToFile(sentContent, getSentDataFile());
  }

  public static void persistToFile(@NotNull String sentContent, File file) {
    try {
      FileUtil.writeToFile(file, sentContent);
    } catch (IOException e) {
      LOG.info(e);
    }
  }

  @NotNull
  public static File getPersistenceStateFile() {
    return new File(getStatisticsSystemCacheDirectory(), "/" + PERSISTENCE_STATE_FILE);
  }
  @NotNull
  public static File getSentDataFile() {
    return new File(getStatisticsSystemCacheDirectory(), "/" + SENT_DATA_FILE);
  }

  @Nullable
  public static String getPreviousStateContent() {
    File statisticsCacheDir = getStatisticsSystemCacheDirectory();
    if (statisticsCacheDir != null) {
      File stateFile = getPersistenceStateFile();
      if (stateFile.exists()) {
        try {
          return FileUtil.loadFile(stateFile);
        }
        catch (IOException e) {
          LOG.info(e);
        }
      }
    }
    return null;
  }

  private static boolean isSessionCacheName(@NotNull String fileName) {
    return FILE_EXTENSION.equals(FileUtilRt.getExtension(fileName));
  }
}

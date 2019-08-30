// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator.persistence;

import com.intellij.internal.statistic.eventLog.EventLogExternalSettingsService;
import com.intellij.internal.statistic.service.fus.FUStatisticsWhiteListGroupsService;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class EventLogWhitelistPersistence extends BaseEventLogWhitelistPersistence {
  public static final String WHITE_LIST_DATA_FILE = "white-list.json";
  private static final Logger LOG =
    Logger.getInstance("#com.intellij.internal.statistic.eventLog.validator.persistence.EventLogWhitelistPersistence");
  @NotNull
  private final String myRecorderId;
  @NotNull
  private final EventLogExternalSettingsService mySettingsService;
  @NotNull
  private final EventLogWhitelistSettingsPersistence myWhitelistSettingsPersistence;

  public EventLogWhitelistPersistence(@NotNull String recorderId) {
    myRecorderId = recorderId;
    mySettingsService = new EventLogExternalSettingsService(recorderId);
    myWhitelistSettingsPersistence = EventLogWhitelistSettingsPersistence.getInstance();
  }

  @Override
  @Nullable
  public String getCachedWhitelist() {
    try {
      WhitelistPathSettings settings = myWhitelistSettingsPersistence.getPathSettings(myRecorderId);
      File file;
      if (settings != null && settings.isUseCustomPath()) {
        file = new File(settings.getCustomPath());
      }
      else {
        file = getDefaultPath();
        if (!file.exists()) initBuiltinWhiteList(file);
      }
      if (file.exists()) return FileUtil.loadFile(file);
    }
    catch (IOException e) {
      LOG.error(e);
    }
    return null;
  }

  public void updateWhiteListIfNeeded() {
    WhitelistPathSettings settings = myWhitelistSettingsPersistence.getPathSettings(myRecorderId);
    if (settings != null && settings.isUseCustomPath()) {
      return;
    }

    final long lastModified = FUStatisticsWhiteListGroupsService.lastModifiedWhitelist(mySettingsService);
    if (LOG.isTraceEnabled()) {
      LOG.trace(
        "Loading whitelist, last modified cached=" + getLastModified() +
        ", last modified on the server=" + lastModified
      );
    }

    if (lastModified <= 0 || lastModified > getLastModified()) {
      final String content = FUStatisticsWhiteListGroupsService.loadWhiteListFromServer(mySettingsService);
      if (StringUtil.isNotEmpty(content)) {
        cacheWhiteList(content, lastModified);
        if (LOG.isTraceEnabled()) {
          LOG.trace("Update local whitelist, last modified cached=" + getLastModified());
        }
      }
    }
  }

  private void cacheWhiteList(@NotNull String gsonWhiteListContent, long lastModified) {
    try {
      final File file = getDefaultPath();
      FileUtil.writeToFile(file, gsonWhiteListContent);
      myWhitelistSettingsPersistence.setLastModified(myRecorderId, lastModified);
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  private void initBuiltinWhiteList(File file) throws IOException {
    try (InputStream stream = getClass().getClassLoader().getResourceAsStream(builtinWhiteListPath())) {
      if (stream == null) return;
      if (!file.getParentFile().mkdirs()) {
        throw new IOException("Unable to create " + file.getParentFile().getAbsolutePath());
      }
      Files.copy(stream, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private String builtinWhiteListPath() {
    return "resources/" + FUS_WHITELIST_PATH + "/" + myRecorderId + "/" + WHITE_LIST_DATA_FILE;
  }

  private long getLastModified() {
    return myWhitelistSettingsPersistence.getLastModified(myRecorderId);
  }

  @NotNull
  private File getDefaultPath() throws IOException {
    return getDefaultWhitelistPath(myRecorderId, WHITE_LIST_DATA_FILE);
  }
}

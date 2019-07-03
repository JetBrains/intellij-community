// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator.persistence;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;


public class EventLogWhitelistPersistence extends BaseEventLogWhiteListPersistence{
  private static final Logger
    LOG = Logger.getInstance("com.intellij.internal.statistic.eventLog.validator.persistence.EventLogWhitelistPersistence");

  private static final String WHITE_LIST_DATA_FILE = "white-list.json";

  public EventLogWhitelistPersistence(@NotNull String recorderId) {
    super(recorderId);
  }

  public void cacheWhiteList(@NotNull String gsonWhiteListContent, long lastModified) {
    File file = getWhiteListFile();
    try {
      FileUtil.writeToFile(file, gsonWhiteListContent);
      EventLogWhitelistSettingsPersistence.getInstance().setLastModified(myRecorderId, lastModified);
    } catch (IOException e) {
      LOG.error(e);
    }
  }

  public long getLastModified() {
    return EventLogWhitelistSettingsPersistence.getInstance().getLastModified(myRecorderId);
  }

  @Override
  Logger getLogger() {
    return LOG;
  }

  @Override
  String getWhiteListDataFileName() {
    return WHITE_LIST_DATA_FILE;
  }
}

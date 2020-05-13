// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.whitelist;

import com.intellij.internal.statistic.eventLog.EventLogSystemLogger;
import com.intellij.internal.statistic.eventLog.validator.persistence.EventLogWhitelistPersistence;
import com.intellij.internal.statistic.eventLog.validator.rules.beans.WhiteListGroupRules;
import com.intellij.internal.statistic.service.fus.EventLogWhitelistLoadException;
import com.intellij.internal.statistic.service.fus.EventLogWhitelistParseException;
import com.intellij.internal.statistic.service.fus.FUStatisticsWhiteListGroupsService;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.concurrency.Semaphore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class WhitelistStorage extends BaseWhitelistStorage {
  private static final Logger LOG = Logger.getInstance(WhitelistStorage.class);

  protected final ConcurrentMap<String, WhiteListGroupRules> eventsValidators = new ConcurrentHashMap<>();
  private final @NotNull Semaphore mySemaphore;
  private final @NotNull String myRecorderId;
  private @Nullable String myVersion;
  private final @NotNull EventLogWhitelistPersistence myWhitelistPersistence;
  private final @NotNull EventLogWhitelistLoader myWhitelistLoader;

  WhitelistStorage(@NotNull String recorderId) {
    myRecorderId = recorderId;
    mySemaphore = new Semaphore();
    myWhitelistPersistence = new EventLogWhitelistPersistence(recorderId);
    myWhitelistLoader = new EventLogServerWhitelistLoader(recorderId);
    myVersion = loadValidatorsFromLocalCache(recorderId);
  }

  @TestOnly
  protected WhitelistStorage(@NotNull String recorderId,
                             @NotNull EventLogWhitelistPersistence persistence,
                             @NotNull EventLogWhitelistLoader loader) {
    myRecorderId = recorderId;
    mySemaphore = new Semaphore();
    myWhitelistPersistence = persistence;
    myWhitelistLoader = loader;
    myVersion = loadValidatorsFromLocalCache(recorderId);
  }

  @Override
  public @Nullable WhiteListGroupRules getGroupRules(@NotNull String groupId) {
    return eventsValidators.get(groupId);
  }

  private @Nullable String loadValidatorsFromLocalCache(@NotNull String recorderId) {
    String whiteListContent = myWhitelistPersistence.getCachedWhitelist();
    if (whiteListContent != null) {
      try {
        String newVersion = updateValidators(whiteListContent);
        EventLogSystemLogger.logWhitelistLoad(recorderId, newVersion);
        return newVersion;
      }
      catch (EventLogWhitelistParseException e) {
        EventLogSystemLogger.logWhitelistErrorOnLoad(myRecorderId, e);
      }
    }
    return null;
  }

  private @Nullable String updateValidators(@NotNull String whiteListContent) throws EventLogWhitelistParseException {
    mySemaphore.down();
    try {
      FUStatisticsWhiteListGroupsService.WLGroups groups = FUStatisticsWhiteListGroupsService.parseWhiteListContent(whiteListContent);
      Map<String, WhiteListGroupRules> result = createValidators(groups);
      isWhiteListInitialized.set(false);
      eventsValidators.clear();
      eventsValidators.putAll(result);

      isWhiteListInitialized.set(true);
      return groups.version;
    }
    finally {
      mySemaphore.up();
    }
  }

  @Override
  public void update() {
    long lastModifiedLocally = myWhitelistPersistence.getLastModified();
    long lastModifiedOnServer = myWhitelistLoader.getLastModifiedOnServer();
    if (LOG.isTraceEnabled()) {
      LOG.trace(
        "Loading whitelist, last modified cached=" + lastModifiedLocally +
        ", last modified on the server=" + lastModifiedOnServer
      );
    }

    try {
      if (lastModifiedOnServer <= 0 || lastModifiedOnServer > lastModifiedLocally || isUnreachableWhitelist()) {
        String whitelistFromServer = myWhitelistLoader.loadWhiteListFromServer();
        String version = updateValidators(whitelistFromServer);
        myWhitelistPersistence.cacheWhiteList(whitelistFromServer, lastModifiedOnServer);
        if (LOG.isTraceEnabled()) {
          LOG.trace("Update local whitelist, last modified cached=" + myWhitelistPersistence.getLastModified());
        }

        if (version != null && !StringUtil.equals(version, myVersion)) {
          myVersion = version;
          EventLogSystemLogger.logWhitelistUpdated(myRecorderId, myVersion);
        }
      }
    }
    catch (EventLogWhitelistLoadException | EventLogWhitelistParseException e) {
      EventLogSystemLogger.logWhitelistErrorOnUpdate(myRecorderId, e);
    }
  }

  @Override
  public void reload() {
    myVersion = loadValidatorsFromLocalCache(myRecorderId);
  }
}

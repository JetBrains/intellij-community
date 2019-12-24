// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.whitelist;

import com.intellij.internal.statistic.eventLog.EventLogSystemLogger;
import com.intellij.internal.statistic.eventLog.validator.persistence.EventLogWhitelistPersistence;
import com.intellij.internal.statistic.eventLog.validator.rules.beans.WhiteListGroupRules;
import com.intellij.internal.statistic.service.fus.FUStatisticsWhiteListGroupsService;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.Map;
import java.util.concurrent.ConcurrentMap;

public class WhitelistStorage extends BaseWhitelistStorage {
  protected final ConcurrentMap<String, WhiteListGroupRules> eventsValidators = ContainerUtil.newConcurrentMap();
  @NotNull
  private final Semaphore mySemaphore;
  @NotNull
  private final String myRecorderId;
  @Nullable
  private String myVersion;
  @NotNull
  private final EventLogWhitelistPersistence myWhitelistPersistence;

  WhitelistStorage(@NotNull String recorderId) {
    myRecorderId = recorderId;
    mySemaphore = new Semaphore();
    myWhitelistPersistence = new EventLogWhitelistPersistence(recorderId);
    myVersion = updateValidators();
    EventLogSystemLogger.logWhitelistLoad(recorderId, myVersion);
  }

  @TestOnly
  protected WhitelistStorage(@NotNull String recorderId, @NotNull EventLogWhitelistPersistence eventLogWhitelistPersistence) {
    myRecorderId = recorderId;
    mySemaphore = new Semaphore();
    myWhitelistPersistence = eventLogWhitelistPersistence;
    myVersion = updateValidators();
    EventLogSystemLogger.logWhitelistLoad(recorderId, myVersion);
  }

  @Nullable
  @Override
  public WhiteListGroupRules getGroupRules(@NotNull String groupId) {
    return eventsValidators.get(groupId);
  }

  @Nullable
  private String updateValidators() {
    String whiteListContent = myWhitelistPersistence.getCachedWhitelist();
    if (whiteListContent != null) {
      mySemaphore.down();
      try {
        eventsValidators.clear();
        isWhiteListInitialized.set(false);
        FUStatisticsWhiteListGroupsService.WLGroups groups = FUStatisticsWhiteListGroupsService.parseWhiteListContent(whiteListContent);
        if (groups != null) {
          Map<String, WhiteListGroupRules> result = createValidators(groups);

          eventsValidators.putAll(result);

          isWhiteListInitialized.set(true);
        }
        return groups == null ? null : groups.version;
      }
      finally {
        mySemaphore.up();
      }
    }
    return null;
  }

  @Override
  public void update() {
    myWhitelistPersistence.updateWhiteListIfNeeded();
    final String version = updateValidators();
    if (!StringUtil.equals(version, myVersion)) {
      myVersion = version;
      EventLogSystemLogger.logWhitelistUpdated(myRecorderId, myVersion);
    }
  }

  public void reload() {
    updateValidators();
  }
}

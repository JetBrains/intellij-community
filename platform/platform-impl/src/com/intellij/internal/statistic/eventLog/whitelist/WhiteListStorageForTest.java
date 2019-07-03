// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.whitelist;

import com.intellij.internal.statistic.eventLog.validator.persistence.EventLogTestWhitelistPersistence;
import com.intellij.internal.statistic.eventLog.validator.rules.beans.WhiteListGroupRules;
import com.intellij.internal.statistic.service.fus.FUStatisticsWhiteListGroupsService;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

public class WhiteListStorageForTest extends BaseWhiteListStorage {
  private static final ConcurrentMap<String, WhiteListStorageForTest> instances = ContainerUtil.newConcurrentMap();
  protected final ConcurrentMap<String, WhiteListGroupRules> eventsValidators = ContainerUtil.newConcurrentMap();
  private final Object myLock = new Object();
  private final String myRecorderId;

  @NotNull
  public static WhiteListStorageForTest getInstance(@NotNull String recorderId) {
    return instances.computeIfAbsent(recorderId, id -> new WhiteListStorageForTest(id));
  }

  public WhiteListStorageForTest(String id) {
    myRecorderId = id;
    updateValidators();
  }

  @NotNull
  @Override
  public Map<String, WhiteListGroupRules> getEventsValidators() {
    return eventsValidators;
  }

  private void updateValidators() {
    synchronized (myLock) {
      eventsValidators.clear();
      isWhiteListInitialized.set(false);
      FUStatisticsWhiteListGroupsService.WLGroups groups =
        EventLogTestWhitelistPersistence.loadTestWhitelist(new EventLogTestWhitelistPersistence(myRecorderId));
      final Map<String, WhiteListGroupRules> result = createValidators(groups);

      eventsValidators.putAll(result);
      isWhiteListInitialized.set(true);
    }
  }

  public void addGroupWithCustomRules(@NotNull String recorderId, @NotNull String groupId, @NotNull String rules) throws IOException {
    EventLogTestWhitelistPersistence.addGroupWithCustomRules(recorderId, groupId, rules);
    updateValidators();
  }

  public void addTestGroup(@NotNull String recorderId, @NotNull String groupId) throws IOException {
    EventLogTestWhitelistPersistence.addTestGroup(recorderId, groupId);
    updateValidators();
  }
}

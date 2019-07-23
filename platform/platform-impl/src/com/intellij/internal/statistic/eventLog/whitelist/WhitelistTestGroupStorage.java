// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.whitelist;

import com.intellij.internal.statistic.eventLog.validator.persistence.EventLogTestWhitelistPersistence;
import com.intellij.internal.statistic.eventLog.validator.rules.beans.WhiteListGroupRules;
import com.intellij.internal.statistic.service.fus.FUStatisticsWhiteListGroupsService;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

public class WhitelistTestGroupStorage extends BaseWhitelistStorage {
  private static final ConcurrentMap<String, WhitelistTestGroupStorage> ourTestInstances = ContainerUtil.newConcurrentMap();

  protected final ConcurrentMap<String, WhiteListGroupRules> eventsValidators = ContainerUtil.newConcurrentMap();
  private final Object myLock = new Object();
  @NotNull
  private final EventLogTestWhitelistPersistence myTestWhitelistPersistence;
  @NotNull
  private final String myRecorderId;

  public static WhitelistTestGroupStorage getInstance(@NotNull String recorderId) {
    return ourTestInstances.computeIfAbsent(
      recorderId,
      id -> new WhitelistTestGroupStorage(id)
    );
  }

  private WhitelistTestGroupStorage(@NotNull String recorderId) {
    myTestWhitelistPersistence = new EventLogTestWhitelistPersistence(recorderId);
    updateValidators();
    myRecorderId = recorderId;
  }

  @Nullable
  @Override
  public WhiteListGroupRules getGroupRules(@NotNull String groupId) {
    return eventsValidators.get(groupId);
  }

  @Override
  public void update() {
  }

  private void updateValidators() {
    synchronized (myLock) {
      eventsValidators.clear();
      isWhiteListInitialized.set(false);
      FUStatisticsWhiteListGroupsService.WLGroups groups =
        EventLogTestWhitelistPersistence.loadTestWhitelist(myTestWhitelistPersistence);
      final Map<String, WhiteListGroupRules> result = createValidators(groups);

      eventsValidators.putAll(result);
      isWhiteListInitialized.set(true);
    }
  }

  public void addGroupWithCustomRules(@NotNull String groupId, @NotNull String rules) throws IOException {
    EventLogTestWhitelistPersistence.addGroupWithCustomRules(myRecorderId, groupId, rules);
    updateValidators();
  }

  public void addTestGroup(@NotNull String groupId) throws IOException {
    EventLogTestWhitelistPersistence.addTestGroup(myRecorderId, groupId);
    updateValidators();
  }

  public void cleanup() {
    synchronized (myLock) {
      eventsValidators.clear();
      myTestWhitelistPersistence.cleanup();
    }
  }

  public static void cleanupAll() {
    for (WhitelistTestGroupStorage value : ourTestInstances.values()) {
      value.cleanup();
    }
  }
}

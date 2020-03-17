// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.whitelist;

import com.intellij.internal.statistic.eventLog.EventLogConfiguration;
import com.intellij.internal.statistic.eventLog.validator.persistence.EventLogTestWhitelistPersistence;
import com.intellij.internal.statistic.eventLog.validator.persistence.EventLogWhitelistPersistence;
import com.intellij.internal.statistic.eventLog.validator.rules.beans.WhiteListGroupRules;
import com.intellij.internal.statistic.service.fus.FUStatisticsWhiteListGroupsService.WLGroups;
import com.intellij.internal.statistic.service.fus.FUStatisticsWhiteListGroupsService.WLRule;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

public class WhitelistTestGroupStorage extends BaseWhitelistStorage {
  private static final ConcurrentMap<String, WhitelistTestGroupStorage> ourTestInstances = ContainerUtil.newConcurrentMap();

  protected final ConcurrentMap<String, WhiteListGroupRules> eventsValidators = ContainerUtil.newConcurrentMap();
  private final Object myLock = new Object();
  @NotNull
  private final EventLogTestWhitelistPersistence myTestWhitelistPersistence;
  @NotNull
  private final EventLogWhitelistPersistence myWhitelistPersistence;
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
    myWhitelistPersistence = new EventLogWhitelistPersistence(recorderId);
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
      final WLGroups productionGroups = EventLogTestWhitelistPersistence.loadTestWhitelist(myWhitelistPersistence);
      final WLGroups testGroups = EventLogTestWhitelistPersistence.loadTestWhitelist(myTestWhitelistPersistence);
      final Map<String, WhiteListGroupRules> result = createValidators(testGroups, productionGroups);

      eventsValidators.putAll(result);
      isWhiteListInitialized.set(true);
    }
  }

  @NotNull
  protected Map<String, WhiteListGroupRules> createValidators(@NotNull WLGroups groups, @NotNull WLGroups productionGroups) {
    final WLRule rules = merge(groups.rules, productionGroups.rules);
    final BuildNumber buildNumber = BuildNumber.fromString(EventLogConfiguration.INSTANCE.getBuild());
    return groups.groups.stream().
      filter(group -> group.accepts(buildNumber)).
      collect(Collectors.toMap(group -> group.id, group -> createRules(group, rules)));
  }

  @Nullable
  private static WLRule merge(@Nullable WLRule testRules, @Nullable WLRule productionTestRules) {
    if (testRules == null) return productionTestRules;
    if (productionTestRules == null) return testRules;

    final WLRule rule = new WLRule();
    copyRules(rule, productionTestRules);
    copyRules(rule, testRules);
    return rule;
  }

  private static void copyRules(@NotNull WLRule to, @NotNull WLRule from) {
    if (to.enums == null) {
      to.enums = ContainerUtil.newHashMap();
    }
    if (to.regexps == null) {
      to.regexps = ContainerUtil.newHashMap();
    }

    if (from.enums != null) {
      to.enums.putAll(from.enums);
    }
    if (from.regexps != null) {
      to.regexps.putAll(from.regexps);
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

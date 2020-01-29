// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.whitelist;

import com.intellij.internal.statistic.eventLog.EventLogBuildNumber;
import com.intellij.internal.statistic.eventLog.EventLogConfiguration;
import com.intellij.internal.statistic.eventLog.StatisticsEventLoggerKt;
import com.intellij.internal.statistic.eventLog.validator.SensitiveDataValidator;
import com.intellij.internal.statistic.eventLog.validator.persistence.EventLogTestWhitelistPersistence;
import com.intellij.internal.statistic.eventLog.validator.persistence.EventLogWhitelistPersistence;
import com.intellij.internal.statistic.eventLog.validator.rules.beans.WhiteListGroupRules;
import com.intellij.internal.statistic.service.fus.FUStatisticsWhiteListGroupsService.WLGroups;
import com.intellij.internal.statistic.service.fus.FUStatisticsWhiteListGroupsService.WLRule;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

public class WhitelistTestGroupStorage extends BaseWhitelistStorage {
  protected final ConcurrentMap<String, WhiteListGroupRules> eventsValidators = ContainerUtil.newConcurrentMap();
  private final Object myLock = new Object();
  @NotNull
  private final EventLogTestWhitelistPersistence myTestWhitelistPersistence;
  @NotNull
  private final EventLogWhitelistPersistence myWhitelistPersistence;
  @NotNull
  private final String myRecorderId;

  WhitelistTestGroupStorage(@NotNull String recorderId) {
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

  public void updateValidators() {
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
    final EventLogBuildNumber buildNumber = EventLogBuildNumber.fromString(EventLogConfiguration.INSTANCE.getBuild());
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

  protected void cleanup() {
    synchronized (myLock) {
      eventsValidators.clear();
      myTestWhitelistPersistence.cleanup();
    }
  }

  public static void cleanupAll() {
    List<String> recorders = StatisticsEventLoggerKt.getEventLogProviders().stream().
      filter(provider -> provider.isRecordEnabled()).
      map(provider -> provider.getRecorderId()).
      collect(Collectors.toList());
    cleanupAll(recorders);
  }

  public static void cleanupAll(List<String> recorders) {
    for (String recorderId : recorders) {
      WhitelistTestGroupStorage testWhitelist = getTestStorage(recorderId);
      if (testWhitelist != null) {
        testWhitelist.cleanup();
      }
    }
  }

  @Nullable
  public static WhitelistTestGroupStorage getTestStorage(String recorderId) {
    SensitiveDataValidator validator = SensitiveDataValidator.getIfInitialized(recorderId);
    WhitelistGroupRulesStorage storage = validator != null ? validator.getWhiteListStorage() : null;
    return storage instanceof WhitelistTestRulesStorageHolder ? ((WhitelistTestRulesStorageHolder)storage).getTestGroupStorage() : null;
  }

  public int size() {
    synchronized (myLock) {
      return eventsValidators.size();
    }
  }
}

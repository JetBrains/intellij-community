// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.whitelist;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellij.internal.statistic.eventLog.EventLogBuild;
import com.intellij.internal.statistic.eventLog.EventLogConfiguration;
import com.intellij.internal.statistic.eventLog.StatisticsEventLoggerKt;
import com.intellij.internal.statistic.eventLog.validator.SensitiveDataValidator;
import com.intellij.internal.statistic.eventLog.validator.persistence.EventLogTestWhitelistPersistence;
import com.intellij.internal.statistic.eventLog.validator.persistence.EventLogWhitelistPersistence;
import com.intellij.internal.statistic.eventLog.validator.rules.beans.EventGroupRules;
import com.intellij.internal.statistic.service.fus.FUStatisticsWhiteListGroupsService;
import com.intellij.internal.statistic.service.fus.FUStatisticsWhiteListGroupsService.WLGroups;
import com.intellij.internal.statistic.service.fus.FUStatisticsWhiteListGroupsService.WLRule;
import com.intellij.internal.statistic.service.fus.StatisticsWhitelistGroupConditions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

public class WhitelistTestGroupStorage extends BaseWhitelistStorage {
  protected final ConcurrentMap<String, EventGroupRules> eventsValidators = new ConcurrentHashMap<>();
  private final Object myLock = new Object();
  private final @NotNull EventLogTestWhitelistPersistence myTestWhitelistPersistence;
  private final @NotNull EventLogWhitelistPersistence myWhitelistPersistence;
  private final @NotNull String myRecorderId;

  WhitelistTestGroupStorage(@NotNull String recorderId) {
    myTestWhitelistPersistence = new EventLogTestWhitelistPersistence(recorderId);
    myWhitelistPersistence = new EventLogWhitelistPersistence(recorderId);
    updateValidators();
    myRecorderId = recorderId;
  }

  @Override
  public @Nullable EventGroupRules getGroupRules(@NotNull String groupId) {
    return eventsValidators.get(groupId);
  }

  @Override
  public void update() {
    updateValidators();
  }

  @Override
  public void reload() {
    updateValidators();
  }

  private void updateValidators() {
    synchronized (myLock) {
      eventsValidators.clear();
      isWhiteListInitialized.set(false);
      WLGroups productionGroups = EventLogTestWhitelistPersistence.loadTestWhitelist(myWhitelistPersistence);
      WLGroups testGroups = EventLogTestWhitelistPersistence.loadTestWhitelist(myTestWhitelistPersistence);
      final Map<String, EventGroupRules> result = createValidators(testGroups, productionGroups.rules);

      eventsValidators.putAll(result);
      isWhiteListInitialized.set(true);
    }
  }

  public @NotNull WLGroups loadProductionGroups() {
    return EventLogTestWhitelistPersistence.loadTestWhitelist(myWhitelistPersistence);
  }

  protected @NotNull Map<String, EventGroupRules> createValidators(@NotNull WLGroups groups,
                                                                   @Nullable WLRule productionRules) {
    final WLRule rules = merge(groups.rules, productionRules);
    final EventLogBuild build = EventLogBuild.fromString(EventLogConfiguration.INSTANCE.getBuild());
    return groups.groups.stream().
      filter(group -> StatisticsWhitelistGroupConditions.create(group).accepts(build)).
      collect(Collectors.toMap(group -> group.id, group -> createRules(group, rules)));
  }

  public void addTestGroup(@NotNull LocalWhitelistGroup group) throws IOException {
    EventLogTestWhitelistPersistence.addTestGroup(myRecorderId, group);
    updateValidators();
  }

  protected void cleanup() {
    synchronized (myLock) {
      eventsValidators.clear();
      myTestWhitelistPersistence.cleanup();
    }
  }

  public @NotNull List<LocalWhitelistGroup> loadLocalWhitelistGroups() {
    ArrayList<FUStatisticsWhiteListGroupsService.WLGroup> localWhitelistGroups =
      EventLogTestWhitelistPersistence.loadTestWhitelist(myTestWhitelistPersistence).groups;
    ArrayList<LocalWhitelistGroup> groups = new ArrayList<>();
    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    for (FUStatisticsWhiteListGroupsService.WLGroup group : localWhitelistGroups) {
      if (group.id == null || group.rules == null || group.rules.event_id == null) continue;
      Set<String> eventIds = group.rules.event_id;
      if (eventIds.contains(EventLogTestWhitelistPersistence.TEST_RULE)) {
        groups.add(new LocalWhitelistGroup(group.id, false));
      }
      else {
        groups.add(new LocalWhitelistGroup(group.id, true, gson.toJson(group.rules)));
      }
    }
    return groups;
  }

  public void updateTestGroups(@NotNull List<LocalWhitelistGroup> groups) throws IOException {
    myTestWhitelistPersistence.updateTestGroups(groups);
    updateValidators();
  }

  private static @Nullable WLRule merge(@Nullable WLRule testRules, @Nullable WLRule productionTestRules) {
    if (testRules == null) return productionTestRules;
    if (productionTestRules == null) return testRules;

    final WLRule rule = new WLRule();
    copyRules(rule, productionTestRules);
    copyRules(rule, testRules);
    return rule;
  }

  private static void copyRules(@NotNull WLRule to, @NotNull WLRule from) {
    if (to.enums == null) {
      to.enums = new HashMap<>();
    }
    if (to.regexps == null) {
      to.regexps = new HashMap<>();
    }

    if (from.enums != null) {
      to.enums.putAll(from.enums);
    }
    if (from.regexps != null) {
      to.regexps.putAll(from.regexps);
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

  public static @Nullable WhitelistTestGroupStorage getTestStorage(String recorderId) {
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

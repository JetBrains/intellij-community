// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellij.internal.statistic.eventLog.EventLogBuild;
import com.intellij.internal.statistic.eventLog.EventLogConfiguration;
import com.intellij.internal.statistic.eventLog.StatisticsEventLoggerKt;
import com.intellij.internal.statistic.eventLog.connection.metadata.EventGroupFilterRules;
import com.intellij.internal.statistic.eventLog.connection.metadata.EventGroupRemoteDescriptors;
import com.intellij.internal.statistic.eventLog.connection.metadata.EventGroupRemoteDescriptors.EventGroupRemoteDescriptor;
import com.intellij.internal.statistic.eventLog.connection.metadata.EventGroupRemoteDescriptors.GroupRemoteRule;
import com.intellij.internal.statistic.eventLog.validator.SensitiveDataValidator;
import com.intellij.internal.statistic.eventLog.validator.rules.beans.EventGroupRules;
import com.intellij.internal.statistic.eventLog.validator.storage.persistence.EventLogMetadataPersistence;
import com.intellij.internal.statistic.eventLog.validator.storage.persistence.EventLogTestMetadataPersistence;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

public final class ValidationTestRulesPersistedStorage extends BaseValidationRulesPersistedStorage {
  protected final ConcurrentMap<String, EventGroupRules> eventsValidators = new ConcurrentHashMap<>();
  private final Object myLock = new Object();
  private final @NotNull EventLogTestMetadataPersistence myTestMetadataPersistence;
  private final @NotNull EventLogMetadataPersistence myMetadataPersistence;
  private final @NotNull String myRecorderId;

  ValidationTestRulesPersistedStorage(@NotNull String recorderId) {
    myTestMetadataPersistence = new EventLogTestMetadataPersistence(recorderId);
    myMetadataPersistence = new EventLogMetadataPersistence(recorderId);
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
      isInitialized.set(false);
      EventGroupRemoteDescriptors productionGroups = EventLogTestMetadataPersistence.loadCachedEventGroupsSchemes(myMetadataPersistence);
      EventGroupRemoteDescriptors testGroups = EventLogTestMetadataPersistence.loadCachedEventGroupsSchemes(myTestMetadataPersistence);
      final Map<String, EventGroupRules> result = createValidators(testGroups, productionGroups.rules);

      eventsValidators.putAll(result);
      isInitialized.set(true);
    }
  }

  public @NotNull EventGroupRemoteDescriptors loadProductionGroups() {
    return EventLogTestMetadataPersistence.loadCachedEventGroupsSchemes(myMetadataPersistence);
  }

  protected @NotNull Map<String, EventGroupRules> createValidators(@NotNull EventGroupRemoteDescriptors groups,
                                                                   @Nullable GroupRemoteRule productionRules) {
    final GroupRemoteRule rules = merge(groups.rules, productionRules);
    final EventLogBuild build = EventLogBuild.fromString(EventLogConfiguration.INSTANCE.getBuild());
    return groups.groups.stream().
      filter(group -> EventGroupFilterRules.create(group).accepts(build)).
      collect(Collectors.toMap(group -> group.id, group -> EventGroupRules.create(group, new GlobalRulesHolder(rules))));
  }

  public void addTestGroup(@NotNull GroupValidationTestRule group) throws IOException {
    EventLogTestMetadataPersistence.addTestGroup(myRecorderId, group);
    updateValidators();
  }

  protected void cleanup() {
    synchronized (myLock) {
      eventsValidators.clear();
      myTestMetadataPersistence.cleanup();
    }
  }

  public @NotNull List<GroupValidationTestRule> loadValidationTestRules() {
    ArrayList<EventGroupRemoteDescriptor> testGroupsSchemes =
      EventLogTestMetadataPersistence.loadCachedEventGroupsSchemes(myTestMetadataPersistence).groups;
    ArrayList<GroupValidationTestRule> groups = new ArrayList<>();
    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    for (EventGroupRemoteDescriptor group : testGroupsSchemes) {
      if (group.id == null || group.rules == null || group.rules.event_id == null) continue;
      Set<String> eventIds = group.rules.event_id;
      if (eventIds.contains(EventLogTestMetadataPersistence.TEST_RULE)) {
        groups.add(new GroupValidationTestRule(group.id, false));
      }
      else {
        groups.add(new GroupValidationTestRule(group.id, true, gson.toJson(group.rules)));
      }
    }
    return groups;
  }

  public void updateTestGroups(@NotNull List<GroupValidationTestRule> groups) throws IOException {
    myTestMetadataPersistence.updateTestGroups(groups);
    updateValidators();
  }

  private static @Nullable GroupRemoteRule merge(@Nullable GroupRemoteRule testRules, @Nullable GroupRemoteRule productionTestRules) {
    if (testRules == null) return productionTestRules;
    if (productionTestRules == null) return testRules;

    final GroupRemoteRule rule = new GroupRemoteRule();
    copyRules(rule, productionTestRules);
    copyRules(rule, testRules);
    return rule;
  }

  private static void copyRules(@NotNull GroupRemoteRule to, @NotNull GroupRemoteRule from) {
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
      ValidationTestRulesPersistedStorage testStorage = getTestStorage(recorderId, false);
      if (testStorage != null) {
        testStorage.cleanup();
      }
    }
  }

  public static @Nullable ValidationTestRulesPersistedStorage getTestStorage(@NotNull String recorderId, boolean initIfNeeded) {
    SensitiveDataValidator validator =
      initIfNeeded ? SensitiveDataValidator.getInstance(recorderId) : SensitiveDataValidator.getIfInitialized(recorderId);
    ValidationRulesStorage storage = validator != null ? validator.getValidationRulesStorage() : null;
    return storage instanceof ValidationTestRulesStorageHolder ? ((ValidationTestRulesStorageHolder)storage).getTestGroupStorage() : null;
  }

  public int size() {
    synchronized (myLock) {
      return eventsValidators.size();
    }
  }
}

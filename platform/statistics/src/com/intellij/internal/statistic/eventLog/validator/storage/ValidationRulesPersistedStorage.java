// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator.storage;

import com.intellij.internal.statistic.eventLog.*;
import com.intellij.internal.statistic.eventLog.connection.metadata.*;
import com.intellij.internal.statistic.eventLog.validator.rules.beans.EventGroupRules;
import com.intellij.internal.statistic.eventLog.validator.rules.utils.CustomRuleProducer;
import com.intellij.internal.statistic.eventLog.validator.rules.utils.ValidationSimpleRuleFactory;
import com.intellij.internal.statistic.eventLog.validator.storage.persistence.EventLogMetadataPersistence;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.concurrency.Semaphore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class ValidationRulesPersistedStorage implements IntellijValidationRulesStorage {
  private static final Logger LOG = Logger.getInstance(ValidationRulesPersistedStorage.class);

  protected final ConcurrentMap<String, EventGroupRules> eventsValidators = new ConcurrentHashMap<>();
  private final @NotNull Semaphore mySemaphore;
  private final @NotNull String myRecorderId;
  private @Nullable String myVersion;
  private final @NotNull EventLogMetadataPersistence myMetadataPersistence;
  private final @NotNull EventLogMetadataLoader myMetadataLoader;
  private final @NotNull AtomicBoolean myIsInitialized;

  ValidationRulesPersistedStorage(@NotNull String recorderId) {
    myIsInitialized = new AtomicBoolean(false);
    myRecorderId = recorderId;
    mySemaphore = new Semaphore();
    myMetadataPersistence = new EventLogMetadataPersistence(recorderId);
    myMetadataLoader = new EventLogServerMetadataLoader(recorderId);
    myVersion = loadValidatorsFromLocalCache(recorderId);
  }

  @TestOnly
  protected ValidationRulesPersistedStorage(@NotNull String recorderId,
                                            @NotNull EventLogMetadataPersistence persistence,
                                            @NotNull EventLogMetadataLoader loader) {
    myIsInitialized = new AtomicBoolean(false);
    myRecorderId = recorderId;
    mySemaphore = new Semaphore();
    myMetadataPersistence = persistence;
    myMetadataLoader = loader;
    myVersion = loadValidatorsFromLocalCache(recorderId);
  }

  @Override
  public @Nullable EventGroupRules getGroupRules(@NotNull String groupId) {
    return eventsValidators.get(groupId);
  }

  private @Nullable String loadValidatorsFromLocalCache(@NotNull String recorderId) {
    String rawEventsScheme = myMetadataPersistence.getCachedEventsScheme();
    if (rawEventsScheme != null) {
      try {
        String newVersion = updateValidators(rawEventsScheme);
        EventLogSystemLogger.logMetadataLoad(recorderId, newVersion);
        return newVersion;
      }
      catch (EventLogMetadataParseException e) {
        EventLogSystemLogger.logMetadataErrorOnLoad(myRecorderId, e);
      }
    }
    return null;
  }

  private @Nullable String updateValidators(@NotNull String rawEventsScheme) throws EventLogMetadataParseException {
    mySemaphore.down();
    try {
      EventGroupRemoteDescriptors groups = EventLogMetadataUtils.parseGroupRemoteDescriptors(rawEventsScheme);
      EventLogBuild build = EventLogBuild.fromString(EventLogConfiguration.INSTANCE.getBuild());
      Map<String, EventGroupRules> result = createValidators(build, groups);
      myIsInitialized.set(false);
      eventsValidators.clear();
      eventsValidators.putAll(result);

      myIsInitialized.set(true);
      return groups.version;
    }
    finally {
      mySemaphore.up();
    }
  }

  @Override
  public void update() {
    EventLogConfigOptionsService.getInstance().updateOptions(myRecorderId, myMetadataLoader);

    long lastModifiedLocally = myMetadataPersistence.getLastModified();
    long lastModifiedOnServer = myMetadataLoader.getLastModifiedOnServer();
    if (LOG.isTraceEnabled()) {
      LOG.trace(
        "Loading events scheme, last modified cached=" + lastModifiedLocally +
        ", last modified on the server=" + lastModifiedOnServer
      );
    }

    try {
      if (lastModifiedOnServer <= 0 || lastModifiedOnServer > lastModifiedLocally || isUnreachable()) {
        String rawEventsSchemeFromServer = myMetadataLoader.loadMetadataFromServer();
        String version = updateValidators(rawEventsSchemeFromServer);
        myMetadataPersistence.cacheEventsScheme(rawEventsSchemeFromServer, lastModifiedOnServer);
        if (LOG.isTraceEnabled()) {
          LOG.trace("Update local events scheme, last modified cached=" + myMetadataPersistence.getLastModified());
        }

        if (version != null && !StringUtil.equals(version, myVersion)) {
          myVersion = version;
          EventLogSystemLogger.logMetadataUpdated(myRecorderId, myVersion);
        }
      }
    }
    catch (EventLogMetadataLoadException | EventLogMetadataParseException e) {
      EventLogSystemLogger.logMetadataErrorOnUpdate(myRecorderId, e);
    }
  }

  @Override
  public void reload() {
    myVersion = loadValidatorsFromLocalCache(myRecorderId);
  }

  @Override
  public boolean isUnreachable() {
    return !myIsInitialized.get();
  }

  @NotNull
  protected Map<String, EventGroupRules> createValidators(@Nullable EventLogBuild build, @NotNull EventGroupRemoteDescriptors groups) {
    GlobalRulesHolder globalRulesHolder = new GlobalRulesHolder(groups.rules);
    return createValidators(build, groups, globalRulesHolder, myRecorderId);
  }

  @NotNull
  public static Map<String, EventGroupRules> createValidators(@Nullable EventLogBuild build,
                                                              @NotNull EventGroupRemoteDescriptors groups,
                                                              @NotNull GlobalRulesHolder globalRulesHolder,
                                                              @NotNull String recorderId) {
    ValidationSimpleRuleFactory ruleFactory = new ValidationSimpleRuleFactory(new CustomRuleProducer(recorderId));
    return groups.groups.stream()
      .filter(group -> EventGroupFilterRules.create(group, EventLogBuild.EVENT_LOG_BUILD_PRODUCER).accepts(build))
      .collect(Collectors.toMap(group -> group.id, group -> {
        return EventGroupRules.create(group, globalRulesHolder, ruleFactory, FeatureUsageData.Companion.getPlatformDataKeys());
      }));
  }
}

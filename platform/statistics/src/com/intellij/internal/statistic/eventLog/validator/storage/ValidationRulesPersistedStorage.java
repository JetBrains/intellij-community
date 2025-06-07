// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.validator.storage;

import com.intellij.internal.statistic.eventLog.*;
import com.intellij.internal.statistic.eventLog.connection.metadata.EventGroupFilterRules;
import com.intellij.internal.statistic.eventLog.connection.metadata.EventLogMetadataLoadException;
import com.intellij.internal.statistic.eventLog.connection.metadata.EventLogMetadataParseException;
import com.intellij.internal.statistic.eventLog.connection.metadata.EventLogMetadataUtils;
import com.intellij.internal.statistic.eventLog.validator.rules.beans.EventGroupRules;
import com.intellij.internal.statistic.eventLog.validator.rules.utils.CustomRuleProducer;
import com.intellij.internal.statistic.eventLog.validator.rules.utils.ValidationSimpleRuleFactory;
import com.intellij.internal.statistic.eventLog.validator.storage.persistence.EventLogMetadataPersistence;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.concurrency.Semaphore;
import com.jetbrains.fus.reporting.model.metadata.EventGroupRemoteDescriptors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static com.intellij.internal.statistic.eventLog.StatisticsEventLogProviderUtil.getEventLogProvider;

public class ValidationRulesPersistedStorage implements IntellijValidationRulesStorage {
  private static final Logger LOG = Logger.getInstance(ValidationRulesPersistedStorage.class);

  protected volatile Map<String, EventGroupRules> eventsValidators = Map.of();

  private final @NotNull Semaphore mySemaphore;
  private final @NotNull String myRecorderId;
  private @Nullable String myVersion;
  private final @NotNull EventLogMetadataPersistence myMetadataPersistence;
  private final @NotNull EventLogMetadataLoader myMetadataLoader;
  private final @NotNull AtomicBoolean myIsInitialized;
  private final @NotNull EventLogSystemCollector eventLogSystemCollector;

  ValidationRulesPersistedStorage(@NotNull String recorderId) {
    myIsInitialized = new AtomicBoolean(false);
    myRecorderId = recorderId;
    mySemaphore = new Semaphore();
    myMetadataPersistence = new EventLogMetadataPersistence(recorderId);
    myMetadataLoader = new EventLogServerMetadataLoader(recorderId);
    eventLogSystemCollector = getEventLogProvider(myRecorderId).getEventLogSystemLogger$intellij_platform_statistics();
    myVersion = loadValidatorsFromLocalCache();
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
    eventLogSystemCollector = getEventLogProvider(myRecorderId).getEventLogSystemLogger$intellij_platform_statistics();
    myVersion = loadValidatorsFromLocalCache();
  }

  @Override
  public @Nullable EventGroupRules getGroupRules(@NotNull String groupId) {
    return eventsValidators.get(groupId);
  }

  private @Nullable String loadValidatorsFromLocalCache() {
    String rawEventsScheme = myMetadataPersistence.getCachedEventsScheme();
    if (rawEventsScheme != null) {
      try {
        String newVersion = updateValidators(rawEventsScheme);
        eventLogSystemCollector.logMetadataLoaded(newVersion);
        return newVersion;
      }
      catch (EventLogMetadataParseException e) {
        eventLogSystemCollector.logMetadataLoadFailed(e);
      }
    }
    return null;
  }

  private @Nullable String updateValidators(@NotNull String rawEventsScheme) throws EventLogMetadataParseException {
    mySemaphore.down();
    try {
      EventGroupRemoteDescriptors groups = EventLogMetadataUtils.parseGroupRemoteDescriptors(rawEventsScheme);
      EventLogBuild build = EventLogBuild.fromString(EventLogConfiguration.getInstance().getBuild());
      Map<String, EventGroupRules> result = createValidators(build, groups);
      myIsInitialized.set(false);

      eventsValidators = Map.copyOf(result);

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
          eventLogSystemCollector.logMetadataUpdated(myVersion);
        }
      }
    }
    catch (EventLogMetadataLoadException | EventLogMetadataParseException e) {
      eventLogSystemCollector.logMetadataUpdateFailed(e);
    }
  }

  @Override
  public void reload() {
    myVersion = loadValidatorsFromLocalCache();
  }

  @Override
  public boolean isUnreachable() {
    return !myIsInitialized.get();
  }

  protected @NotNull Map<String, EventGroupRules> createValidators(@Nullable EventLogBuild build, @NotNull EventGroupRemoteDescriptors groups) {
    GlobalRulesHolder globalRulesHolder = new GlobalRulesHolder(groups.rules);
    return createValidators(build, groups, globalRulesHolder, myRecorderId);
  }

  public static @NotNull Map<String, EventGroupRules> createValidators(@Nullable EventLogBuild build,
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

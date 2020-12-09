// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator.storage;

import com.intellij.internal.statistic.eventLog.EventLogBuild;
import com.intellij.internal.statistic.eventLog.EventLogConfiguration;
import com.intellij.internal.statistic.eventLog.EventLogSystemLogger;
import com.intellij.internal.statistic.eventLog.connection.metadata.EventGroupRemoteDescriptors;
import com.intellij.internal.statistic.eventLog.connection.metadata.EventLogMetadataLoadException;
import com.intellij.internal.statistic.eventLog.connection.metadata.EventLogMetadataParseException;
import com.intellij.internal.statistic.eventLog.connection.metadata.EventLogMetadataUtils;
import com.intellij.internal.statistic.eventLog.validator.storage.persistence.EventLogMetadataPersistence;
import com.intellij.internal.statistic.eventLog.validator.rules.beans.EventGroupRules;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.concurrency.Semaphore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ValidationRulesPersistedStorage extends BaseValidationRulesPersistedStorage {
  private static final Logger LOG = Logger.getInstance(ValidationRulesPersistedStorage.class);

  protected final ConcurrentMap<String, EventGroupRules> eventsValidators = new ConcurrentHashMap<>();
  private final @NotNull Semaphore mySemaphore;
  private final @NotNull String myRecorderId;
  private @Nullable String myVersion;
  private final @NotNull EventLogMetadataPersistence myMetadataPersistence;
  private final @NotNull EventLogMetadataLoader myMetadataLoader;

  ValidationRulesPersistedStorage(@NotNull String recorderId) {
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
      isInitialized.set(false);
      eventsValidators.clear();
      eventsValidators.putAll(result);

      isInitialized.set(true);
      return groups.version;
    }
    finally {
      mySemaphore.up();
    }
  }

  @Override
  public void update() {
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
}

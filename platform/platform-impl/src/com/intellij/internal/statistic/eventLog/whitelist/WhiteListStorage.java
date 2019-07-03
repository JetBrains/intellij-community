// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.whitelist;

import com.intellij.internal.statistic.eventLog.EventLogConfiguration;
import com.intellij.internal.statistic.eventLog.EventLogExternalSettingsService;
import com.intellij.internal.statistic.eventLog.EventLogSystemLogger;
import com.intellij.internal.statistic.eventLog.validator.persistence.EventLogWhitelistPersistence;
import com.intellij.internal.statistic.eventLog.validator.rules.beans.WhiteListGroupRules;
import com.intellij.internal.statistic.service.fus.FUStatisticsWhiteListGroupsService;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class WhiteListStorage implements WhiteListGroupRulesStorage {
  private static final Logger LOG = Logger.getInstance("com.intellij.internal.statistic.eventLog.whitelist.WhiteListStorage");

  private static final ConcurrentMap<String, WhiteListStorage> instances = ContainerUtil.newConcurrentMap();

  protected final Map<String, WhiteListGroupRules> eventsValidators = ContainerUtil.newConcurrentMap();
  private final Semaphore mySemaphore;
  private final String myRecorderId;
  private final AtomicBoolean isWhiteListInitialized;
  private String myVersion;
  private final EventLogWhitelistPersistence myWhitelistPersistence;
  private final EventLogExternalSettingsService mySettingsService;

  @NotNull
  public static WhiteListStorage getInstance(@NotNull String recorderId) {
    return instances.computeIfAbsent(
      recorderId,
      id -> new WhiteListStorage(id)
    );
  }

  public WhiteListStorage(@NotNull String recorderId) {
    myRecorderId = recorderId;
    mySemaphore = new Semaphore();
    isWhiteListInitialized = new AtomicBoolean(false);
    myWhitelistPersistence = new EventLogWhitelistPersistence(recorderId);
    mySettingsService = new EventLogExternalSettingsService(recorderId);
    myVersion = updateValidators(myWhitelistPersistence.getCachedWhiteList());
    EventLogSystemLogger.logWhitelistLoad(recorderId, myVersion);
  }

  @Override
  @NotNull
  public Map<String, WhiteListGroupRules> getEventsValidators() {
    return eventsValidators;
  }

  @Nullable
  private String updateValidators(@Nullable String whiteListContent) {
    if (whiteListContent != null) {
      mySemaphore.down();
      try {
        eventsValidators.clear();
        isWhiteListInitialized.set(false);
        FUStatisticsWhiteListGroupsService.WLGroups groups = FUStatisticsWhiteListGroupsService.parseWhiteListContent(whiteListContent);
        if (groups != null) {
          final BuildNumber buildNumber = BuildNumber.fromString(EventLogConfiguration.INSTANCE.getBuild());
          final Map<String, WhiteListGroupRules> result = groups.groups.stream().
            filter(group -> group.accepts(buildNumber)).
            collect(Collectors.toMap(group -> group.id, group -> createRules(group, groups.rules)));

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

  @NotNull
  private static WhiteListGroupRules createRules(@NotNull FUStatisticsWhiteListGroupsService.WLGroup group,
                                                 @Nullable FUStatisticsWhiteListGroupsService.WLRule globalRules) {
    return globalRules != null
           ? WhiteListGroupRules.create(group, globalRules.enums, globalRules.regexps)
           : WhiteListGroupRules.create(group, null, null);
  }

  public void update() {
    final String version = updateValidators(getWhiteListContent());
    if (!StringUtil.equals(version, myVersion)) {
      myVersion = version;
      EventLogSystemLogger.logWhitelistUpdated(myRecorderId, myVersion);
    }
  }

  protected String getWhiteListContent() {
    final long lastModified = FUStatisticsWhiteListGroupsService.lastModifiedWhitelist(mySettingsService);
    if (LOG.isTraceEnabled()) {
      LOG.trace(
        "Loading whitelist, last modified cached=" + myWhitelistPersistence.getLastModified() +
        ", last modified on the server=" + lastModified
      );
    }

    if (lastModified <= 0 || lastModified > myWhitelistPersistence.getLastModified()) {
      final String content = FUStatisticsWhiteListGroupsService.loadWhiteListFromServer(mySettingsService);
      if (StringUtil.isNotEmpty(content)) {
        myWhitelistPersistence.cacheWhiteList(content, lastModified);
        if (LOG.isTraceEnabled()) {
          LOG.trace("Update local whitelist, last modified cached=" + myWhitelistPersistence.getLastModified());
        }
        return content;
      }
    }
    return myWhitelistPersistence.getCachedWhiteList();
  }

  @Override
  public boolean isUnreachableWhitelist() {
    return !isWhiteListInitialized.get();
  }

  public void reload() {
    updateValidators(myWhitelistPersistence.getCachedWhiteList());
  }

  public boolean shouldUpdateCache(@NotNull String gsonWhiteListContent) {
    int cachedVersion = getVersion(myWhitelistPersistence.getCachedWhiteList());

    return cachedVersion == 0 || getVersion(gsonWhiteListContent) > cachedVersion;
  }

  private static int getVersion(@Nullable String whiteListContent) {
    if (whiteListContent == null) return 0;
    FUStatisticsWhiteListGroupsService.WLGroups groups = FUStatisticsWhiteListGroupsService.parseWhiteListContent(whiteListContent);
    if (groups == null) return 0;
    String version = groups.version;
    if (version == null) return 0;
    try {
      return Integer.parseInt(version);
    }
    catch (Exception ignored) {
    }
    return 0;
  }
}

// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator;

import com.intellij.internal.statistic.eventLog.*;
import com.intellij.internal.statistic.eventLog.validator.persistence.EventLogWhitelistPersistence;
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext;
import com.intellij.internal.statistic.eventLog.validator.rules.beans.WhiteListGroupRules;
import com.intellij.internal.statistic.service.fus.FUStatisticsWhiteListGroupsService;
import com.intellij.openapi.application.ApplicationManager;
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

import static com.intellij.internal.statistic.eventLog.validator.ValidationResultType.*;
import static com.intellij.internal.statistic.utils.StatisticsUtilKt.addPluginInfoTo;

public class SensitiveDataValidator {
  private static final Logger LOG = Logger.getInstance("com.intellij.internal.statistic.eventLog.validator.SensitiveDataValidator");
  private static final ConcurrentMap<String, SensitiveDataValidator> instances = ContainerUtil.newConcurrentMap();

  private final String myRecorderId;
  private final Semaphore mySemaphore;
  private final AtomicBoolean isWhiteListInitialized;
  protected final Map<String, WhiteListGroupRules> eventsValidators = ContainerUtil.newConcurrentMap();

  private String myVersion;
  private final EventLogWhitelistPersistence myWhitelistPersistence;
  private final EventLogExternalSettingsService mySettingsService;

  @NotNull
  public static SensitiveDataValidator getInstance(@NotNull String recorderId) {
    return instances.computeIfAbsent(
      recorderId,
      id -> ApplicationManager.getApplication().isUnitTestMode() ? new BlindSensitiveDataValidator(id) : new SensitiveDataValidator(id)
    );
  }

  protected SensitiveDataValidator(@NotNull String recorderId) {
    myRecorderId = recorderId;
    mySemaphore = new Semaphore();
    isWhiteListInitialized = new AtomicBoolean(false);
    myWhitelistPersistence = new EventLogWhitelistPersistence(recorderId);
    mySettingsService = new EventLogExternalSettingsService(recorderId);

    myVersion = updateValidators(myWhitelistPersistence.getCachedWhiteList());
    EventLogSystemLogger.logWhitelistLoad(recorderId, myVersion);
  }

  public String guaranteeCorrectEventId(@NotNull EventLogGroup group,
                                        @NotNull EventContext context) {
    if (isUnreachableWhitelist()) return UNREACHABLE_WHITELIST.getDescription();
    if (isSystemEventId(context.eventId)) return context.eventId;

    ValidationResultType validationResultType = validateEvent(group, context);
    return validationResultType == ACCEPTED ? context.eventId : validationResultType.getDescription();
  }

  public Map<String, Object> guaranteeCorrectEventData(@NotNull EventLogGroup group, @NotNull EventContext context) {
    WhiteListGroupRules whiteListRule = eventsValidators.get(group.getId());

    Map<String, Object> validatedData =
      ContainerUtil.newConcurrentMap(); // TODO: don't create validatedData map if all keys are accepted (just return context.eventData)
    for (Map.Entry<String, Object> entry : context.eventData.entrySet()) {
      String key = entry.getKey();
      Object entryValue = entry.getValue();

      ValidationResultType resultType = validateEventData(context, whiteListRule, key, entryValue);
      validatedData.put(key, resultType == ACCEPTED ? entryValue : resultType.getDescription());
    }

    if (context.pluginInfo != null && !(validatedData.containsKey("plugin") || validatedData.containsKey("plugin_type"))) {
      addPluginInfoTo(context.pluginInfo, validatedData);
    }
    return validatedData;
  }

  public SensitiveDataValidator update() {
    final String version = updateValidators(getWhiteListContent());
    if (!StringUtil.equals(version, myVersion)) {
      myVersion = version;
      EventLogSystemLogger.logWhitelistUpdated(myRecorderId, myVersion);
    }
    return this;
  }

  public void reload() {
    updateValidators(myWhitelistPersistence.getCachedWhiteList());
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

  private boolean isUnreachableWhitelist() {
    return !isWhiteListInitialized.get();
  }

  private static boolean isSystemEventId(@Nullable String eventId) {
    return "invoked".equals(eventId) || "registered".equals(eventId);
  }

  public ValidationResultType validateEvent(@NotNull EventLogGroup group, @NotNull EventContext context) {
    WhiteListGroupRules whiteListRule = eventsValidators.get(group.getId());
    if (whiteListRule == null || !whiteListRule.areEventIdRulesDefined()) {
      return UNDEFINED_RULE; // there are no rules (eventId and eventData) to validate
    }

    return whiteListRule.validateEventId(context);
  }

  private ValidationResultType validateEventData(@NotNull EventContext context,
                                                 @Nullable WhiteListGroupRules whiteListRule,
                                                 @NotNull String key,
                                                 @NotNull Object entryValue) {
    if (isUnreachableWhitelist()) return UNREACHABLE_WHITELIST;
    if (whiteListRule == null) return UNDEFINED_RULE;
    if (FeatureUsageData.Companion.getPlatformDataKeys().contains(key)) return ACCEPTED;
    return whiteListRule.validateEventData(key, entryValue, context);
  }

  @NotNull
  private static WhiteListGroupRules createRules(@NotNull FUStatisticsWhiteListGroupsService.WLGroup group,
                                                 @Nullable FUStatisticsWhiteListGroupsService.WLRule globalRules) {
    return globalRules != null
           ? WhiteListGroupRules.create(group, globalRules.enums, globalRules.regexps)
           : WhiteListGroupRules.create(group, null, null);
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


  private static class BlindSensitiveDataValidator extends SensitiveDataValidator {
    protected BlindSensitiveDataValidator(@NotNull String recorderId) {
      super(recorderId);
    }

    @Override
    public String guaranteeCorrectEventId(@NotNull EventLogGroup group, @NotNull EventContext context) {
      return context.eventId;
    }

    @Override
    public Map<String, Object> guaranteeCorrectEventData(@NotNull EventLogGroup group, @NotNull EventContext context) {
      return context.eventData;
    }
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
    } catch (Exception ignored) {
    }
    return 0;
  }
}

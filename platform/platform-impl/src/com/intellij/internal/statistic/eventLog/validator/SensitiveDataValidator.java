// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator;

import com.intellij.internal.statistic.eventLog.EventLogConfiguration;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.eventLog.validator.persistence.FUSWhiteListPersistence;
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext;
import com.intellij.internal.statistic.eventLog.validator.rules.beans.WhiteListGroupRules;
import com.intellij.internal.statistic.service.fus.FUStatisticsWhiteListGroupsService;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static com.intellij.internal.statistic.eventLog.validator.ValidationResultType.*;

public class SensitiveDataValidator {
  private static final NotNullLazyValue<SensitiveDataValidator> me = NotNullLazyValue.createValue(
    () -> ApplicationManager.getApplication().isUnitTestMode() ? new BlindSensitiveDataValidator() : new SensitiveDataValidator()
  );

  private final Semaphore mySemaphore;
  private final AtomicBoolean isWhiteListInitialized;
  protected final Map<String, WhiteListGroupRules> eventsValidators = ContainerUtil.newConcurrentMap();

  public static SensitiveDataValidator getInstance() {
    return me.getValue();
  }

  protected SensitiveDataValidator() {
    mySemaphore = new Semaphore();
    isWhiteListInitialized = new AtomicBoolean(false);
    updateValidators(FUSWhiteListPersistence.getCachedWhiteList());
  }

  public String guaranteeCorrectEventId(@NotNull EventLogGroup group,
                                        @NotNull EventContext context) {
    if (isUnreachableWhitelist()) return UNREACHABLE_WHITELIST.getDescription();

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
    return validatedData;
  }

  public SensitiveDataValidator update() {
    updateValidators(getWhiteListContent());
    return this;
  }

  private void updateValidators(@Nullable String whiteListContent) {
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
      }
      finally {
        mySemaphore.up();
      }
    }
  }

  private boolean isUnreachableWhitelist() {
    return !isWhiteListInitialized.get();
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
    String content = FUStatisticsWhiteListGroupsService.getFUSWhiteListContent();
    if (StringUtil.isNotEmpty(content)) {
      FUSWhiteListPersistence.cacheWhiteList(content);
      return content;
    }
    return FUSWhiteListPersistence.getCachedWhiteList();
  }


  private static class BlindSensitiveDataValidator extends SensitiveDataValidator {
    @Override
    public String guaranteeCorrectEventId(@NotNull EventLogGroup group, @NotNull EventContext context) {
      return context.eventId;
    }

    @Override
    public Map<String, Object> guaranteeCorrectEventData(@NotNull EventLogGroup group, @NotNull EventContext context) {
      return context.eventData;
    }
  }
}

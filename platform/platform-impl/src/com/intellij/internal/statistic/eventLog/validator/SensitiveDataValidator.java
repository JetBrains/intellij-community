// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator;

import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext;
import com.intellij.internal.statistic.eventLog.validator.rules.beans.WhiteListGroupRules;
import com.intellij.internal.statistic.eventLog.validator.rules.impl.TestModeValidationRule;
import com.intellij.internal.statistic.eventLog.whitelist.MergedWhiteListStorage;
import com.intellij.internal.statistic.eventLog.whitelist.WhiteListGroupRulesStorage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import static com.intellij.internal.statistic.eventLog.validator.ValidationResultType.*;
import static com.intellij.internal.statistic.utils.StatisticsUtilKt.addPluginInfoTo;

public class SensitiveDataValidator {
  private static final Logger LOG = Logger.getInstance("com.intellij.internal.statistic.eventLog.validator.SensitiveDataValidator");
  private static final ConcurrentMap<String, SensitiveDataValidator> instances = ContainerUtil.newConcurrentMap();

  protected final WhiteListGroupRulesStorage myWhiteListStorage;

  @NotNull
  public static SensitiveDataValidator getInstance(@NotNull String recorderId) {
    return instances.computeIfAbsent(
      recorderId,
      id -> {
        MergedWhiteListStorage whiteListStorage = MergedWhiteListStorage.getInstance(recorderId);
        return ApplicationManager.getApplication().isUnitTestMode()
               ? new BlindSensitiveDataValidator(whiteListStorage)
               : new SensitiveDataValidator(whiteListStorage);
      }
    );
  }

  protected SensitiveDataValidator(WhiteListGroupRulesStorage storage) {
    myWhiteListStorage = storage;
  }

  public String guaranteeCorrectEventId(@NotNull EventLogGroup group,
                                        @NotNull EventContext context) {
    if (myWhiteListStorage.isUnreachableWhitelist()) return UNREACHABLE_WHITELIST.getDescription();
    if (isSystemEventId(context.eventId)) return context.eventId;

    ValidationResultType validationResultType = validateEvent(group, context);
    return validationResultType == ACCEPTED ? context.eventId : validationResultType.getDescription();
  }

  public Map<String, Object> guaranteeCorrectEventData(@NotNull EventLogGroup group, @NotNull EventContext context) {
    WhiteListGroupRules whiteListRule = myWhiteListStorage.getEventsValidators().get(group.getId());
    if (isTestModeEnabled(whiteListRule)) {
      return context.eventData;
    }

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

  private static boolean isTestModeEnabled(@Nullable WhiteListGroupRules rule) {
    return TestModeValidationRule.isTestModeEnabled() && rule != null &&
           Arrays.stream(rule.getEventIdRules()).anyMatch( r -> r instanceof TestModeValidationRule);
  }

  //todo (ivanova) вынести эти методы
  public void update() {
    myWhiteListStorage.update();
  }

  public void reload() {
    myWhiteListStorage.reload();
  }

  private static boolean isSystemEventId(@Nullable String eventId) {
    return "invoked".equals(eventId) || "registered".equals(eventId);
  }

  public ValidationResultType validateEvent(@NotNull EventLogGroup group, @NotNull EventContext context) {
    WhiteListGroupRules whiteListRule = myWhiteListStorage.getEventsValidators().get(group.getId());
    if (whiteListRule == null || !whiteListRule.areEventIdRulesDefined()) {
      return UNDEFINED_RULE; // there are no rules (eventId and eventData) to validate
    }

    return whiteListRule.validateEventId(context);
  }

  private ValidationResultType validateEventData(@NotNull EventContext context,
                                                 @Nullable WhiteListGroupRules whiteListRule,
                                                 @NotNull String key,
                                                 @NotNull Object entryValue) {
    if (myWhiteListStorage.isUnreachableWhitelist()) return UNREACHABLE_WHITELIST;
    if (whiteListRule == null) return UNDEFINED_RULE;
    if (FeatureUsageData.Companion.getPlatformDataKeys().contains(key)) return ACCEPTED;
    return whiteListRule.validateEventData(key, entryValue, context);
  }


  private static class BlindSensitiveDataValidator extends SensitiveDataValidator {
    protected BlindSensitiveDataValidator(WhiteListGroupRulesStorage whiteListStorage) {
      super(whiteListStorage);
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
}

// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator.rules.beans;

import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType;
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext;
import com.intellij.internal.statistic.eventLog.validator.rules.FUSRule;
import com.intellij.internal.statistic.eventLog.validator.rules.impl.EnumValidationRule;
import com.intellij.internal.statistic.eventLog.validator.rules.utils.ValidationSimpleRuleFactory;
import com.intellij.internal.statistic.service.fus.FUStatisticsWhiteListGroupsService;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.SortedList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.intellij.internal.statistic.eventLog.validator.ValidationResultType.*;

public class EventGroupRules {
  public static final EventGroupRules EMPTY =
    new EventGroupRules(Collections.emptySet(), Collections.emptyMap(), EventGroupContextData.EMPTY);

  private final FUSRule[] eventIdRules;
  private final Map<String, FUSRule[]> eventDataRules = new ConcurrentHashMap<>();

  private EventGroupRules(@Nullable Set<String> eventIdRules,
                          @Nullable Map<String, Set<String>> eventDataRules, @NotNull EventGroupContextData contextData) {
    this.eventIdRules = getRules(eventIdRules, contextData);

    if (eventDataRules != null) {
      for (Map.Entry<String, Set<String>> entry : eventDataRules.entrySet()) {
        if (FeatureUsageData.Companion.getPlatformDataKeys().contains(entry.getKey())) {
          this.eventDataRules.put(entry.getKey(), new FUSRule[]{FUSRule.TRUE});
        }
        else {
          this.eventDataRules.put(entry.getKey(), getRules(entry.getValue(), contextData));
        }
      }
    }
  }

  public FUSRule[] getEventIdRules() {
    return eventIdRules;
  }

  public Map<String, FUSRule[]> getEventDataRules() {
    return eventDataRules;
  }

  private static FUSRule @NotNull [] getRules(@Nullable Set<String> rules,
                                              @NotNull EventGroupContextData contextData) {

    if (rules == null) return FUSRule.EMPTY_ARRAY;
    List<FUSRule> fusRules = new SortedList<>(getRulesComparator());
    for (String rule : rules) {
      ContainerUtil.addIfNotNull(fusRules, ValidationSimpleRuleFactory.createRule(rule, contextData));
    }
    return fusRules.toArray(FUSRule.EMPTY_ARRAY);
  }

  private static @NotNull Comparator<FUSRule> getRulesComparator() {
    // todo: do it better )))
    return (o1, o2) -> {
      if (o1 instanceof EnumValidationRule) return o2 instanceof EnumValidationRule ? -1 : 0;
      return o2 instanceof EnumValidationRule ? 0 : 1;
    };
  }

  public boolean areEventIdRulesDefined() {
    return eventIdRules.length > 0;
  }

  public boolean areEventDataRulesDefined() {
    return eventDataRules.size() > 0;
  }

  public ValidationResultType validateEventId(@NotNull EventContext context) {
    ValidationResultType prevResult = null;
    for (FUSRule rule : eventIdRules) {
      ValidationResultType resultType = rule.validate(context.eventId, context);
      if (resultType.isFinal()) return resultType;
      prevResult = resultType;
    }
    return prevResult != null ? prevResult : REJECTED;
  }

  /**
   * @return validated data, incorrect values are replaced with ValidationResultType#description
   */
  public Object validateEventData(@NotNull String key,
                                  @Nullable Object data,
                                  @NotNull EventContext context) {
    if (FeatureUsageData.Companion.getPlatformDataKeys().contains(key)) return data;

    if (data == null) return REJECTED.getDescription();

    if (data instanceof Map<?, ?>) {
      HashMap<Object, Object> validatedData = new HashMap<>();
      for (Map.Entry<?, ?> entry : ((Map<?, ?>)data).entrySet()) {
        Object entryKey = entry.getKey();
        if (entryKey instanceof String) {
          validatedData.put(entryKey, validateEventData(key + "." + entryKey, entry.getValue(), context));
        }
        else {
          validatedData.put(entryKey, REJECTED.getDescription());
        }
      }
      return validatedData;
    }

    if (data instanceof List<?>) {
      return ContainerUtil.map(((List<?>)data), value -> validateEventData(key, value, context));
    }

    FUSRule[] rules = eventDataRules.get(key);
    if (rules == null || rules.length == 0) return UNDEFINED_RULE.getDescription();
    return validateValue(data, context, rules);
  }

  private static Object validateValue(@NotNull Object data, @NotNull EventContext context, FUSRule @NotNull [] rules) {
    ValidationResultType resultType = acceptRule(data.toString(), context, rules);
    return resultType == ACCEPTED ? data : resultType.getDescription();
  }

  private static ValidationResultType acceptRule(@NotNull String ruleData, @NotNull EventContext context, FUSRule @Nullable ... rules) {
    if (rules == null) return UNDEFINED_RULE;

    ValidationResultType prevResult = null;
    for (FUSRule rule : rules) {
      ValidationResultType resultType = rule.validate(ruleData, context);
      if (resultType.isFinal()) return resultType;
      prevResult = resultType;
    }
    return prevResult != null ? prevResult : REJECTED;
  }

  public static @NotNull EventGroupRules create(@NotNull FUStatisticsWhiteListGroupsService.WLGroup group,
                                                @Nullable Map<String, Set<String>> globalEnums,
                                                @Nullable Map<String, String> globalRegexps) {
    FUStatisticsWhiteListGroupsService.WLRule rules = group.rules;
    return rules == null
           ? EMPTY
           : new EventGroupRules(rules.event_id, rules.event_data,
                                 EventGroupContextData.create(rules.enums, globalEnums, rules.regexps, globalRegexps));
  }
}

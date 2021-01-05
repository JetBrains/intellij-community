// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator.rules.beans;

import com.intellij.internal.statistic.eventLog.connection.metadata.EventGroupRemoteDescriptors.EventGroupRemoteDescriptor;
import com.intellij.internal.statistic.eventLog.connection.metadata.EventGroupRemoteDescriptors.GroupRemoteRule;
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType;
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext;
import com.intellij.internal.statistic.eventLog.validator.rules.FUSRule;
import com.intellij.internal.statistic.eventLog.validator.rules.utils.ValidationSimpleRuleFactory;
import com.intellij.internal.statistic.eventLog.validator.storage.GlobalRulesHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static com.intellij.internal.statistic.eventLog.validator.ValidationResultType.*;
import static com.intellij.internal.statistic.eventLog.validator.rules.utils.ValidationSimpleRuleFactory.REJECTING_UTIL_URL_PRODUCER;

public final class EventGroupRules {
  public static final EventGroupRules EMPTY =
    new EventGroupRules(Collections.emptySet(), Collections.emptyMap(), EventGroupContextData.EMPTY,
                        new ValidationSimpleRuleFactory(REJECTING_UTIL_URL_PRODUCER));

  private final FUSRule[] eventIdRules;
  private final Map<String, FUSRule[]> eventDataRules = new ConcurrentHashMap<>();

  private EventGroupRules(@Nullable Set<String> eventIdRules,
                          @Nullable Map<String, Set<String>> eventDataRules,
                          @NotNull EventGroupContextData contextData,
                          @NotNull ValidationSimpleRuleFactory factory) {
    this.eventIdRules = factory.getRules(null, eventIdRules, contextData);

    if (eventDataRules != null) {
      for (Map.Entry<String, Set<String>> entry : eventDataRules.entrySet()) {
        this.eventDataRules.put(entry.getKey(), factory.getRules(entry.getKey(), entry.getValue(), contextData));
      }
    }
  }

  public FUSRule[] getEventIdRules() {
    return eventIdRules;
  }

  public Map<String, FUSRule[]> getEventDataRules() {
    return eventDataRules;
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
      return ((List<?>)data).stream().map(value -> validateEventData(key, value, context)).collect(Collectors.toList());
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

  public static @NotNull EventGroupRules create(@NotNull EventGroupRemoteDescriptor group,
                                                @NotNull GlobalRulesHolder globalRulesHolder,
                                                @NotNull ValidationSimpleRuleFactory factory) {
    GroupRemoteRule rules = group.rules;
    return rules == null
           ? EMPTY
           : new EventGroupRules(rules.event_id, rules.event_data,
                                 new EventGroupContextData(rules.enums, rules.regexps, globalRulesHolder), factory);
  }
}

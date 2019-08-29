// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator.rules.beans;

import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext;
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType;
import com.intellij.internal.statistic.eventLog.validator.rules.FUSRule;
import com.intellij.internal.statistic.eventLog.validator.rules.impl.EnumWhiteListRule;
import com.intellij.internal.statistic.eventLog.validator.rules.utils.WhiteListSimpleRuleFactory;
import com.intellij.internal.statistic.service.fus.FUStatisticsWhiteListGroupsService;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.SortedList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.internal.statistic.eventLog.validator.ValidationResultType.*;

public class WhiteListGroupRules {
  public static final WhiteListGroupRules EMPTY =
    new WhiteListGroupRules(Collections.emptySet(), Collections.emptyMap(), WhiteListGroupContextData.EMPTY);

  private final FUSRule[] eventIdRules;
  private final Map<String, FUSRule[]> eventDataRules = ContainerUtil.newConcurrentMap();

  private WhiteListGroupRules(@Nullable Set<String> eventIdRules,
                              @Nullable Map<String, Set<String>> eventDataRules, @NotNull WhiteListGroupContextData contextData) {
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

  @NotNull
  private static FUSRule[] getRules(@Nullable Set<String> rules,
                                    @NotNull WhiteListGroupContextData contextData) {

    if (rules == null) return FUSRule.EMPTY_ARRAY;
    List<FUSRule> fusRules = new SortedList<>(getRulesComparator());
    for (String rule : rules) {
      ContainerUtil.addIfNotNull(fusRules, WhiteListSimpleRuleFactory.createRule(rule, contextData));
    }
    return fusRules.toArray(FUSRule.EMPTY_ARRAY);
  }

  @NotNull
  private static Comparator<FUSRule> getRulesComparator() {
    // todo: do it better )))
    return (o1, o2) -> {
      if (o1 instanceof EnumWhiteListRule) return o2 instanceof EnumWhiteListRule ? -1 : 0;
      return o2 instanceof EnumWhiteListRule ? 0 : 1;
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

  public ValidationResultType validateEventData(@NotNull String key,
                                                @Nullable Object data,
                                                @NotNull EventContext context) {
    if (FeatureUsageData.Companion.getPlatformDataKeys().contains(key)) return ACCEPTED;

    FUSRule[] rules = eventDataRules.get(key);

    if (rules == null || rules.length == 0) return UNDEFINED_RULE;

    if (data == null) {
      return REJECTED;
    }
    else if (data instanceof List<?>) {
      for (Object dataPart : (List<?>) data) {
        ValidationResultType resultType = acceptRule(dataPart.toString(), context, rules);
        if (resultType != ACCEPTED) {
          return resultType;
        }
      }
      return ACCEPTED;
    }
    else {
      return acceptRule(data.toString(), context, rules);
    }
  }

  private static ValidationResultType acceptRule(@NotNull String ruleData, @NotNull EventContext context, @Nullable FUSRule... rules) {
    if (rules == null) return UNDEFINED_RULE;

    ValidationResultType prevResult = null;
    for (FUSRule rule : rules) {
      ValidationResultType resultType = rule.validate(ruleData, context);
      if (resultType.isFinal()) return resultType;
      prevResult = resultType;
    }
    return prevResult != null ? prevResult : REJECTED;
  }

  @NotNull
  public static WhiteListGroupRules create(@NotNull FUStatisticsWhiteListGroupsService.WLGroup group,
                                           @Nullable Map<String, Set<String>> globalEnums,
                                           @Nullable Map<String, String> globalRegexps) {
    FUStatisticsWhiteListGroupsService.WLRule rules = group.rules;
    return rules == null
           ? EMPTY
           : new WhiteListGroupRules(rules.event_id, rules.event_data,
                                     WhiteListGroupContextData.create(rules.enums, globalEnums, rules.regexps, globalRegexps));
  }
}

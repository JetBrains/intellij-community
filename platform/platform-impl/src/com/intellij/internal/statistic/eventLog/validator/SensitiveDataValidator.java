// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator;

import com.intellij.internal.statistic.eventLog.EventLogConfiguration;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.validator.persistence.FUSWhiteListPersistence;
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext;
import com.intellij.internal.statistic.eventLog.validator.rules.beans.WhiteListGroupRules;
import com.intellij.internal.statistic.service.fus.FUStatisticsWhiteListGroupsService;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.stream.Collectors;

import static com.intellij.internal.statistic.eventLog.validator.ValidationResultType.ACCEPTED;

public class SensitiveDataValidator {
  private static NotNullLazyValue<SensitiveDataValidator> me = NotNullLazyValue.createValue(() -> new SensitiveDataValidator());

  public static SensitiveDataValidator getInstance() {
    return me.getValue();
  }

  protected SensitiveDataValidator() {}

  protected final Map<String, WhiteListGroupRules> eventsValidators = ContainerUtil.createConcurrentSoftMap();

  public String guaranteeCorrectEventId(@NotNull EventLogGroup group,
                                        @NotNull EventContext context) {
    ValidationResultType validationResultType = validateEvent(group, context);
    return validationResultType == ACCEPTED ? context.eventId : validationResultType.getDescription();
  }

  public Map<String, Object> guaranteeCorrectEventData(@NotNull EventLogGroup group, @NotNull EventContext context) {
    WhiteListGroupRules whiteListRule = eventsValidators.get(group.getId());
    if (whiteListRule == null || !whiteListRule.areEventDataRulesDefined()) return context.eventData;  // there are no rules to validate

    Map<String, Object> validatedData = ContainerUtil.newConcurrentMap(); // TODO: don't create validatedData map if all keys are accepted (just return context.eventData)
    for (Map.Entry<String, Object> entry : context.eventData.entrySet()) {
      String key = entry.getKey();
      Object entryValue = entry.getValue();
      ValidationResultType resultType = whiteListRule.validateEventData(key, entryValue, context);
      validatedData.put(key, resultType == ACCEPTED ? entryValue : resultType.getDescription());
    }
    return validatedData;
  }

  public SensitiveDataValidator update() {
    String whiteListContent = getWhiteListContent();
    if (whiteListContent != null) {
      FUStatisticsWhiteListGroupsService.WLGroups groups = FUStatisticsWhiteListGroupsService.parseWhiteListContent(whiteListContent);
      if (groups != null) {
        final BuildNumber buildNumber = BuildNumber.fromString(EventLogConfiguration.INSTANCE.getBuild());
        final Map<String, WhiteListGroupRules> result = groups.groups.stream().
          filter(group -> group.accepts(buildNumber)).
          collect(Collectors.toMap(group -> group.id, group -> createRules(group, groups.rules)));
        eventsValidators.clear();
        eventsValidators.putAll(result);
      }
    }
    return this;
  }

  public ValidationResultType validateEvent(@NotNull EventLogGroup group, @NotNull EventContext context) {
    WhiteListGroupRules whiteListRule = eventsValidators.get(group.getId());
    if (whiteListRule == null || !whiteListRule.areEventIdRulesDefined()) {
      return ACCEPTED; // there are no rules (eventId and eventData) to validate
    }

    return whiteListRule.validateEventId(context);
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
}

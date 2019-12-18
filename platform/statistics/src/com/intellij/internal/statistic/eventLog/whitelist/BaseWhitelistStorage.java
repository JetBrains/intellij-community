// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.whitelist;

import com.intellij.internal.statistic.eventLog.EventLogConfiguration;
import com.intellij.internal.statistic.eventLog.validator.rules.beans.WhiteListGroupRules;
import com.intellij.internal.statistic.service.fus.FUStatisticsWhiteListGroupsService;
import com.intellij.openapi.util.BuildNumber;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public abstract class BaseWhitelistStorage implements WhitelistGroupRulesStorage {
  @NotNull
  protected final AtomicBoolean isWhiteListInitialized;

  protected BaseWhitelistStorage() {
    isWhiteListInitialized = new AtomicBoolean(false);
  }

  @Override
  public boolean isUnreachableWhitelist() {
    return !isWhiteListInitialized.get();
  }

  @NotNull
  protected Map<String, WhiteListGroupRules> createValidators(@NotNull FUStatisticsWhiteListGroupsService.WLGroups groups) {
    final BuildNumber buildNumber = BuildNumber.fromString(EventLogConfiguration.INSTANCE.getBuild());
    return groups.groups.stream().
      filter(group -> group.accepts(buildNumber)).
      collect(Collectors.toMap(group -> group.id, group -> createRules(group, groups.rules)));
  }

  @NotNull
  protected static WhiteListGroupRules createRules(@NotNull FUStatisticsWhiteListGroupsService.WLGroup group,
                                                   @Nullable FUStatisticsWhiteListGroupsService.WLRule globalRules) {
    return globalRules != null
           ? WhiteListGroupRules.create(group, globalRules.enums, globalRules.regexps)
           : WhiteListGroupRules.create(group, null, null);
  }
}

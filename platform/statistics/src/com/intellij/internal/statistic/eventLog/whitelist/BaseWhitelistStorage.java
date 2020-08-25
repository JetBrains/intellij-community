// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.whitelist;

import com.intellij.internal.statistic.eventLog.EventLogBuild;
import com.intellij.internal.statistic.eventLog.validator.rules.beans.EventGroupRules;
import com.intellij.internal.statistic.service.fus.FUStatisticsWhiteListGroupsService;
import com.intellij.internal.statistic.service.fus.StatisticsWhitelistGroupConditions;
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
  protected Map<String, EventGroupRules> createValidators(@Nullable EventLogBuild build, @NotNull FUStatisticsWhiteListGroupsService.WLGroups groups) {
    GlobalRulesHolder globalRulesHolder = new GlobalRulesHolder(groups.rules);
    return groups.groups.stream().
      filter(group -> StatisticsWhitelistGroupConditions.create(group).accepts(build)).
      collect(Collectors.toMap(group -> group.id, group -> {
        return EventGroupRules.create(group, globalRulesHolder);
      }));
  }
}

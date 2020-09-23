// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator.storage;

import com.intellij.internal.statistic.eventLog.EventLogBuild;
import com.intellij.internal.statistic.eventLog.validator.rules.beans.EventGroupRules;
import com.intellij.internal.statistic.eventLog.connection.metadata.EventGroupFilterRules;
import com.intellij.internal.statistic.eventLog.connection.metadata.EventGroupRemoteDescriptors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public abstract class BaseValidationRulesPersistedStorage implements ValidationRulesStorage {
  @NotNull
  protected final AtomicBoolean isInitialized;

  protected BaseValidationRulesPersistedStorage() {
    isInitialized = new AtomicBoolean(false);
  }

  @Override
  public boolean isUnreachable() {
    return !isInitialized.get();
  }

  @NotNull
  protected Map<String, EventGroupRules> createValidators(@Nullable EventLogBuild build, @NotNull EventGroupRemoteDescriptors groups) {
    GlobalRulesHolder globalRulesHolder = new GlobalRulesHolder(groups.rules);
    return groups.groups.stream().
      filter(group -> EventGroupFilterRules.create(group).accepts(build)).
      collect(Collectors.toMap(group -> group.id, group -> {
        return EventGroupRules.create(group, globalRulesHolder);
      }));
  }
}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.validator.storage;

import com.intellij.internal.statistic.eventLog.EventLogBuild;
import com.intellij.internal.statistic.eventLog.validator.GroupValidators;
import com.intellij.internal.statistic.eventLog.validator.ValidationRuleStorage;
import com.intellij.internal.statistic.eventLog.validator.rules.beans.EventGroupRules;
import com.intellij.internal.statistic.eventLog.validator.rules.utils.ValidationSimpleRuleFactory;
import com.jetbrains.fus.reporting.model.metadata.EventGroupRemoteDescriptors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public interface IntellijValidationRulesStorage extends ValidationRuleStorage<EventLogBuild> {
  @Nullable EventGroupRules getGroupRules(@NotNull String groupId);

  /**
   * Loads and updates events scheme from the server if necessary
   */
  void update();

  /**
   * Re-loads events scheme from local caches
   */
  void reload();

  @Override
  default @NotNull GroupValidators<EventLogBuild> getGroupValidators(@NotNull String groupId) {
    return new GroupValidators<>(getGroupRules(groupId), null);
  }

  @Override
  default @NotNull Map<String, EventGroupRules> createValidators(@NotNull EventGroupRemoteDescriptors descriptors,
                                                                 @NotNull ValidationSimpleRuleFactory validationSimpleRuleFactory,
                                                                 @NotNull List<String> excludeFields) {
    // Duplication of com.intellij.internal.statistic.eventLog.validator.ValidationRuleStorage#createValidators
    // due kotlin default function in interface ↔ java Interface compatibility problems
    GlobalRulesHolder globalRulesHolder = new GlobalRulesHolder(descriptors.rules);
    final ArrayList<EventGroupRemoteDescriptors.EventGroupRemoteDescriptor> groups = descriptors.groups;
    return groups.stream().collect(Collectors.toMap(
      descriptor -> descriptor.id,
      descriptor -> EventGroupRules.create(descriptor, globalRulesHolder, validationSimpleRuleFactory, excludeFields)
    ));
  }
}

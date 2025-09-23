// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.validator.storage;

import com.intellij.internal.statistic.eventLog.EventLogBuild;
import com.intellij.internal.statistic.eventLog.validator.DictionaryStorage;
import com.intellij.internal.statistic.eventLog.validator.GroupValidators;
import com.intellij.internal.statistic.eventLog.validator.ValidationRuleStorage;
import com.intellij.internal.statistic.eventLog.validator.rules.beans.EventGroupRules;
import com.intellij.internal.statistic.eventLog.validator.rules.impl.RecorderDataValidationRule;
import com.intellij.internal.statistic.eventLog.validator.rules.utils.ValidationSimpleRuleFactory;
import com.jetbrains.fus.reporting.MetadataStorage;
import com.jetbrains.fus.reporting.model.metadata.EventGroupRemoteDescriptors;
import kotlin.NotImplementedError;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.Job;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public interface IntellijValidationRulesStorage extends ValidationRuleStorage<EventLogBuild>, MetadataStorage<EventLogBuild> {
  @Nullable EventGroupRules getGroupRules(@NotNull String groupId);

  /**
   * Loads and updates events scheme from the server if necessary
   *
   * @return true if events scheme was updated without errors, false otherwise
   */
  @Override
  boolean update();

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
      descriptor -> EventGroupRules.create(descriptor, globalRulesHolder, validationSimpleRuleFactory, excludeFields, getDictionaryStorage())
    ));
  }

  @Override
  @Nullable
  DictionaryStorage getDictionaryStorage();

  @Override
  boolean isUnreachable();

  // only required if anonymization is handled by the library
  @Override
  default @NotNull Set<String> getFieldsToAnonymize(@NotNull String groupId, @NotNull String eventId) {
    throw new NotImplementedError("getFieldsToAnonymize is not implemented for IntellijValidationRulesStorage");
  }

  // only required when scheduled updates are handled by the library
  @Override
  default @Nullable Job update(@NotNull CoroutineScope scope, @NotNull Continuation<? super Job> continuation) {
    throw new NotImplementedError("update is not implemented for IntellijValidationRulesStorage");
  }

  // only required if anonymization is handled by the library
  @Override
  default @NotNull Set<String> getSkipAnonymizationIds() {
    throw new NotImplementedError("getSkipAnonymizationIds is not implemented for IntellijValidationRulesStorage");
  }

  // only required if v4 validation format is used (not the case for IntelliJ)
  @Override
  @NotNull
  default RecorderDataValidationRule getClientDataRulesRevisions() {
    throw new NotImplementedError("getClientDataRulesRevisions is not implemented for IntellijValidationRulesStorage");
  }

  // only required if v4 validation format is used (not the case for IntelliJ)
  @Override
  @NotNull
  default RecorderDataValidationRule getSystemDataRulesRevisions() {
    throw new NotImplementedError("getSystemDataRulesRevisions is not implemented for IntellijValidationRulesStorage");
  }

  // only required if v4 validation format is used (not the case for IntelliJ)
  @Override
  @NotNull
  default RecorderDataValidationRule getIdsRulesRevisions() {
    throw new NotImplementedError("getIdsRulesRevisions is not implemented for IntellijValidationRulesStorage");
  }
}

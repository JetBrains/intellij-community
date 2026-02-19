// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.validator.storage

import com.intellij.internal.statistic.eventLog.EventLogBuild
import com.intellij.internal.statistic.eventLog.validator.IEventGroupRules
import com.intellij.internal.statistic.eventLog.validator.IEventGroupsFilterRules
import com.intellij.internal.statistic.eventLog.validator.IGroupValidators
import com.intellij.internal.statistic.eventLog.validator.rules.impl.RecorderDataValidationRule
import com.jetbrains.fus.reporting.MetadataStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.jetbrains.annotations.ApiStatus

/**
 * This class is for internal purposes. It uses [ValidationTestRulesPersistedStorage] to execute test rules
 * which have been added using the statistics event log tool window.
 */
@ApiStatus.Internal
class CompositeValidationRulesStorage internal constructor(
  private val metadataStorage: MetadataStorage<EventLogBuild>,
  private val testRulesStorage: ValidationTestRulesPersistedStorage
) : MetadataStorage<EventLogBuild>, ValidationTestRulesStorageHolder {

  private class TestGroupValidators(
    private val testGroupRules: IEventGroupRules
  ) : IGroupValidators<EventLogBuild> {
    override val eventGroupRules: IEventGroupRules
      get() = testGroupRules
    override val versionFilter: IEventGroupsFilterRules<EventLogBuild>?
      get() = null
  }

  private class EmptyGroupValidators : IGroupValidators<EventLogBuild> {
    override val eventGroupRules: IEventGroupRules? = null
    override val versionFilter: IEventGroupsFilterRules<EventLogBuild>? = null
  }

  override fun getGroupValidators(groupId: String): IGroupValidators<EventLogBuild> {
    val testGroupRules = testRulesStorage.getGroupRules(groupId)
    if (testGroupRules != null) {
      return TestGroupValidators(testGroupRules)
    }

    // if custom metadata is used in statistics tool window, we don't go to regular metadata storage
    if (testRulesStorage.hasCustomPathMetadata()) {
      return EmptyGroupValidators()
    }

    return metadataStorage.getGroupValidators(groupId)
  }

  override fun isUnreachable(): Boolean {
    return metadataStorage.isUnreachable() && testRulesStorage.isUnreachable()
  }

  override fun update(): Boolean {
    return metadataStorage.update() && testRulesStorage.update()
  }

  override suspend fun update(scope: CoroutineScope): Job = metadataStorage.update(scope)

  override fun reload() {
    metadataStorage.reload()
    testRulesStorage.reload()
  }

  override fun getTestGroupStorage(): ValidationTestRulesPersistedStorage = testRulesStorage

  override fun getClientDataRulesRevisions(): RecorderDataValidationRule = throw NotImplementedError()
  override fun getFieldsToAnonymize(groupId: String, eventId: String): Set<String> = throw NotImplementedError()
  override fun getIdsRulesRevisions(): RecorderDataValidationRule = throw NotImplementedError()
  override fun getSkipAnonymizationIds(): Set<String> = throw NotImplementedError()
  override fun getSystemDataRulesRevisions(): RecorderDataValidationRule = throw NotImplementedError()
}

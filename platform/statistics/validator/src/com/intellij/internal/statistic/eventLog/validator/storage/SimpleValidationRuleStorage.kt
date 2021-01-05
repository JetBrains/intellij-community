// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator.storage

import com.intellij.internal.statistic.eventLog.connection.metadata.EventGroupRemoteDescriptors
import com.intellij.internal.statistic.eventLog.validator.rules.beans.EventGroupRules
import com.intellij.internal.statistic.eventLog.validator.rules.utils.UtilRuleProducer
import com.intellij.internal.statistic.eventLog.validator.rules.utils.ValidationSimpleRuleFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

class SimpleValidationRuleStorage(val initialMetadataContent: String,
                                  val utilRulesProducer: UtilRuleProducer = ValidationSimpleRuleFactory.REJECTING_UTIL_URL_PRODUCER)
  : ValidationRulesStorage {
  private val validationRuleFactory = ValidationSimpleRuleFactory(utilRulesProducer)
  private val eventsValidators: ConcurrentMap<String, EventGroupRules> = ConcurrentHashMap()
  private val lock = Any()

  init {
    updateEventGroupRules(initialMetadataContent)
  }

  fun update(metadataContent: String) {
    updateEventGroupRules(metadataContent)
  }

  private fun updateEventGroupRules(metadataContent: String?) {
    synchronized(lock) {
      eventsValidators.clear()
      eventsValidators.putAll(createValidators(metadataContent))
    }
  }

  private fun createValidators(content: String?): Map<String?, EventGroupRules> {
    val descriptors = EventGroupRemoteDescriptors.create(content)
    val globalRulesHolder = GlobalRulesHolder(descriptors.rules)
    val groups = descriptors.groups
    return groups.associate { it.id to EventGroupRules.create(it, globalRulesHolder, validationRuleFactory) }
  }

  override fun getGroupRules(groupId: String): EventGroupRules? {
    synchronized(lock) {
      return eventsValidators[groupId]
    }
  }
}
// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator.storage

import com.intellij.internal.statistic.eventLog.connection.metadata.EventGroupRemoteDescriptors
import com.intellij.internal.statistic.eventLog.validator.rules.beans.EventGroupRules
import com.intellij.internal.statistic.eventLog.validator.rules.utils.ValidationSimpleRuleFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.AtomicBoolean

abstract class BaseValidationRuleStorage(val initialMetadataContent: String?) : ValidationRulesStorage {
  private val eventsValidators: ConcurrentMap<String, EventGroupRules> = ConcurrentHashMap()
  private val isInitialized: AtomicBoolean = AtomicBoolean(false)
  private val lock = Any()

  init {
    val metadata = initialMetadataContent ?: loadMetadataContent()
    updateEventGroupRules(metadata)
  }

  override fun update() {
    updateEventGroupRules(loadMetadataContent())
  }

  private fun updateEventGroupRules(metadataContent: String?) {
    synchronized(lock) {
      isInitialized.set(false)
      eventsValidators.clear()
      eventsValidators.putAll(createValidators(metadataContent))
      isInitialized.set(true)
    }
  }

  private fun createValidators(content: String?): Map<String?, EventGroupRules> {
    val descriptors = EventGroupRemoteDescriptors.create(content)
    val globalRulesHolder = GlobalRulesHolder(descriptors.rules)
    val groups = descriptors.groups
    return groups.associate { it.id to EventGroupRules.create(it, globalRulesHolder, ValidationSimpleRuleFactory()) }
  }

  override fun reload() = update()

  override fun isUnreachable(): Boolean = !isInitialized.get()

  override fun getGroupRules(groupId: String): EventGroupRules? = eventsValidators[groupId]

  abstract fun loadMetadataContent(): String
}
// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator.storage

import com.intellij.internal.statistic.eventLog.validator.rules.beans.EventGroupRules

interface ValidationTestRulesStorageHolder {
  fun getTestGroupStorage() : ValidationTestRulesPersistedStorage
}

/**
 * Thread unsafe
 */
object ValidationRulesInMemoryStorage : ValidationRulesStorage {
  val eventsValidators = HashMap<String, EventGroupRules>()

  override fun getGroupRules(groupId: String): EventGroupRules? = eventsValidators[groupId]
  override fun isUnreachable(): Boolean = false
  override fun update() = Unit
  override fun reload() = Unit
}
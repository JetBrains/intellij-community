// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator.storage

import com.intellij.internal.statistic.eventLog.validator.rules.beans.EventGroupRules

/**
 * Thread unsafe
 */
object ValidationRulesInMemoryStorage : IntellijValidationRulesStorage {
  val eventsValidators = HashMap<String, EventGroupRules>()

  override fun getGroupRules(groupId: String): EventGroupRules? = eventsValidators[groupId]
  override fun isUnreachable(): Boolean = false
  override fun update() = Unit
  override fun reload() = Unit
}
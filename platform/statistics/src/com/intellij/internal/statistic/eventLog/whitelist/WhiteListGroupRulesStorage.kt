// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.whitelist

import com.intellij.internal.statistic.eventLog.validator.rules.beans.EventGroupRules

interface WhitelistGroupRulesStorage {
  fun getGroupRules(groupId: String): EventGroupRules?

  fun isUnreachableWhitelist(): Boolean

  /**
   * Loads and updates whitelist from the server if necessary
   */
  fun update()

  /**
   * Re-loads whitelist from local caches
   */
  fun reload()
}

interface WhitelistTestRulesStorageHolder {
  fun getTestGroupStorage() : WhitelistTestGroupStorage
}

/**
 * Thread unsafe
 */
object InMemoryWhitelistStorage : WhitelistGroupRulesStorage {
  val eventsValidators = HashMap<String, EventGroupRules>()

  override fun getGroupRules(groupId: String): EventGroupRules? = eventsValidators[groupId]
  override fun isUnreachableWhitelist(): Boolean = false
  override fun update() = Unit
  override fun reload() = Unit
}
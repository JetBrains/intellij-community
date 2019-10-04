// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.whitelist

import com.intellij.internal.statistic.eventLog.validator.rules.beans.WhiteListGroupRules

interface WhitelistGroupRulesStorage {
  fun getGroupRules(groupId: String): WhiteListGroupRules?

  fun isUnreachableWhitelist(): Boolean

  fun update()
}

/**
 * Thread unsafe
 */
object InMemoryWhitelistStorage : WhitelistGroupRulesStorage {
  val eventsValidators = HashMap<String, WhiteListGroupRules>()

  override fun getGroupRules(groupId: String): WhiteListGroupRules? {
    return eventsValidators[groupId]
  }

  override fun isUnreachableWhitelist(): Boolean {
    return false
  }

  override fun update() = Unit
}
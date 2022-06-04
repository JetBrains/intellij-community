// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages.impl

import com.intellij.usages.impl.rules.ReadAccessFilteringRule
import com.intellij.usages.impl.rules.WriteAccessFilteringRule

internal class UsageFilteringRuleStateRWAware(
  private val delegate: UsageFilteringRuleState,
) : UsageFilteringRuleState {

  private val rRuleId = ReadAccessFilteringRule.ruleId
  private val wRuleId = WriteAccessFilteringRule.ruleId

  override fun isActive(ruleId: String): Boolean = delegate.isActive(ruleId)

  override fun setActive(ruleId: String, active: Boolean) {
    delegate.setActive(ruleId, active)
    if (active) {
      val otherRuleId = when (ruleId) {
        rRuleId -> wRuleId
        wRuleId -> rRuleId
        else -> return
      }
      if (isActive(otherRuleId)) {
        delegate.setActive(otherRuleId, false) // deactivate another rule, both cannot be active at the same time
      }
    }
  }
}

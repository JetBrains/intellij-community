// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages.impl

import com.intellij.util.containers.ContainerUtil

internal class UsageFilteringRuleStateImpl(
  private val sharedState: MutableSet<String>
) : UsageFilteringRuleState {

  private val localState: MutableSet<String> = ContainerUtil.newConcurrentSet()

  init {
    localState.addAll(sharedState)
  }

  /**
   * @return whether [ruleId] is active locally, e.g. in the current usage view
   */
  override fun isActive(ruleId: String): Boolean = localState.contains(ruleId)

  /**
   * Sets [ruleId] state to [active] propagating it to [sharedState].
   */
  override fun setActive(ruleId: String, active: Boolean) {
    if (active) {
      localState.add(ruleId)
      sharedState.add(ruleId)
    }
    else {
      localState.remove(ruleId)
      sharedState.remove(ruleId)
    }
  }
}

// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages.impl

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.usages.rules.UsageFilteringRuleCustomizer

internal class UsageFilteringRuleStateImpl(
  private val sharedState: MutableSet<String>
) : UsageFilteringRuleState {

  private val localState: MutableSet<String> = ConcurrentCollectionFactory.createConcurrentSet()

  init {
    localState.addAll(sharedState.filter { shouldShareState(it) })
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
      if (shouldShareState(ruleId)) {
        sharedState.add(ruleId)
      }
    }
    else {
      localState.remove(ruleId)
      if (shouldShareState(ruleId)) {
        sharedState.remove(ruleId)
      }
    }
  }

  private fun shouldShareState(ruleId: String): Boolean {
    // For not it is used in Rider to disable persisting `Show Read Usages / Show Write Usages` between different sessions
    //  because we consider this behavior misleading - users ofter lose some usages because of "forgotten" access filters
    return UsageFilteringRuleCustomizer.EP.extensionList.none { it.transientRule(ruleId) }
  }
}

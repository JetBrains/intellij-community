// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages.impl.rules

import com.intellij.usages.Usage
import com.intellij.usages.UsageTarget
import com.intellij.usages.rules.ImportFilteringRule
import com.intellij.usages.rules.UsageFilteringRule
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.NonNls


@Internal
object ImportUsageFilteringRule : UsageFilteringRule {

  const val ACTION_ID: String = "UsageFiltering.Imports"
  val RULE_ID: String = ImportUsageFilteringRule::class.java.name

  override fun getRuleId(): @NonNls String = RULE_ID

  override fun getActionId(): String = ACTION_ID

  override fun isVisible(usage: Usage, targets: Array<out UsageTarget>): Boolean {
    for (rule in ImportFilteringRule.EP_NAME.extensionList) {
      if (!rule.isVisible(usage, targets)) {
        return false
      }
    }
    return true
  }
}

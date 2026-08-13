// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages.impl.rules

import com.intellij.openapi.project.Project
import com.intellij.usages.Usage
import com.intellij.usages.UsageTarget
import com.intellij.usages.rules.GeneratedSourceUsageFilter
import com.intellij.usages.rules.UsageFilteringRule
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

@ApiStatus.Internal
class UsageInGeneratedCodeFilteringRule(private val project: Project) : UsageFilteringRule {
  companion object {
    const val ACTION_ID: String = "UsageFiltering.GeneratedCode"
    val RULE_ID: String = UsageInGeneratedCodeFilteringRule::class.java.name
  }

  override fun getRuleId(): @NonNls String = RULE_ID

  override fun getActionId(): String = ACTION_ID

  override fun isVisible(usage: Usage, targets: Array<out UsageTarget>): Boolean {
    for (filter in GeneratedSourceUsageFilter.EP_NAME.extensions) {
      if (filter.isGeneratedSource(usage, project)) {
        return false
      }
    }
    return true
  }
}

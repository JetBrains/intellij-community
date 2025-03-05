// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.apiDump.lang.usages

import com.intellij.devkit.apiDump.lang.psi.ADFile
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.usages.Usage
import com.intellij.usages.rules.PsiElementUsage
import com.intellij.usages.rules.UsageFilteringRule
import com.intellij.usages.rules.UsageFilteringRuleProvider
import org.jetbrains.annotations.NonNls

internal class ADUsageFilteringRuleProvider : UsageFilteringRuleProvider {
  override fun getApplicableRules(project: Project): Collection<UsageFilteringRule> {
    if (!Registry.`is`("intellij.devkit.api.dump.find.usages")) {
      return emptyList()
    }
    return listOf(ADUsageFilterRule())
  }
}

private class ADUsageFilterRule : UsageFilteringRule {
  override fun isVisible(usage: Usage): Boolean =
    !(usage is PsiElementUsage && usage.element?.containingFile is ADFile)

  override fun getActionId(): String =
    adFilteringActionId

  override fun getRuleId(): @NonNls String =
    adFilteringRuleId
}

private const val adFilteringActionId = "UsageFiltering.ApiDump"
private const val adFilteringRuleId = "intellij.devkit.api.dump.filtering.rule.id"

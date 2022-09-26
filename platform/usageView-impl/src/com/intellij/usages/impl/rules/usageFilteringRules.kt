// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("UsageFilteringRules")

package com.intellij.usages.impl.rules

import com.intellij.openapi.project.Project
import com.intellij.usages.rules.GeneratedSourceUsageFilter
import com.intellij.usages.rules.ImportFilteringRule
import com.intellij.usages.rules.UsageFilteringRule
import com.intellij.usages.rules.UsageFilteringRuleProvider
import org.jetbrains.annotations.ApiStatus

internal fun usageFilteringRules(project: Project): List<UsageFilteringRule> {
  val result = ArrayList(platformUsageFilteringRules(project))
  fromExtensions(project, result)
  return java.util.List.copyOf(result)
}

@ApiStatus.Internal
fun platformUsageFilteringRules(project: Project): List<UsageFilteringRule> {
  val result = ArrayList<UsageFilteringRule>()
  result.add(ReadAccessFilteringRule)
  result.add(WriteAccessFilteringRule)
  if (areGeneratedSourceUsageFiltersAvailable()) {
    result.add(UsageInGeneratedCodeFilteringRule(project))
  }
  if (ImportFilteringRule.EP_NAME.hasAnyExtensions()) {
    result.add(ImportUsageFilteringRule)
  }
  return result
}

private fun areGeneratedSourceUsageFiltersAvailable(): Boolean {
  return GeneratedSourceUsageFilter.EP_NAME.extensionList.any(GeneratedSourceUsageFilter::isAvailable)
}

private fun fromExtensions(project: Project, result: MutableList<UsageFilteringRule>) {
  for (provider in UsageFilteringRuleProvider.EP_NAME.extensionList) {
    result.addAll(provider.getApplicableRules(project))
  }
}

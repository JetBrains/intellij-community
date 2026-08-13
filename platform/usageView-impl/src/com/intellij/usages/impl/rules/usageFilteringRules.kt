// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("UsageFilteringRules")

package com.intellij.usages.impl.rules

import com.intellij.idea.AppMode
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.usages.rules.GeneratedSourceUsageFilter
import com.intellij.usages.rules.ImportFilteringRule
import com.intellij.usages.rules.UsageFilteringRule
import com.intellij.usages.rules.UsageFilteringRuleProvider
import org.jetbrains.annotations.ApiStatus

internal fun usageFilteringRules(project: Project): List<UsageFilteringRule> {
  val result = mutableListOf<UsageFilteringRule>()
  for (provider in EP_NAME.extensionList) {
    result += provider.getUsageFilteringRules(project)
  }
  return result
}

private val EP_NAME = ExtensionPointName.create<PlatformUsageFilteringRuleProvider>("com.intellij.platformUsageFilteringRuleProvider")

/**
 * Platform-only equivalent of [UsageFilteringRuleProvider].
 * 
 * It's separate from the public provider because the platform has special remdev handling.
 */
@ApiStatus.Internal
interface PlatformUsageFilteringRuleProvider {
  fun getUsageFilteringRules(project: Project): List<UsageFilteringRule>
}

internal class DefaultPlatformUsageFilteringRuleProvider : PlatformUsageFilteringRuleProvider {
  override fun getUsageFilteringRules(project: Project): List<UsageFilteringRule> {
    if (!AppMode.isMonolith()) return emptyList() // replaced by FrontendPlatformUsageFilteringRuleProvider
    val result = ArrayList(platformUsageFilteringRules(project))
    fromExtensions(project, result)
    return java.util.List.copyOf(result)
  }
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
  result.add(CommentUsageFilteringRule)
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

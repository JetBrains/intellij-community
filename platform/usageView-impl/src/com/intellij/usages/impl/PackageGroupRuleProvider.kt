// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages.impl

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.ExtensionPointName.Companion.create
import com.intellij.openapi.project.Project
import com.intellij.usages.UsageViewSettings
import com.intellij.usages.rules.UsageGroupingRule
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
interface PackageGroupRuleProvider {
  fun getUsageGroupingRule(project: Project, isDetachedMode: Boolean): UsageGroupingRule?
  fun getUsageGroupingRule(project: Project,
                           usageViewSettings: UsageViewSettings,
                           isDetachedMode: Boolean): UsageGroupingRule? {
    return getUsageGroupingRule(project, isDetachedMode)
  }

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<PackageGroupRuleProvider> = create("com.intellij.packageGroupRuleProvider")
  }
}

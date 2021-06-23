// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("UsageFilteringRules")

package com.intellij.usages.impl.rules

import com.intellij.openapi.project.Project
import com.intellij.usages.rules.UsageFilteringRule
import com.intellij.usages.rules.UsageFilteringRuleProvider
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun usageFilteringRules(project: Project): List<UsageFilteringRule> {
  val result = ArrayList<UsageFilteringRule>()
  fromExtensions(project, result)
  return ContainerUtil.immutableCopy(result)
}

private fun fromExtensions(project: Project, result: MutableList<UsageFilteringRule>) {
  for (provider in UsageFilteringRuleProvider.EP_NAME.extensionList) {
    ContainerUtil.addAll(result, provider.getApplicableRules(project))
  }
}

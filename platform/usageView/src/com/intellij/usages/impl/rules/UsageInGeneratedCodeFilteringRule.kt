// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages.impl.rules

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.GeneratedSourcesFilter
import com.intellij.usages.Usage
import com.intellij.usages.UsageTarget
import com.intellij.usages.rules.UsageFilteringRule
import com.intellij.usages.rules.UsageInFile

internal class UsageInGeneratedCodeFilteringRule(private val project: Project) : UsageFilteringRule {

  override fun getActionId(): String = "UsageFiltering.GeneratedCode"

  override fun isVisible(usage: Usage, targets: Array<out UsageTarget>): Boolean {
    return usage !is UsageInFile || usage.file.let { file ->
      file == null || !GeneratedSourcesFilter.isGeneratedSourceByAnyFilter(file, project)
    }
  }
}

// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages.impl.rules

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.GeneratedSourcesFilter
import com.intellij.usages.Usage
import com.intellij.usages.rules.GeneratedSourceUsageFilter
import com.intellij.usages.rules.UsageInFile

class DefaultGeneratedSourceUsageFilter: GeneratedSourceUsageFilter {
  
  override fun isAvailable(): Boolean = GeneratedSourcesFilter.EP_NAME.hasAnyExtensions()

  override fun isGeneratedSource(usage: Usage, project: Project): Boolean {
    return usage is UsageInFile &&
           usage.file.let { file -> file != null && GeneratedSourcesFilter.isGeneratedSourceByAnyFilter(file, project) }
  }
}
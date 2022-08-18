package com.intellij.usages.rules

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.usages.Usage

interface GeneratedSourceUsageFilter {

  companion object {
    val EP_NAME: ExtensionPointName<GeneratedSourceUsageFilter> = ExtensionPointName.create("com.intellij.generatedSourceUsageFilter")
  }

  fun isAvailable(): Boolean

  fun isGeneratedSource(usage: Usage, project: Project): Boolean
}
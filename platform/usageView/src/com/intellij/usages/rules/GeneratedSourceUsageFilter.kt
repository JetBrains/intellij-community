package com.intellij.usages.rules

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.usages.Usage
import org.jetbrains.annotations.ApiStatus.Internal

interface GeneratedSourceUsageFilter {

  companion object {
    @Internal
    val EP_NAME: ExtensionPointName<GeneratedSourceUsageFilter> = ExtensionPointName.create("com.intellij.generatedSourceUsageFilter")
  }

  @Deprecated(replaceWith = ReplaceWith("isAvailable(Project)"), message = "Use the Project override")
  fun isAvailable(): Boolean = true

  @Suppress("DEPRECATION")
  fun isAvailable(project: Project): Boolean = isAvailable()

  fun isGeneratedSource(usage: Usage, project: Project): Boolean
}
// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.gradle

import com.intellij.execution.ConsoleFolding
import com.intellij.openapi.project.Project

internal class IdeLayoutWarningsCodeFolding : ConsoleFolding() {

  override fun shouldFoldLine(project: Project, line: String): Boolean {
    return line.contains("Layout component ") &&
           line.contains(" has some nonexistent ") &&
           line.contains(" elements:")
  }

  override fun shouldBeAttachedToThePreviousLine(): Boolean = false

  override fun getPlaceholderText(project: Project, lines: List<String>): String? {
    if (lines.isEmpty()) return null
    return DevKitGradleBundle.message("intellij.platform.gradle.console.folding.layout.component.warnings", lines.size)
  }
}

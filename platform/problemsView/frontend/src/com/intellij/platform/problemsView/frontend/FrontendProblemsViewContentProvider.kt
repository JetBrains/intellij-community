// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.problemsView.frontend

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.ui.content.Content

/**
 * Allows providing Problems View toolwindow content from a frontend module
 * by replacing the corresponding tab's content component in split mode.
 *
 * Copied from com.intellij.platform.vcs.impl.frontend.changes.FrontendChangesViewContentProvider
 */
internal interface FrontendProblemsViewContentProvider {
  fun isAvailable(project: Project): Boolean

  fun initTabContent(project: Project, content: Content)

  fun matchesTabName(tabName: String) : Boolean

  companion object {
    val EP_NAME = ExtensionPointName<FrontendProblemsViewContentProvider>("com.intellij.frontendProblemsViewContentProvider")
  }
}
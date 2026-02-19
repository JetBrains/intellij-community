// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.frontend.changes

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.ui.content.Content
import org.jetbrains.annotations.NonNls

/**
 * Must be registered with `com.intellij.changesViewContent` extension point matching its tab name.
 * Allows providing VCS toolwindow content from a frontend module
 * by replacing the corresponding tab's content component both in split in monolith modes.
 *
 * @see [com.intellij.platform.vcs.impl.frontend.toolwindow.VcsToolWindowContentReplacer]
 */
internal interface FrontendChangesViewContentProvider {
  fun isAvailable(project: Project): Boolean

  fun matchesTabName(tabName: @NonNls String): Boolean

  fun initTabContent(project: Project, content: Content)

  companion object {
    val EP_NAME = ExtensionPointName<FrontendChangesViewContentProvider>("com.intellij.frontendChangesViewContentProvider")
  }
}

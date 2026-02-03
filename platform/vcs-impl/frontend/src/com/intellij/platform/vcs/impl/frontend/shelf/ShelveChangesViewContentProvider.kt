// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.frontend.shelf

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.vcs.impl.frontend.changes.FrontendChangesViewContentProvider
import com.intellij.ui.content.Content
import org.jetbrains.annotations.NonNls

internal class ShelveChangesViewContentProvider() : FrontendChangesViewContentProvider {
  override fun matchesTabName(tabName: @NonNls String): Boolean = tabName == "Shelf"

  override fun isAvailable(project: Project): Boolean = Registry.`is`("vcs.shelves.rhizome.enabled")

  override fun initTabContent(project: Project, content: Content) {
    content.component = ShelfTreeUpdater.getInstance(project).createToolWindowPanel()
  }
}

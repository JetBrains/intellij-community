// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.frontend.changes

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangesViewId
import com.intellij.openapi.vcs.changes.ChangesViewSplitComponentBinding
import com.intellij.ui.split.SplitComponentBinding
import com.intellij.ui.split.SplitComponentProvider
import kotlinx.coroutines.CoroutineScope
import javax.swing.JComponent

internal class ChangesViewSplitComponentProvider : SplitComponentProvider<ChangesViewId> {
  override val binding: SplitComponentBinding<ChangesViewId> = ChangesViewSplitComponentBinding

  override fun createComponent(project: Project, scope: CoroutineScope, modelId: ChangesViewId): JComponent {
    val panel = FrontendCommitChangesViewWithToolbarPanel.create(project, scope)
    panel.initPanel()
    return panel
  }
}

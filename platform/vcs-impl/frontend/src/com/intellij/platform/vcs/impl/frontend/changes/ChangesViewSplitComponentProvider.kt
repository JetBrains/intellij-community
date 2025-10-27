// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.frontend.changes

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.ChangesViewId
import com.intellij.openapi.vcs.changes.ChangesViewSplitComponentBinding
import com.intellij.openapi.vcs.changes.CommitChangesViewWithToolbarPanel
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.platform.vcs.impl.shared.changes.ChangeListsViewModel
import com.intellij.ui.split.SplitComponentBinding
import com.intellij.ui.split.SplitComponentProvider
import kotlinx.coroutines.CoroutineScope
import javax.swing.JComponent

internal class ChangesViewSplitComponentProvider : SplitComponentProvider<ChangesViewId> {
  override val binding: SplitComponentBinding<ChangesViewId> = ChangesViewSplitComponentBinding

  override fun createComponent(project: Project, scope: CoroutineScope, modelId: ChangesViewId): JComponent {
    val panel = FrontendCommitChangesViewWithToolbarPanel.create(project, scope)
    panel.initPanel(object : CommitChangesViewWithToolbarPanel.ModelProvider {
      override fun getModelData(): CommitChangesViewWithToolbarPanel.ModelProvider.ModelData {
        val changeLists = ChangeListsViewModel.getInstance(project).changeLists.value
        return CommitChangesViewWithToolbarPanel.ModelProvider.ModelData(changeLists.lists,
                                                                         emptyList(),
                                                                         emptyList()) { true }
      }

      override fun synchronizeInclusion(changeLists: List<LocalChangeList>, unversionedFiles: List<FilePath>) {
      }
    })

    return panel
  }
}

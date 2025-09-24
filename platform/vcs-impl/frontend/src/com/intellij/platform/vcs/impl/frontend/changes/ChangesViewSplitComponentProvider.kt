// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.frontend.changes

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingPolicyFactory
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder
import com.intellij.platform.vcs.impl.shared.changes.ChangeListsViewModel
import com.intellij.ui.split.SplitComponentBinding
import com.intellij.ui.split.SplitComponentProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.swing.JComponent

internal class ChangesViewSplitComponentProvider : SplitComponentProvider<ChangesViewId> {
  override val binding: SplitComponentBinding<ChangesViewId> = ChangesViewSplitComponentBinding

  override fun createComponent(project: Project, scope: CoroutineScope, modelId: ChangesViewId): JComponent {
    val tree = LocalChangesListView(project)
    val panel = CommitChangesViewWithToolbarPanel(tree, scope)
    panel.id = modelId
    panel.initPanel(object : CommitChangesViewWithToolbarPanel.ModelProvider {
      override fun getModel(grouping: ChangesGroupingPolicyFactory): CommitChangesViewWithToolbarPanel.ModelProvider.ExtendedTreeModel {
        val changeLists = ChangeListsViewModel.getInstance(project).changeLists.value
        return CommitChangesViewWithToolbarPanel.ModelProvider.ExtendedTreeModel(
          changeLists.lists, emptyList(), buildModel(project, grouping, changeLists)
        )
      }

      override fun synchronizeInclusion(changeLists: List<LocalChangeList>, unversionedFiles: List<FilePath>) {
      }
    })

    scope.launch {
      ChangeListsViewModel.getInstance(project).changeLists.collect {
        panel.scheduleRefresh()
      }
    }

    return panel
  }

  private fun buildModel(
    project: Project,
    grouping: ChangesGroupingPolicyFactory,
    lists: ChangeListsViewModel.ChangeLists
  ) = TreeModelBuilder(project, grouping).setChangeLists(lists.lists, false, null).build()
}

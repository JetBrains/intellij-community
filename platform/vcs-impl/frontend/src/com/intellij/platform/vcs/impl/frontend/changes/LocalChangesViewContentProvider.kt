// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.frontend.changes

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.CommitChangesViewWithToolbarPanel
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.openapi.vcs.changes.LocalChangesListView
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingPolicyFactory
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder
import com.intellij.platform.vcs.impl.shared.RdLocalChanges
import com.intellij.platform.vcs.impl.shared.changes.ChangeListsViewModel
import com.intellij.ui.content.Content
import com.intellij.util.cancelOnDispose
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.NonNls
import javax.swing.tree.DefaultTreeModel

internal class LocalChangesViewContentProvider : FrontendChangesViewContentProvider {
  override fun matchesTabName(tabName: @NonNls String): Boolean = tabName == "Local Changes" || tabName == "Commit"

  override fun isAvailable(project: Project): Boolean = RdLocalChanges.isEnabled()

  override fun initTabContent(project: Project, content: Content) {
    val tree = LocalChangesListView(project)
    val panel = CommitChangesViewWithToolbarPanel(tree, content)
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

    content.component = panel

    project.service<ScopeProvider>().cs.launch {
      ChangeListsViewModel.getInstance(project).changeLists.collect {
        panel.scheduleRefresh()
      }
    }.cancelOnDispose(content)
  }

  private fun buildModel(project: Project, grouping: ChangesGroupingPolicyFactory, lists: ChangeListsViewModel.ChangeLists): DefaultTreeModel =
    TreeModelBuilder(project, grouping).setChangeLists(lists.lists, false, null).build()

  @Service(Service.Level.PROJECT)
  internal class ScopeProvider(val cs: CoroutineScope)
}

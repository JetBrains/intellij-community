// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.frontend.changes

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangesViewPanel
import com.intellij.openapi.vcs.changes.ChangesViewPanelActions
import com.intellij.openapi.vcs.changes.LocalChangesListView
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingPolicyFactory
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder
import com.intellij.platform.vcs.impl.shared.RdLocalChanges
import com.intellij.platform.vcs.impl.shared.changes.ChangeListsViewModel
import com.intellij.ui.content.Content
import com.intellij.util.cancelOnDispose
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.annotations.NonNls
import javax.swing.tree.DefaultTreeModel

internal class LocalChangesViewContentProvider : FrontendChangesViewContentProvider {
  override fun matchesTabName(tabName: @NonNls String): Boolean = tabName == "Local Changes" || tabName == "Commit"

  override fun isAvailable(project: Project): Boolean = RdLocalChanges.isEnabled()

  override fun initTabContent(project: Project, content: Content) {
    val tree = LocalChangesListView(project)
    val changesViewPanel = ChangesViewPanel(tree)
    changesViewPanel.isToolbarHorizontal = true

    content.component = changesViewPanel
    tree.model = buildModel(project, tree.grouping, ChangeListsViewModel.getInstance(project).changeLists.value)

    // TODO actions on thin client might still be unavailable. Should probably be replaced with FrontendActionRegistrationListener
    ChangesViewPanelActions.initActions(changesViewPanel)

    // TODO IJPL-173924 migrate ChangesViewManager
    tree.installPopupHandler(ActionManager.getInstance().getAction("ChangesViewPopupMenuShared") as ActionGroup)

    project.service<ScopeProvider>().cs.launch(Dispatchers.UiWithModelAccess) {
      ChangeListsViewModel.getInstance(project).changeLists.collect {
        tree.updateTreeModel(buildModel(project, tree.grouping, it), ChangesTree.DO_NOTHING)
      }
    }.cancelOnDispose(content)
  }

  // TODO grouping
  private fun buildModel(project: Project, grouping: ChangesGroupingPolicyFactory, lists: ChangeListsViewModel.ChangeLists): DefaultTreeModel =
    TreeModelBuilder(project, grouping).setChangeLists(
      lists.lists, true, null
    ).build()

  @Service(Service.Level.PROJECT)
  internal class ScopeProvider(val cs: CoroutineScope)
}

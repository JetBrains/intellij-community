// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.frontend.changes

import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder
import com.intellij.platform.vcs.impl.shared.changes.ChangeListsViewModel
import com.intellij.ui.content.Content
import com.intellij.util.cancelOnDispose
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.NonNls
import javax.swing.tree.DefaultTreeModel

internal class LocalChangesViewContentProvider : FrontendChangesViewContentProvider {
  override fun matchesTabName(tabName: @NonNls String): Boolean = tabName == "Local Changes" || tabName == "Commit"

  override fun isAvailable(project: Project): Boolean = Registry.`is`("vcs.rd.local.changes.enabled")

  override fun initTabContent(project: Project, content: Content) {
    val tree = SampleChangesTree(project)
    tree.rebuildTree()
    content.component = tree

    project.service<ScopeProvider>().cs.launch {
      ChangeListsViewModel.getInstance(project).changeLists.collect {
        withContext(Dispatchers.UiWithModelAccess) {
          tree.rebuildTree()
        }
      }
    }.cancelOnDispose(content)
  }

  @Service(Service.Level.PROJECT)
  internal class ScopeProvider(val cs: CoroutineScope)
}

private class SampleChangesTree(project: Project) : ChangesTree(project, false, false) {
  override fun rebuildTree() {
    val changeLists = ChangeListsViewModel.getInstance(project).changeLists.value.lists
    val newModel: DefaultTreeModel = TreeModelBuilder(project, grouping).setChangeLists(
      changeLists, true, null
    ).build()

    updateTreeModel(newModel)
  }
}
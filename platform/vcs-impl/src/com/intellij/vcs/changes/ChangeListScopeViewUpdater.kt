// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.changes

import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.scopeView.ScopeViewPane
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.*
import com.intellij.packageDependencies.DependencyValidationManager
import com.intellij.util.ui.UIUtil

class ChangeListScopeViewUpdater(private val project: Project) : ChangeListAdapter() {
  override fun changeListAdded(list: ChangeList) {
    fireListeners()
  }

  override fun changeListRemoved(list: ChangeList) {
    fireListeners()
  }

  override fun changeListRenamed(list: ChangeList, oldName: String) {
    fireListeners()
  }

  override fun changeListsChanged() {
    val pane = ProjectView.getInstance(project).getProjectViewPaneById(ScopeViewPane.ID) as? ScopeViewPane ?: return
    if (pane.selectedScope is ChangeListScope) {
      pane.updateSelectedScope()
    }
  }

  private fun fireListeners() {
    UIUtil.invokeLaterIfNeeded { DependencyValidationManager.getInstance(project).fireScopeListeners() }
  }
}
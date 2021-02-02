// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.changes

import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.scopeView.ScopeViewPane
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeList
import com.intellij.openapi.vcs.changes.ChangeListAdapter
import com.intellij.packageDependencies.DependencyValidationManager
import com.intellij.util.ui.UIUtil

class ChangeListScopeViewUpdater(private val project: Project) : ChangeListAdapter() {
  override fun changeListAdded(list: ChangeList) {
    updateAvailableScopesList(project)
  }

  override fun changeListRemoved(list: ChangeList) {
    updateAvailableScopesList(project)
  }

  override fun changeListRenamed(list: ChangeList, oldName: String) {
    updateAvailableScopesList(project)
  }

  override fun changeListAvailabilityChanged() {
    updateAvailableScopesList(project)
  }

  override fun changeListsChanged() {
    updateActiveScope(project)
  }

  companion object {
    private fun updateActiveScope(project: Project) {
      UIUtil.invokeLaterIfNeeded {
        if (project.isDisposed) return@invokeLaterIfNeeded
        val pane = ProjectView.getInstance(project).getProjectViewPaneById(ScopeViewPane.ID) as? ScopeViewPane ?: return@invokeLaterIfNeeded
        if (pane.selectedScope is ChangeListScope) {
          pane.updateSelectedScope()
        }
      }
    }

    private fun updateAvailableScopesList(project: Project) {
      UIUtil.invokeLaterIfNeeded {
        if (project.isDisposed) return@invokeLaterIfNeeded
        DependencyValidationManager.getInstance(project).fireScopeListeners()
      }
    }
  }
}
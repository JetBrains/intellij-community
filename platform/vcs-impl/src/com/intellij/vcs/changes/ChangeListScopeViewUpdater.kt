// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.changes

import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.scopeView.ScopeViewPane
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeList
import com.intellij.openapi.vcs.changes.ChangeListAdapter
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.openapi.vcs.changes.LocalChangeListsLoadedListener
import com.intellij.packageDependencies.DependencyValidationManager

class ChangeListScopeViewUpdater(private val project: Project) : ChangeListAdapter() {
  class InitialRefresh(private val project: Project) : LocalChangeListsLoadedListener {
    override fun processLoadedLists(lists: MutableList<LocalChangeList>) {
      updateAvailableScopesList(project)
    }
  }

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
      ApplicationManager.getApplication().invokeLater {
        if (project.isDisposed) return@invokeLater
        val projectView = project.serviceIfCreated<ProjectView>() ?: return@invokeLater
        val pane = projectView.getProjectViewPaneById(ScopeViewPane.ID) as? ScopeViewPane ?: return@invokeLater
        if (pane.selectedScope is ChangeListScope) {
          pane.updateSelectedScope()
        }
      }
    }

    private fun updateAvailableScopesList(project: Project) {
      ApplicationManager.getApplication().invokeLater {
        if (project.isDisposed) return@invokeLater
        DependencyValidationManager.getInstance(project).fireScopeListeners()
      }
    }
  }
}
// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.committed

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.AbstractVcsHelper
import com.intellij.openapi.vcs.CommittedChangesProvider
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList
import com.intellij.openapi.vfs.VirtualFile

internal class ProjectCommittedChangesPanel(
  project: Project,
  var provider: CommittedChangesProvider<*, *>
) : CommittedChangesPanel(project) {

  private val cache = CommittedChangesCache.getInstance(project)

  init {
    setup(null, provider.createActions(browser, null))
  }

  override fun refreshChanges() =
    cache.hasCachesForAnyRoot { hasCaches ->
      if (!hasCaches) {
        browser.emptyText.text = message("committed.changes.not.loaded.message")
        return@hasCachesForAnyRoot
      }

      val changeListsConsumer = { changeLists: List<CommittedChangeList> ->
        browser.emptyText.text = message("committed.changes.empty.message")
        browser.setItems(changeLists, CommittedChangesBrowserUseCase.COMMITTED)
      }
      val errorHandler = { errors: List<VcsException> ->
        AbstractVcsHelper.getInstance(project).showErrors(errors, message("changes.error.refreshing.vcs.history"))
      }
      cache.getProjectChangesAsync(provider.createDefaultSettings(), 0, true, changeListsConsumer, errorHandler)
    }

  fun clearCaches() =
    cache.clearCaches {
      runInEdt(ModalityState.NON_MODAL) {
        if (project.isDisposed) return@runInEdt

        browser.emptyText.text = message("committed.changes.not.loaded.message")
        browser.setItems(emptyList(), CommittedChangesBrowserUseCase.COMMITTED)
      }
    }

  fun passCachedListsToListener(notification: VcsConfigurationChangeListener.DetailedNotification, root: VirtualFile?) {
    val changeLists = mutableListOf<CommittedChangeList>()

    browser.reportLoadedLists(object : CommittedChangeListsListener {
      override fun onBeforeStartReport() = Unit

      override fun report(list: CommittedChangeList): Boolean {
        changeLists.add(list)
        return false
      }

      override fun onAfterEndReport() {
        if (changeLists.isNotEmpty()) {
          notification.execute(project, root, changeLists)
        }
      }
    })
  }
}

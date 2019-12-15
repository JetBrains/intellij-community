// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.committed

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.vcs.*
import com.intellij.openapi.vcs.ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED
import com.intellij.openapi.vcs.changes.committed.CommittedChangesCache.COMMITTED_TOPIC
import com.intellij.openapi.vcs.changes.committed.VcsConfigurationChangeListener.BRANCHES_CHANGED
import com.intellij.openapi.vcs.changes.committed.VcsConfigurationChangeListener.BRANCHES_CHANGED_RESPONSE
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentProvider
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier.showOverChangesView
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.content.Content
import com.intellij.util.NotNullFunction

class CommittedChangesViewManager(private val project: Project) : ChangesViewContentProvider {
  private var panel: CommittedChangesPanel? = null

  override fun initTabContent(content: Content) =
    createCommittedChangesPanel().let {
      panel = it
      content.component = it
      content.disposer = Disposable { panel = null }

      with(project.messageBus.connect(it)) {
        subscribe(VCS_CONFIGURATION_CHANGED, VcsListener { runInEdtIfNotDisposed { updateCommittedChangesProvider() } })
        subscribe(COMMITTED_TOPIC, MyCommittedChangesListener())
        subscribe(BRANCHES_CHANGED, VcsConfigurationChangeListener.Notification { _, vcsRoot ->
          runInEdtIfNotDisposed { panel?.notifyBranchesChanged(vcsRoot) }
        })
      }

      it.refreshChanges(true)
    }

  private fun createCommittedChangesPanel(): CommittedChangesPanel {
    val provider = CommittedChangesCache.getInstance(project).providerForProject!!
    return CommittedChangesPanel(project, provider, provider.createDefaultSettings(), null, null)
  }

  private fun updateCommittedChangesProvider() {
    val provider = CommittedChangesCache.getInstance(project).providerForProject ?: return

    panel?.run {
      setProvider(provider)
      notifyBranchesChanged(null)
    }
  }

  private fun CommittedChangesPanel.notifyBranchesChanged(vcsRoot: VirtualFile?) =
    passCachedListsToListener(project.messageBus.syncPublisher(BRANCHES_CHANGED_RESPONSE), vcsRoot)

  private fun runInEdtIfNotDisposed(block: () -> Unit) =
    runInEdt {
      if (project.isDisposed || panel == null) return@runInEdt

      block()
    }

  private inner class MyCommittedChangesListener : CommittedChangesListener {
    override fun changesLoaded(location: RepositoryLocation, changes: List<CommittedChangeList>) =
      runInEdtIfNotDisposed { panel?.refreshChanges(true) }

    override fun refreshErrorStatusChanged(lastError: VcsException?) {
      lastError?.let { showOverChangesView(project, it.message, MessageType.ERROR) }
    }
  }

  class VisibilityPredicate : NotNullFunction<Project, Boolean> {
    override fun `fun`(project: Project): Boolean =
      ProjectLevelVcsManager.getInstance(project).allActiveVcss.any { isCommittedChangesAvailable(it) }
  }

  companion object {
    fun isCommittedChangesAvailable(vcs: AbstractVcs): Boolean =
      vcs.committedChangesProvider != null && vcs.type == VcsType.centralized
  }
}
// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import java.util.function.Predicate
import java.util.function.Supplier

private fun Project.getCommittedChangesProvider(): CommittedChangesProvider<*, *>? =
  ProjectLevelVcsManager.getInstance(this).allActiveVcss
    .filter { it.committedChangesProvider != null }
    .let {
      when {
        it.isEmpty() -> null
        it.size == 1 -> it.first().committedChangesProvider!!
        else -> CompositeCommittedChangesProvider(this, it)
      }
    }

internal class CommittedChangesViewManager(private val project: Project) : ChangesViewContentProvider {
  private var panel: ProjectCommittedChangesPanel? = null

  override fun initTabContent(content: Content) {
    createCommittedChangesPanel().let {
      panel = it
      content.component = it
      content.setDisposer(Disposable { panel = null })

      val busConnection = project.messageBus.connect(it)
      busConnection.subscribe(VCS_CONFIGURATION_CHANGED, VcsListener { runInEdtIfNotDisposed { updateCommittedChangesProvider() } })
      busConnection.subscribe(COMMITTED_TOPIC, MyCommittedChangesListener())
      busConnection.subscribe(BRANCHES_CHANGED, VcsConfigurationChangeListener.Notification { _, vcsRoot ->
        runInEdtIfNotDisposed { panel?.notifyBranchesChanged(vcsRoot) }
      })

      it.refreshChanges()
    }
  }

  private fun createCommittedChangesPanel(): ProjectCommittedChangesPanel =
    ProjectCommittedChangesPanel(project, project.getCommittedChangesProvider()!!)

  private fun updateCommittedChangesProvider() {
    val projectProvider = project.getCommittedChangesProvider() ?: return

    panel?.run {
      provider = projectProvider
      notifyBranchesChanged(null)
    }
  }

  private fun ProjectCommittedChangesPanel.notifyBranchesChanged(vcsRoot: VirtualFile?) =
    passCachedListsToListener(project.messageBus.syncPublisher(BRANCHES_CHANGED_RESPONSE), vcsRoot)

  private fun runInEdtIfNotDisposed(block: () -> Unit) =
    runInEdt {
      if (project.isDisposed || panel == null) return@runInEdt

      block()
    }

  private inner class MyCommittedChangesListener : CommittedChangesListener {
    override fun changesLoaded(location: RepositoryLocation, changes: List<CommittedChangeList>) =
      runInEdtIfNotDisposed { panel?.refreshChanges() }

    override fun refreshErrorStatusChanged(lastError: VcsException?) {
      lastError?.let { showOverChangesView(project, it.message, MessageType.ERROR) }
    }
  }

  internal class VisibilityPredicate : Predicate<Project> {
    override fun test(project: Project): Boolean {
      return ProjectLevelVcsManager.getInstance(project).allActiveVcss.any { isCommittedChangesAvailable(it) }
    }
  }

  internal class DisplayNameSupplier : Supplier<String> {
    override fun get(): String = VcsBundle.message("committed.changes.tab")
  }
}

internal fun isCommittedChangesAvailable(vcs: AbstractVcs): Boolean {
  return vcs.committedChangesProvider != null && vcs.type == VcsType.centralized
}

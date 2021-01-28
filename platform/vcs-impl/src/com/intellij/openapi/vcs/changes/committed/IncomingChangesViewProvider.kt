// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.committed

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.vcs.*
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.changes.committed.CommittedChangesCache.COMMITTED_TOPIC
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentProvider
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier.showOverChangesView
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList
import com.intellij.ui.content.Content
import com.intellij.util.NotNullFunction
import java.util.function.Supplier

class IncomingChangesViewProvider(private val project: Project) : ChangesViewContentProvider {
  private var browser: CommittedChangesTreeBrowser? = null

  override fun initTabContent(content: Content) =
    createIncomingChangesBrowser().let {
      browser = it
      content.component = it
      content.setDisposer(Disposable { browser = null })

      project.messageBus.connect(it).subscribe(COMMITTED_TOPIC, IncomingChangesListener())

      loadIncomingChanges(false)
    }

  private fun createIncomingChangesBrowser(): CommittedChangesTreeBrowser =
    CommittedChangesTreeBrowser(project, emptyList()).apply {
      emptyText.text = message("incoming.changes.not.loaded.message")

      val group = ActionManager.getInstance().getAction("IncomingChangesToolbar") as ActionGroup
      setToolBar(createGroupFilterToolbar(project, group, null, emptyList()).component)
      setTableContextMenu(group, emptyList())
    }

  private fun loadIncomingChanges(inBackground: Boolean) {
    val cache = CommittedChangesCache.getInstance(project)

    cache.hasCachesForAnyRoot { hasCaches: Boolean ->
      if (!hasCaches) return@hasCachesForAnyRoot

      val cachedIncomingChanges = cache.cachedIncomingChanges
      if (cachedIncomingChanges != null) {
        browser?.setIncomingChanges(cachedIncomingChanges)
      }
      else {
        cache.loadIncomingChangesAsync(
          { incomingChanges -> runInEdt { browser?.setIncomingChanges(incomingChanges) } },
          inBackground
        )
      }
    }
  }

  private fun CommittedChangesTreeBrowser.setIncomingChanges(changeLists: List<CommittedChangeList>) {
    emptyText.text = message("incoming.changes.empty.message")
    setItems(changeLists, CommittedChangesBrowserUseCase.INCOMING)
  }

  private inner class IncomingChangesListener : CommittedChangesListener {
    override fun changesLoaded(location: RepositoryLocation, changes: List<CommittedChangeList>) = updateModel()

    override fun incomingChangesUpdated(receivedChanges: List<CommittedChangeList>?) = updateModel()

    override fun changesCleared() {
      browser?.setIncomingChanges(emptyList())
    }

    override fun refreshErrorStatusChanged(lastError: VcsException?) {
      lastError?.let { showOverChangesView(project, it.message, MessageType.ERROR) }
    }

    private fun updateModel() =
      runInEdt {
        if (project.isDisposed || browser == null) return@runInEdt

        loadIncomingChanges(true)
      }
  }

  class VisibilityPredicate : NotNullFunction<Project, Boolean> {
    override fun `fun`(project: Project): Boolean =
      ProjectLevelVcsManager.getInstance(project).allActiveVcss.any { isIncomingChangesAvailable(it) }
  }

  class DisplayNameSupplier : Supplier<String> {
    override fun get(): String = message("incoming.changes.tab")
  }

  companion object {
    fun isIncomingChangesAvailable(vcs: AbstractVcs): Boolean =
      vcs.cachingCommittedChangesProvider?.supportsIncomingChanges() == true
  }
}
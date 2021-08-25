// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.stash.ui

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ui.SimpleChangesBrowser
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.concurrency.EdtExecutorService
import git4idea.i18n.GitBundle
import git4idea.stash.GitStashCache
import git4idea.ui.StashInfo
import java.util.concurrent.CompletableFuture

class GitStashChangesBrowser(project: Project) : SimpleChangesBrowser(project, false, false) {
  private val stashCache: GitStashCache get() = myProject.service()

  private var currentStash: StashInfo? = null
  private var currentChangesFuture: CompletableFuture<GitStashCache.StashData>? = null

  init {
    viewer.emptyText.text = GitBundle.message("stash.changes.empty")
  }

  fun selectStash(stash: StashInfo?) {
    if (stash == currentStash) return
    currentStash = stash
    currentChangesFuture?.cancel(false)
    currentChangesFuture = null

    if (stash == null) {
      setChangesToDisplay(emptyList())
      viewer.emptyText.text = GitBundle.message("stash.changes.empty")
      return
    }

    setChangesToDisplay(emptyList())
    viewer.emptyText.text = GitBundle.message("stash.changes.loading")

    val futureChanges = stashCache.loadStashData(stash) ?: return
    currentChangesFuture = futureChanges
    futureChanges.thenRunAsync(Runnable {
      if (currentStash != stash) return@Runnable

      when (val stashData = currentChangesFuture?.get()) {
        is GitStashCache.StashData.ChangeList -> {
          setChangesToDisplay(stashData.changeList.changes)
          viewer.emptyText.text = ""
        }
        is GitStashCache.StashData.Error -> {
          setChangesToDisplay(emptyList())
          viewer.emptyText.setText(stashData.error.localizedMessage, SimpleTextAttributes.ERROR_ATTRIBUTES)
        }
      }
      currentChangesFuture = null
    }, EdtExecutorService.getInstance())
  }

  override fun createPopupMenuActions(): List<AnAction> {
    return super.createPopupMenuActions() + ActionManager.getInstance().getAction("Git.Stash.ChangesBrowser.ContextMenu")
  }
}
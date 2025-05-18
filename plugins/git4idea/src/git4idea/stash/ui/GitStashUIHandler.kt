// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.stash.ui

import com.intellij.notification.NotificationAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.savedPatches.SavedPatchesUi
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager.Companion.getToolWindowFor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.VcsShowToolWindowTabAction
import git4idea.i18n.GitBundle
import git4idea.index.isStagingAreaAvailable
import git4idea.index.showToolWindowTab
import git4idea.repo.GitRepositoryManager
import git4idea.stash.GitStashTracker
import git4idea.stash.isNotEmpty
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface GitStashUIHandler {
  fun isStashTabAvailable(): Boolean
  fun isStashTabAvailableInWindow(): Boolean
  fun isStashTabVisible(): Boolean
  fun showStashes()
  fun showStashes(root: VirtualFile?)
  fun showStashesNotificationActions(roots: Collection<VirtualFile>): List<NotificationAction>
}

private class GitStashUIHandlerImpl(
  private val project: Project
) : GitStashUIHandler {
  override fun isStashTabAvailable(): Boolean {
    return stashToolWindowRegistryOption().asBoolean()
  }

  override fun isStashTabAvailableInWindow(): Boolean {
    return isStashTabAvailable() && getToolWindowFor(project, GitStashContentProvider.TAB_NAME) != null
  }

  override fun isStashTabVisible(): Boolean {
    if (!isStashTabAvailable()) return false
    return isStashesAndShelvesTabEnabled(project) || project.service<GitStashTracker>().isNotEmpty()
  }

  override fun showStashes() {
    VcsShowToolWindowTabAction.activateVcsTab(project, GitStashContentProvider.TAB_NAME, true)
  }

  override fun showStashes(root: VirtualFile?) {
    val repository = root?.let { GitRepositoryManager.getInstance(project).getRepositoryForRootQuick(root) }
    showToolWindowTab(project, GitStashContentProvider.TAB_NAME) { component ->
      val savedPatchesUi = component as? SavedPatchesUi ?: return@showToolWindowTab
      val provider = savedPatchesUi.providers.filterIsInstance<GitStashProvider>().firstOrNull() ?: return@showToolWindowTab
      if (repository == null) {
        savedPatchesUi.showFirstUnderProvider(provider)
      } else {
        savedPatchesUi.showFirstUnderObject(provider, repository)
      }
    }
  }

  override fun showStashesNotificationActions(roots: Collection<VirtualFile>): List<NotificationAction> {
    return buildList {
      if (isStashTabAvailable()) {
        add(NotificationAction.createSimple(GitBundle.message("stash.view.stashes.link")) {
          showStashes(roots.firstOrNull())
        })
      }
      else if (isStagingAreaAvailable(project)) {
        add(NotificationAction.createSimpleExpiring(GitBundle.message("stash.enable.stashes.link")) {
          stashToolWindowRegistryOption().setValue(true)
          showStashes(roots.firstOrNull())
        })
      }
    }
  }
}
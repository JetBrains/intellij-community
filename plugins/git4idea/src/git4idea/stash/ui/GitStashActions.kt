// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.stash.ui

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.savedPatches.SavedPatchesOperationsGroup
import com.intellij.openapi.vcs.changes.savedPatches.SavedPatchesProvider
import com.intellij.openapi.vcs.changes.savedPatches.SavedPatchesUi.Companion.SAVED_PATCHES_UI
import com.intellij.openapi.vfs.VirtualFile
import git4idea.i18n.GitBundle
import git4idea.stash.GitStashOperations
import git4idea.stash.GitStashTracker
import git4idea.ui.StashInfo

val STASH_INFO = DataKey.create<List<StashInfo>>("GitStashInfoList")

abstract class GitSingleStashAction : DumbAwareAction() {
  abstract fun perform(project: Project, stashInfo: StashInfo): Boolean

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = e.project != null && e.getData(STASH_INFO)?.size == 1
    e.presentation.isVisible = e.isFromActionToolbar || (e.project != null && e.getData(STASH_INFO) != null)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val stashInfos = e.getData(STASH_INFO) ?: return
    if (perform(project, stashInfos.single())) {
      e.project!!.serviceIfCreated<GitStashTracker>()?.scheduleRefresh()
    }
  }
}

class GitDropStashAction : GitSingleStashAction() {
  override fun perform(project: Project, stashInfo: StashInfo): Boolean {
    return GitStashOperations.dropStashWithConfirmation(project, null, stashInfo)
  }
}

class GitPopStashAction : GitSingleStashAction() {
  override fun perform(project: Project, stashInfo: StashInfo): Boolean {
    return GitStashOperations.unstash(project, stashInfo, null, true, false)
  }
}

class GitApplyStashAction : GitSingleStashAction() {
  override fun perform(project: Project, stashInfo: StashInfo): Boolean {
    return GitStashOperations.unstash(project, stashInfo, null, false, false)
  }
}

class GitUnstashAsAction : GitSingleStashAction() {
  override fun perform(project: Project, stashInfo: StashInfo): Boolean {
    val dialog = GitUnstashAsDialog(project, stashInfo)
    if (dialog.showAndGet()) {
      return GitStashOperations.unstash(project, stashInfo, dialog.branch, dialog.popStash, dialog.keepIndex)
    }
    return false
  }
}

class GitClearStashesAction : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    val project = e.project
    val isVisible = project != null && e.getData(SAVED_PATCHES_UI) != null
    e.presentation.isVisible = isVisible

    val root = project?.let { getSelectedRoot(it, e) }
    e.presentation.isEnabled = isVisible && root != null

    val allRoots = project?.serviceIfCreated<GitStashTracker>()?.roots
    if (root != null && allRoots?.size?.let { it > 1 } == true) {
      e.presentation.text = GitBundle.message("action.Git.Stash.Clear.in.root.text", root.name)
      e.presentation.description = GitBundle.message("action.Git.Stash.Clear.in.root.description")
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val root = getSelectedRoot(project, e) ?: return
    GitStashOperations.clearStashesWithConfirmation(project, root, e.getData(SAVED_PATCHES_UI))
  }

  private fun getSelectedRoot(project: Project, e: AnActionEvent): VirtualFile? {
    val stashInfos = e.getData(STASH_INFO)
    if (!stashInfos.isNullOrEmpty()) {
      val roots = stashInfos.mapTo(mutableSetOf()) { it.root }
      return roots.singleOrNull()
    }
    val stashTracker = project.serviceIfCreated<GitStashTracker>()
    val root = stashTracker?.roots?.singleOrNull() ?: return null
    if (stashTracker.getStashes(root).isNotEmpty()) return root
    return null
  }
}

class GitStashOperationsGroup : SavedPatchesOperationsGroup() {
  override fun isApplicable(patchObject: SavedPatchesProvider.PatchObject<*>): Boolean {
    return patchObject.data is StashInfo
  }
}
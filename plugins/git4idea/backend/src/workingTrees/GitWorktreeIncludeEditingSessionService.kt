// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotifications
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks which open `.worktreeinclude` files still have a New Worktree dialog waiting for the user to come
 * back to it. [GitWorktreeIncludeEditorNotificationProvider] reads this to show its "Return to the dialog"
 * banner, and [git4idea.workingTrees.GitCreateWorkingTreeService] registers and clears the entry per file.
 */
@Service(Service.Level.PROJECT)
internal class GitWorktreeIncludeEditingSessionService(private val project: Project) {

  companion object {
    fun getInstance(project: Project): GitWorktreeIncludeEditingSessionService = project.service()
  }

  private val pendingReturns = ConcurrentHashMap<VirtualFile, () -> Unit>()

  internal fun hasPendingReturn(file: VirtualFile): Boolean = pendingReturns.containsKey(file)

  /** Registers [onReturn] for [file] and refreshes that file's editor notifications to show the banner. */
  internal fun setPendingReturn(file: VirtualFile, onReturn: () -> Unit) {
    pendingReturns[file] = onReturn
    EditorNotifications.getInstance(project).updateNotifications(file)
  }

  /** Removes any pending return for [file] and refreshes its editor notifications to hide the banner. */
  internal fun clearPendingReturn(file: VirtualFile) {
    if (pendingReturns.remove(file) != null) {
      EditorNotifications.getInstance(project).updateNotifications(file)
    }
  }

  /** Invoked by the "Return to the dialog" notification action for [file]; a no-op once nothing is pending. */
  internal fun returnToDialog(file: VirtualFile) {
    pendingReturns[file]?.invoke()
  }
}

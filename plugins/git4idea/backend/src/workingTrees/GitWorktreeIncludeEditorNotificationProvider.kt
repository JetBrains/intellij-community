// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import git4idea.i18n.GitBundle
import java.util.function.Function
import javax.swing.JComponent

/**
 * Shows a banner on a `.worktreeinclude` file that a New Worktree dialog is still waiting for, with an
 * action that reopens that dialog.
 */
internal class GitWorktreeIncludeEditorNotificationProvider : EditorNotificationProvider, DumbAware {
  override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? {
    if (file.name != WORKTREE_INCLUDE_FILE_NAME) return null
    if (!GitWorktreeIncludeEditingSessionService.getInstance(project).hasPendingReturn(file)) return null

    return Function { fileEditor ->
      val panel = EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Info)
      panel.text = GitBundle.message("working.tree.dialog.worktree.include.editor.notification.text")
      panel.createActionLabel(GitBundle.message("working.tree.dialog.worktree.include.editor.notification.action")) {
        GitWorktreeIncludeEditingSessionService.getInstance(project).returnToDialog(file)
      }
      panel
    }
  }
}

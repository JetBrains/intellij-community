// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.index.vfs

import com.intellij.codeInsight.hint.HintUtil
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import git4idea.i18n.GitBundle
import java.util.function.Function
import javax.swing.JComponent

private class GitIndexVirtualFileEditorNotificationProvider : EditorNotificationProvider {
  override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? {
    if (file !is GitIndexVirtualFile) {
      return null
    }

    return Function {
      val panel = EditorNotificationPanel(HintUtil.INFORMATION_COLOR_KEY, EditorNotificationPanel.Status.Info)
      panel.text = GitBundle.message("stage.vfs.editor.notification.text", file.name)
      if (file.filePath.virtualFile != null) {
        panel.createActionLabel(GitBundle.message("stage.vfs.editor.notification.link"), "Git.Stage.Show.Local")
      }
      panel
    }
  }
}
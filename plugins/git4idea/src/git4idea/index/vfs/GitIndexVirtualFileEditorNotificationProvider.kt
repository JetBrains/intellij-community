// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index.vfs

import com.intellij.codeInsight.hint.HintUtil
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotifications
import git4idea.i18n.GitBundle

private val KEY: Key<EditorNotificationPanel> = Key.create("GitIndexVirtualFileEditorNotification")

class GitIndexVirtualFileEditorNotificationProvider : EditorNotifications.Provider<EditorNotificationPanel>() {
  override fun getKey(): Key<EditorNotificationPanel> = KEY

  override fun createNotificationPanel(file: VirtualFile, fileEditor: FileEditor, project: Project): EditorNotificationPanel? {
    if (file !is GitIndexVirtualFile) return null
    return EditorNotificationPanel(HintUtil.INFORMATION_COLOR_KEY).apply {
      text = GitBundle.message("stage.vfs.editor.notification.text", file.name)
      if (file.filePath.virtualFile != null) {
        createActionLabel(GitBundle.message("stage.vfs.editor.notification.link"), "Git.Stage.Show.Local")
      }
    }
  }
}
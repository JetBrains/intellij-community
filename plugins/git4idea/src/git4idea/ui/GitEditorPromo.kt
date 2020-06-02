// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ui

import com.intellij.codeInsight.hint.HintUtil
import com.intellij.icons.AllIcons
import com.intellij.ide.CommandLineWaitingManager
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.help.HelpManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.IconButton
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotifications
import com.intellij.ui.InplaceButton
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepositoryFiles
import git4idea.repo.GitRepositoryManager
import java.awt.BorderLayout

private val KEY: Key<EditorNotificationPanel> = Key.create("GitEditorPromo")
private const val PROMO_DISMISSED_KEY = "git.editor.promo.dismissed"

class GitEditorPromo : EditorNotifications.Provider<EditorNotificationPanel>() {
  override fun getKey(): Key<EditorNotificationPanel> = KEY

  override fun createNotificationPanel(file: VirtualFile,
                                       fileEditor: FileEditor,
                                       project: Project): EditorNotificationPanel? {
    return if (isEnabled() && CommandLineWaitingManager.getInstance().hasHookFor(file)
               && file.name == GitRepositoryFiles.COMMIT_EDITMSG) {
      EditorNotificationPanel(HintUtil.PROMOTION_PANE_KEY).apply {
        icon(AllIcons.Ide.Gift)
        text = GitBundle.message("editor.promo.commit.text", ApplicationNamesInfo.getInstance().fullProductName)
        val repository = GitRepositoryManager.getInstance(project).repositories.find { it.repositoryFiles.isCommitMessageFile(file.path) }
        if (repository != null) {
          createActionLabel(GitBundle.message("editor.promo.commit.try.link"), IdeActions.ACTION_CHECKIN_PROJECT, false)
        }
        else {
          createActionLabel(GitBundle.message("editor.promo.help.link")) {
            HelpManager.getInstance().invokeHelp("Commit and push changes")
          }
        }
        add(InplaceButton(IconButton(GitBundle.message("editor.promo.close.link"), AllIcons.Actions.Close, AllIcons.Actions.CloseHovered)) {
          PropertiesComponent.getInstance().setValue(PROMO_DISMISSED_KEY, true)
          EditorNotifications.getInstance(project).updateNotifications(this@GitEditorPromo)
        }, BorderLayout.EAST)
      }
    }
    else null
  }

  private fun isEnabled(): Boolean = !PropertiesComponent.getInstance().getBoolean(PROMO_DISMISSED_KEY)
}
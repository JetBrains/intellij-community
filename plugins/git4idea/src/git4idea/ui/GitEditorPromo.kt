// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications
import com.intellij.ui.InplaceButton
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepositoryFiles
import git4idea.repo.GitRepositoryManager
import java.awt.BorderLayout
import java.util.function.Function
import javax.swing.JComponent

private const val PROMO_DISMISSED_KEY = "git.editor.promo.dismissed"

private class GitEditorPromo : EditorNotificationProvider {
  override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? {
    if (!isEnabled() || !CommandLineWaitingManager.getInstance().hasHookFor(file) || file.name != GitRepositoryFiles.COMMIT_EDITMSG) {
      return null
    }

    return Function {
      val panel = EditorNotificationPanel(HintUtil.PROMOTION_PANE_KEY, EditorNotificationPanel.Status.Info)
      panel.icon(AllIcons.Ide.Gift)
      panel.text = GitBundle.message("editor.promo.commit.text", ApplicationNamesInfo.getInstance().fullProductName)
      val repository = GitRepositoryManager.getInstance(project).repositories.find { it.repositoryFiles.isCommitMessageFile(file.path) }
      if (repository == null) {
        panel.createActionLabel(GitBundle.message("editor.promo.help.link")) {
          HelpManager.getInstance().invokeHelp("Commit and push changes")
        }
      }
      else {
        panel.createActionLabel(GitBundle.message("editor.promo.commit.try.link"), IdeActions.ACTION_CHECKIN_PROJECT, false)
      }
      panel.add(
        InplaceButton(IconButton(GitBundle.message("editor.promo.close.link"), AllIcons.Actions.Close, AllIcons.Actions.CloseHovered)) {
          PropertiesComponent.getInstance().setValue(PROMO_DISMISSED_KEY, true)
          EditorNotifications.getInstance(project).updateNotifications(this)
        }, BorderLayout.EAST)
      panel
    }
  }

  private fun isEnabled(): Boolean = !PropertiesComponent.getInstance().getBoolean(PROMO_DISMISSED_KEY)
}
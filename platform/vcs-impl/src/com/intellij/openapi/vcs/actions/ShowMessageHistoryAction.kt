// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.actions.ContentChooser
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.CommitMessageI
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.VcsDataKeys

/**
 * Action showing the history of recently used commit messages. Source code of this class is provided
 * as a sample of using the [CheckinProjectPanel] API. Actions to be shown in the commit dialog
 * should be added to the `Vcs.MessageActionGroup` action group.
 */
class ShowMessageHistoryAction : DumbAwareAction() {
  init {
    isEnabledInModalContext = true
  }

  override fun update(e: AnActionEvent) {
    val project = e.project
    val commitMessage = getCommitMessage(e)

    e.presentation.isVisible = project != null && commitMessage != null
    e.presentation.isEnabled = e.presentation.isVisible && !VcsConfiguration.getInstance(project!!).recentMessages.isEmpty()
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project!!
    val commitMessage = getCommitMessage(e)!!
    val configuration = VcsConfiguration.getInstance(project)
    val contentChooser = object : ContentChooser<String>(project, message("dialog.title.choose.commit.message.from.history"), false) {
      override fun removeContentAt(content: String) = configuration.removeMessage(content)
      override fun getStringRepresentationFor(content: String) = content
      override fun getContents(): List<String> = configuration.recentMessages.reversed()
    }

    if (contentChooser.showAndGet()) {
      val selectedIndex = contentChooser.selectedIndex

      if (selectedIndex >= 0) {
        commitMessage.setCommitMessage(contentChooser.allContents[selectedIndex])
      }
    }
  }

  private fun getCommitMessage(e: AnActionEvent): CommitMessageI? =
    e.getData(CheckinProjectPanel.PANEL_KEY) as? CommitMessageI ?: e.getData(VcsDataKeys.COMMIT_MESSAGE_CONTROL)
}

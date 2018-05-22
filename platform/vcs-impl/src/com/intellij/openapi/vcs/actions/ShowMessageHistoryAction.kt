// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.actions.ContentChooser
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.vcs.*
import java.util.*

/**
 * Action showing the history of recently used commit messages. Source code of this class is provided
 * as a sample of using the [CheckinProjectPanel] API. Actions to be shown in the commit dialog
 * should be added to the `Vcs.MessageActionGroup` action group.
 *
 * @author lesya
 * @since 5.1
 */
class ShowMessageHistoryAction : AnAction(), DumbAware {
  init {
    isEnabledInModalContext = true
  }

  override fun update(e: AnActionEvent) {
    super.update(e)

    val dc = e.dataContext
    val project = CommonDataKeys.PROJECT.getData(dc)
    var panel: Any? = CheckinProjectPanel.PANEL_KEY.getData(dc)
    if (panel !is CommitMessageI) {
      panel = VcsDataKeys.COMMIT_MESSAGE_CONTROL.getData(dc)
    }

    if (project == null || panel == null) {
      e.presentation.isVisible = false
      e.presentation.isEnabled = false
    }
    else {
      e.presentation.isVisible = true
      val recentMessages = VcsConfiguration.getInstance(project).recentMessages
      e.presentation.isEnabled = !recentMessages.isEmpty()
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val commitMessageI: CommitMessageI?
    val dc = e.dataContext
    val project = CommonDataKeys.PROJECT.getData(dc)
    val panel = CheckinProjectPanel.PANEL_KEY.getData(dc)
    commitMessageI = if (panel is CommitMessageI) panel else VcsDataKeys.COMMIT_MESSAGE_CONTROL.getData(dc)

    if (commitMessageI != null && project != null) {
      val configuration = VcsConfiguration.getInstance(project)


      if (!configuration.recentMessages.isEmpty()) {

        val contentChooser = object : ContentChooser<String>(project, VcsBundle.message("dialog.title.choose.commit.message.from.history"),
                                                             false) {
          override fun removeContentAt(content: String) {
            configuration.removeMessage(content)
          }

          override fun getStringRepresentationFor(content: String): String? {
            return content
          }

          override fun getContents(): List<String> {
            val recentMessages = configuration.recentMessages
            Collections.reverse(recentMessages)
            return recentMessages
          }
        }

        if (contentChooser.showAndGet()) {
          val selectedIndex = contentChooser.selectedIndex

          if (selectedIndex >= 0) {
            commitMessageI.setCommitMessage(contentChooser.allContents[selectedIndex])
          }
        }
      }
    }
  }
}

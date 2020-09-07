// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.action.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.github.i18n.GithubBundle
import javax.swing.JComponent
import javax.swing.JLabel

class GithubMergeCommitMessageDialog(project: Project,
                                     @NlsContexts.DialogTitle title: String,
                                     subject: String,
                                     body: String) : DialogWrapper(project) {

  private val commitMessage = CommitMessage(project, false, false, true).apply {
    setCommitMessage("$subject\n\n$body")
    preferredSize = JBDimension(500, 85)
  }

  init {
    Disposer.register(disposable, commitMessage)

    setTitle(title)
    setOKButtonText(GithubBundle.message("merge.commit.dialog.merge.button"))
    init()
  }

  override fun createCenterPanel(): JComponent? {
    return JBUI.Panels.simplePanel(0, UIUtil.DEFAULT_VGAP)
      .addToTop(JLabel(GithubBundle.message("merge.commit.dialog.message")))
      .addToCenter(commitMessage)
  }

  val message: Pair<String, String>
    get() {
      val text = commitMessage.comment

      val idx = text.indexOf("\n\n")
      return if (idx < 0) "" to text
      else {
        val subject = text.substring(0, idx)
        if (subject.contains("\n")) "" to text
        else subject to text.substring(idx + 2)
      }
    }
}
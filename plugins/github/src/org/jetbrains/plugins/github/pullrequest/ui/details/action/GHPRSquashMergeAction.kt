// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.details.action

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.pullrequest.action.ui.GithubMergeCommitMessageDialog
import org.jetbrains.plugins.github.pullrequest.data.GHPRBusyStateTracker
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRStateService
import org.jetbrains.plugins.github.ui.util.HtmlEditorPane
import org.jetbrains.plugins.github.ui.util.SingleValueModel
import org.jetbrains.plugins.github.util.errorOnEdt
import org.jetbrains.plugins.github.util.handleOnEdt
import org.jetbrains.plugins.github.util.successOnEdt
import java.awt.event.ActionEvent
import javax.swing.AbstractAction

internal class GHPRSquashMergeAction(private val project: Project,
                                     private val dataProvider: GHPRDataProvider,
                                     private val detailsModel: SingleValueModel<out GHPullRequestShort>,
                                     private val busyStateTracker: GHPRBusyStateTracker,
                                     private val stateService: GHPRStateService,
                                     private val errorPanel: HtmlEditorPane)
  : AbstractAction("Squash and Merge...") {

  override fun actionPerformed(e: ActionEvent) {
    detailsModel.value.let {
      if (it !is GHPullRequest) return
      if (!busyStateTracker.acquire(it.number)) return
      errorPanel.setBody("")


      dataProvider.apiCommitsRequest.successOnEdt { commits ->
        val body = "* " + StringUtil.join(commits, { it.message }, "\n\n* ")
        val dialog = GithubMergeCommitMessageDialog(project,
                                                    "Merge Pull Request",
                                                    "Merge pull request #${it.number}",
                                                    body)
        if (!dialog.showAndGet()) {
          throw ProcessCanceledException()
        }
        dialog.message
      }.thenCompose { message ->
        stateService.squashMerge(EmptyProgressIndicator(), it.number, message, it.headRefOid)
      }.errorOnEdt { error ->
        //language=HTML
        errorPanel.setBody("<p>Error occurred while merging pull request:</p>" + "<p>${error.message.orEmpty()}</p>")
      }.handleOnEdt { _, _ ->
        busyStateTracker.release(it.number)
      }
    }
  }
}
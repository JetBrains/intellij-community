// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.details.action

import com.intellij.openapi.progress.EmptyProgressIndicator
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRStateService
import org.jetbrains.plugins.github.ui.util.HtmlEditorPane
import org.jetbrains.plugins.github.ui.util.SingleValueModel
import org.jetbrains.plugins.github.util.errorOnEdt
import org.jetbrains.plugins.github.util.handleOnEdt
import java.awt.event.ActionEvent
import javax.swing.AbstractAction

internal class GHPRRebaseMergeAction(private val detailsModel: SingleValueModel<out GHPullRequestShort>,
                                     private val busyStateModel: SingleValueModel<Boolean>,
                                     private val stateService: GHPRStateService,
                                     private val errorPanel: HtmlEditorPane)
  : AbstractAction("Rebase and Merge") {

  override fun actionPerformed(e: ActionEvent) {
    detailsModel.value.let {
      if (it !is GHPullRequest) return
      if (busyStateModel.value) return
      busyStateModel.value = true
      errorPanel.setBody("")

      stateService.rebaseMerge(EmptyProgressIndicator(), it.number, it.headRefOid)
        .errorOnEdt { error ->
          //language=HTML
          errorPanel.setBody("<p>Error occurred while merging pull request:</p>" + "<p>${error.message.orEmpty()}</p>")
        }
        .handleOnEdt { _, _ ->
          busyStateModel.value = false
        }
    }
  }
}
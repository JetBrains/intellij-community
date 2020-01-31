// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.details.action

import com.intellij.openapi.progress.EmptyProgressIndicator
import org.jetbrains.plugins.github.pullrequest.data.GHPRBusyStateTracker
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRStateService
import org.jetbrains.plugins.github.ui.util.HtmlEditorPane
import org.jetbrains.plugins.github.util.errorOnEdt
import org.jetbrains.plugins.github.util.handleOnEdt
import java.awt.event.ActionEvent
import javax.swing.AbstractAction

internal class GHPRCloseAction(private val number: Long,
                               private val busyStateTracker: GHPRBusyStateTracker,
                               private val stateService: GHPRStateService,
                               private val errorPanel: HtmlEditorPane)
  : AbstractAction("Close") {

  override fun actionPerformed(e: ActionEvent?) {
    if (!busyStateTracker.acquire(number)) return
    errorPanel.setBody("")
    stateService.close(EmptyProgressIndicator(), number)
      .errorOnEdt { error ->
        //language=HTML
        errorPanel.setBody("<p>Error occurred while closing pull request:</p>" + "<p>${error.message.orEmpty()}</p>")
      }
      .handleOnEdt { _, _ ->
        busyStateTracker.release(number)
      }
  }
}
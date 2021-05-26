// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui

import org.jetbrains.plugins.github.i18n.GithubBundle
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Action

open class GHRetryLoadingErrorHandler(protected val resetRunnable: () -> Unit) : GHLoadingErrorHandler {
  override fun getActionForError(error: Throwable): Action? = RetryAction()

  protected inner class RetryAction : AbstractAction(GithubBundle.message("retry.action")) {
    override fun actionPerformed(e: ActionEvent?) {
      resetRunnable()
    }
  }
}
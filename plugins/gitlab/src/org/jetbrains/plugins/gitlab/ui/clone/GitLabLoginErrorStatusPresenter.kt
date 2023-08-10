// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui.clone

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.codereview.list.error.ErrorStatusPresenter
import java.net.ConnectException
import javax.swing.Action

internal class GitLabLoginErrorStatusPresenter : ErrorStatusPresenter<Throwable> {
  override fun getErrorTitle(error: Throwable): String = CollaborationToolsBundle.message("clone.dialog.login.failed")

  override fun getErrorDescription(error: Throwable): String = when (error) {
    is ConnectException -> CollaborationToolsBundle.message("clone.dialog.login.error.server")
    else -> error.localizedMessage.orEmpty()
  }

  override fun getErrorAction(error: Throwable): Action? = null
}
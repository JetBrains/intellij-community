// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui.clone

import com.intellij.collaboration.auth.ui.login.LoginException
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.ExceptionUtil
import com.intellij.collaboration.ui.codereview.list.error.ErrorStatusPresenter
import com.intellij.collaboration.ui.util.swingAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.authentication.ui.GitLabTokenLoginPanelModel
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import java.net.ConnectException
import javax.swing.Action

internal class GitLabLoginErrorStatusPresenter(
  private val cs: CoroutineScope,
  private val model: GitLabTokenLoginPanelModel,
) : ErrorStatusPresenter.Text<Throwable> {
  override fun getErrorTitle(error: Throwable): String = CollaborationToolsBundle.message("clone.dialog.login.failed")

  override fun getErrorDescription(error: Throwable): String = when (error) {
    is ConnectException -> CollaborationToolsBundle.message("clone.dialog.login.error.server")
    is LoginException.UnsupportedServerVersion -> {
      GitLabBundle.message("server.version.unsupported", error.version, error.earliestSupportedVersion)
    }
    is LoginException.InvalidTokenOrUnsupportedServerVersion -> {
      GitLabBundle.message("invalid.token.or.server.version.unsupported", error.earliestSupportedVersion)
    }
    else -> ExceptionUtil.getPresentableMessage(error)
  }

  override fun getErrorAction(error: Throwable): Action? = when (error) {
    is LoginException.UnsupportedServerVersion,
    is LoginException.InvalidTokenOrUnsupportedServerVersion -> swingAction(CollaborationToolsBundle.message("login.via.git")) {
      cs.launch {
        model.tryGitAuthorization()
      }
    }
    else -> null
  }
}
// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui.clone

import com.intellij.collaboration.messages.CollaborationToolsBundle
import org.jetbrains.annotations.Nls

internal sealed class GitLabCloneException(
  val message: @Nls String,
  val errorActionConfig: ErrorActionConfig
) {
  class MissingAccessToken(loginAction: () -> Unit) : GitLabCloneException(
    CollaborationToolsBundle.message("account.token.missing"),
    ErrorActionConfig(loginAction, CollaborationToolsBundle.message("login.again.action.text"))
  )

  class RevokedToken(loginAction: () -> Unit) : GitLabCloneException(
    CollaborationToolsBundle.message("http.status.error.refresh.token"),
    ErrorActionConfig(loginAction, CollaborationToolsBundle.message("login.again.action.text"))
  )

  class ConnectionError(reloadAction: () -> Unit) : GitLabCloneException(
    CollaborationToolsBundle.message("error.connection.error"),
    ErrorActionConfig(reloadAction, CollaborationToolsBundle.message("clone.dialog.error.retry"))
  )

  class Unknown(message: @Nls String, reloadAction: () -> Unit) : GitLabCloneException(
    message,
    ErrorActionConfig(reloadAction, CollaborationToolsBundle.message("clone.dialog.error.retry"))
  )

  class ErrorActionConfig(
    val action: () -> Unit,
    val name: @Nls String
  )
}
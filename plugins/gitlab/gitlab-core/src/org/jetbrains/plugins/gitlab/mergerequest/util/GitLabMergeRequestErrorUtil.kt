// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.util

import com.intellij.collaboration.api.HttpStatusErrorException
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.codereview.list.error.ErrorStatusPresenter
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.gitlab.api.data.GitLabHttpStatusError.HttpStatusErrorType
import org.jetbrains.plugins.gitlab.api.data.asGitLabStatusError
import org.jetbrains.plugins.gitlab.authentication.GitLabLoginSource
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountViewModel
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import java.net.ConnectException
import javax.swing.Action

object GitLabMergeRequestErrorUtil {
  internal fun createErrorStatusPresenter(
    accountVm: GitLabAccountViewModel,
    reloadAction: Action?,
    loginSource: GitLabLoginSource,
  ): ErrorStatusPresenter.Text<Throwable> = ErrorStatusPresenter.simple(
    GitLabBundle.message("merge.request.list.error"),
    descriptionProvider = descriptionProvider@{ error ->
      when (error) {
        is ConnectException -> CollaborationToolsBundle.message("review.list.connection.error")
        is HttpStatusErrorException -> {
          val actualError = error.asGitLabStatusError() ?: return@descriptionProvider error.localizedMessage
          when (actualError.statusErrorType) {
            HttpStatusErrorType.INVALID_TOKEN -> CollaborationToolsBundle.message("http.status.error.refresh.token")
            HttpStatusErrorType.UNKNOWN -> CollaborationToolsBundle.message("http.request.error") + "\n" + actualError.error
          }
        }
        else -> error.localizedMessage
      }
    },
    actionProvider = actionProvider@{ error ->
      when (error) {
        is HttpStatusErrorException -> {
          val actualError = error.asGitLabStatusError() ?: return@actionProvider null
          when (actualError.statusErrorType) {
            HttpStatusErrorType.INVALID_TOKEN -> accountVm.loginAction(loginSource)
            HttpStatusErrorType.UNKNOWN -> null
          }
        }
        else -> reloadAction
      }
    }
  )
}

internal fun Throwable.localizedMessageOrClassName(): @Nls String =
  localizedMessage ?: this::class.simpleName ?: "${javaClass.name} occurred"

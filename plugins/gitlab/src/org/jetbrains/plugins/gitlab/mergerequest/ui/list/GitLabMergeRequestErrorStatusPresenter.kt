// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.list

import com.intellij.collaboration.api.HttpStatusErrorException
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.codereview.list.error.ErrorStatusPresenter
import com.intellij.util.asSafely
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.gitlab.api.data.GitLabHttpStatusError.HttpStatusErrorType
import org.jetbrains.plugins.gitlab.api.data.asGitLabStatusError
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountViewModel
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import javax.swing.Action

internal class GitLabMergeRequestErrorStatusPresenter(private val accountVm: GitLabAccountViewModel) : ErrorStatusPresenter<Throwable> {
  override fun getErrorTitle(error: Throwable): @Nls String = GitLabBundle.message("merge.request.list.error")

  override fun getErrorDescription(error: Throwable): @Nls String {
    return when (error) {
      is HttpStatusErrorException -> {
        val actualError = error.asGitLabStatusError() ?: return error.localizedMessage
        when (actualError.statusErrorType) {
          HttpStatusErrorType.INVALID_TOKEN -> CollaborationToolsBundle.message("http.status.error.refresh.token")
          HttpStatusErrorType.UNKNOWN -> CollaborationToolsBundle.message("http.request.error") + "\n" + actualError.error
        }
      }
      else -> error.localizedMessage
    }
  }

  override fun getErrorAction(error: Throwable): Action? {
    val actualError = error.asSafely<HttpStatusErrorException>()?.asGitLabStatusError() ?: return null
    return when (actualError.statusErrorType) {
      HttpStatusErrorType.INVALID_TOKEN -> accountVm.loginAction()
      HttpStatusErrorType.UNKNOWN -> null
    }
  }
}
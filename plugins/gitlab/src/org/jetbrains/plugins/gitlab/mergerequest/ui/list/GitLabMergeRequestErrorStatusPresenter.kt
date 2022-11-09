// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.list

import com.intellij.collaboration.api.HttpStatusErrorException
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.codereview.list.error.ErrorStatusPresenter
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.gitlab.api.data.GitLabHttpStatusError
import org.jetbrains.plugins.gitlab.api.data.GitLabHttpStatusError.HttpStatusErrorType
import org.jetbrains.plugins.gitlab.api.data.asGitLabStatusError
import org.jetbrains.plugins.gitlab.util.GitLabBundle

internal class GitLabMergeRequestErrorStatusPresenter : ErrorStatusPresenter {
  override fun getErrorTitle(error: Throwable): @Nls String = GitLabBundle.message("merge.request.list.error")

  override fun getErrorDescription(error: Throwable): @Nls String {
    val httpStatusError = parseHttpStatusError(error) ?: return CollaborationToolsBundle.message("http.status.error.unknown")
    return when (httpStatusError.statusErrorType) {
      HttpStatusErrorType.INVALID_TOKEN -> CollaborationToolsBundle.message("http.status.error.refresh.token")
      HttpStatusErrorType.UNKNOWN -> CollaborationToolsBundle.message("http.status.error.unknown")
    }
  }

  private fun parseHttpStatusError(error: Throwable): GitLabHttpStatusError? = when (error) {
    is HttpStatusErrorException -> error.asGitLabStatusError()
    else -> null
  }
}
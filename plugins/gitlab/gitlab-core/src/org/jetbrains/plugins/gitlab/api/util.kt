// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api

import com.intellij.collaboration.api.HttpStatusErrorException
import com.intellij.collaboration.util.resolveRelative
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabCredentialsRefreshException
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabMissingCredentialsException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

object GitLabApiUtil {
  fun isAuthorizationError(exception: Throwable): Boolean {
    return exception is GitLabCredentialsRefreshException ||
           exception is GitLabMissingCredentialsException ||
           isInvalidCredentialsError(exception)
  }

  fun isInvalidCredentialsError(exception: Throwable): Boolean {
    return exception is HttpStatusErrorException && exception.statusCode == HttpURLConnection.HTTP_UNAUTHORIZED
  }
}

fun GitLabApi.projectApiUrl(projectId: String): URI = server.projectApiUri(URLEncoder.encode(projectId, Charsets.UTF_8))

fun GitLabServerPath.projectApiUri(projectId: String): URI = restApiUri
  .resolveRelative("projects/")
  .resolveRelative("$projectId/")

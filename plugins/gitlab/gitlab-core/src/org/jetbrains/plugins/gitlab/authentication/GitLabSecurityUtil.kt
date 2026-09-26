// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.authentication

import com.intellij.collaboration.auth.ui.login.LoginException
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.util.Urls.parseEncoded
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.gitlab.GitLabServersManager
import org.jetbrains.plugins.gitlab.api.GitLabApiManager
import org.jetbrains.plugins.gitlab.api.GitLabServerPath
import org.jetbrains.plugins.gitlab.api.getMetadataOrNull
import org.jetbrains.plugins.gitlab.api.request.getCurrentUser

object GitLabSecurityUtil {
  private const val API_SCOPE = "api"
  private const val READ_USER_SCOPE = "read_user"
  private const val DEFAULT_CLIENT_NAME = "GitLab Integration Plugin"
  val MASTER_SCOPES = listOf(API_SCOPE, READ_USER_SCOPE)

  internal fun buildNewTokenUrl(serverUri: String): String? {
    val productName = ApplicationNamesInfo.getInstance().fullProductName

    return parseEncoded("${serverUri}/-/user_settings/personal_access_tokens")
      ?.addParameters(
        mapOf(
          "name" to "$productName $DEFAULT_CLIENT_NAME",
          "scopes" to MASTER_SCOPES.joinToString(",")
        )
      )
      ?.toExternalForm()
  }

  internal suspend fun validateAndResolveUsername(
    requiredUsername: String?,
    serverPath: GitLabServerPath,
    accessToken: String,
    uniqueAccountPredicate: (GitLabServerPath, String) -> Boolean,
  ): String {
    val api = service<GitLabApiManager>().getClient(serverPath, accessToken)
    val version = api.getMetadataOrNull()?.version
    val earliestSupportedVersion = serviceAsync<GitLabServersManager>().earliestSupportedVersion

    if (version == null) {
      throw LoginException.InvalidTokenOrUnsupportedServerVersion(earliestSupportedVersion.toString())
    }
    if (version < earliestSupportedVersion) {
      throw LoginException.UnsupportedServerVersion(earliestSupportedVersion.toString())
    }

    val user = withContext(Dispatchers.IO) {
      api.graphQL.getCurrentUser()
    }
    val username = user.username
    if (requiredUsername != null && username != requiredUsername) {
      throw LoginException.AccountUsernameMismatch(requiredUsername, username)
    }

    if (!uniqueAccountPredicate(serverPath, username)) {
      throw LoginException.AccountAlreadyExists(username)
    }

    return username
  }
}
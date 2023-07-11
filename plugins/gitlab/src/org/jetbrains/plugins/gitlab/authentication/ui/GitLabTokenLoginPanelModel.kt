// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.authentication.ui

import com.intellij.collaboration.auth.ui.login.LoginPanelModelBase
import com.intellij.collaboration.auth.ui.login.LoginTokenGenerator
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.util.URIUtil
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.components.service
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.gitlab.GitLabServersManager
import org.jetbrains.plugins.gitlab.api.GitLabApiManager
import org.jetbrains.plugins.gitlab.api.GitLabServerPath
import org.jetbrains.plugins.gitlab.api.request.getCurrentUser
import org.jetbrains.plugins.gitlab.authentication.GitLabSecurityUtil
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import org.jetbrains.plugins.gitlab.validateServerVersion

class GitLabTokenLoginPanelModel(var requiredUsername: String? = null,
                                 var uniqueAccountPredicate: (GitLabServerPath, String) -> Boolean)
  : LoginPanelModelBase(), LoginTokenGenerator {

  override suspend fun checkToken(): String {
    val server = createServerPath(serverUri)
    val api = service<GitLabApiManager>().getClient(token)
    val user = withContext(Dispatchers.IO) {
      api.graphQL.getCurrentUser(server)
    } ?: throw IllegalArgumentException(CollaborationToolsBundle.message("account.token.invalid"))

    service<GitLabServersManager>().validateServerVersion(server, api)

    val username = user.username
    if (requiredUsername != null) {
      require(username == requiredUsername) {
        GitLabBundle.message("account.username.mismatch", requiredUsername!!, username)
      }
    }

    require(uniqueAccountPredicate(server, username)) {
      GitLabBundle.message("account.not.unique", username)
    }
    return username
  }

  fun getServerPath(): GitLabServerPath = createServerPath(serverUri)

  private fun createServerPath(uri: String): GitLabServerPath {
    val normalized = URIUtil.normalizeAndValidateHttpUri(uri)
    return GitLabServerPath(normalized)
  }

  override fun canGenerateToken(serverUri: String): Boolean {
    return URIUtil.isValidHttpUri(serverUri)
  }

  override fun generateToken(serverUri: String) {
    val newTokenUrl = GitLabSecurityUtil.buildNewTokenUrl(serverUri) ?: return
    BrowserUtil.browse(newTokenUrl)
  }
}
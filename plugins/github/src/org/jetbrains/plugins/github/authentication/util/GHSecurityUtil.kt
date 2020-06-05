// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication.util

import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.util.Url
import com.intellij.util.Urls.newUrl
import org.jetbrains.plugins.github.api.*
import org.jetbrains.plugins.github.api.data.GithubAuthenticatedUser

object GHSecurityUtil {
  private const val REPO_SCOPE = "repo"
  private const val GIST_SCOPE = "gist"
  private const val READ_ORG_SCOPE = "read:org"
  val MASTER_SCOPES = listOf(REPO_SCOPE, GIST_SCOPE, READ_ORG_SCOPE)

  const val DEFAULT_CLIENT_NAME = "Github Integration Plugin"

  @JvmStatic
  internal fun loadCurrentUserWithScopes(executor: GithubApiRequestExecutor,
                                         progressIndicator: ProgressIndicator,
                                         server: GithubServerPath): Pair<GithubAuthenticatedUser, String?> {
    var scopes: String? = null
    val details = executor.execute(progressIndicator,
                                   object : GithubApiRequest.Get.Json<GithubAuthenticatedUser>(
                                     GithubApiRequests.getUrl(server,
                                                              GithubApiRequests.CurrentUser.urlSuffix),
                                     GithubAuthenticatedUser::class.java) {
                                     override fun extractResult(response: GithubApiResponse): GithubAuthenticatedUser {
                                       scopes = response.findHeader("X-OAuth-Scopes")
                                       return super.extractResult(response)
                                     }
                                   }.withOperationName("get profile information"))
    return details to scopes
  }

  @JvmStatic
  internal fun isEnoughScopes(grantedScopes: String): Boolean {
    val scopesArray = grantedScopes.split(", ")
    if (scopesArray.isEmpty()) return false
    if (!scopesArray.contains(REPO_SCOPE)) return false
    if (!scopesArray.contains(GIST_SCOPE)) return false
    if (scopesArray.none { it.endsWith(":org") }) return false

    return true
  }

  internal fun buildNewTokenUrl(server: GithubServerPath): String {
    val productName = ApplicationNamesInfo.getInstance().fullProductName

    return server
      .append("settings/tokens/new")
      .addParameters(mapOf(
        "description" to "$productName GitHub integration plugin",
        "scopes" to MASTER_SCOPES.joinToString(",")
      ))
      .toExternalForm()
  }

  private fun GithubServerPath.append(path: String): Url =
    newUrl(schema, host + port?.let { ":$it" }.orEmpty(), suffix.orEmpty() + "/" + path)
}
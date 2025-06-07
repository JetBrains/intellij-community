// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication.util

import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.util.Url
import com.intellij.util.Urls.newUrl
import org.jetbrains.plugins.github.api.GithubServerPath

object GHSecurityUtil {
  private const val REPO_SCOPE = "repo"
  private const val GIST_SCOPE = "gist"
  private const val READ_ORG_SCOPE = "read:org"
  private const val WORKFLOW_SCOPE = "workflow"
  private const val USER_READ_SCOPE = "read:user"
  private const val USER_EMAIL_SCOPE = "user:email"
  val MASTER_SCOPES = listOf(
    REPO_SCOPE,
    GIST_SCOPE,
    READ_ORG_SCOPE,
    WORKFLOW_SCOPE,
    USER_READ_SCOPE,
    USER_EMAIL_SCOPE
  )

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
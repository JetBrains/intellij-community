// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.authentication

import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.util.Urls.parseEncoded

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
}
// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.tasks

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.authentication.GHAccountsUtil
import org.jetbrains.plugins.github.authentication.GHLoginData
import org.jetbrains.plugins.github.authentication.GHLoginRequest
import org.jetbrains.plugins.github.authentication.GHLoginSource
import org.jetbrains.plugins.github.authentication.ui.GHLoginModel
import org.jetbrains.plugins.github.exceptions.GithubParseException

internal object GHRepositoryEditor {
  fun askToken(project: Project, host: String): String? {
    val server = tryParse(host) ?: return null

    val model = object : GHLoginModel {
      var token: String? = null

      override fun isAccountUnique(server: GithubServerPath, login: String): Boolean = true

      override suspend fun saveLogin(server: GithubServerPath, login: String, token: String) {
        this.token = token
      }
    }
    GHAccountsUtil.login(model, GHLoginRequest(server = server, loginData = GHLoginData(GHLoginSource.TRACKER)), project, null)
    return model.token
  }

  private fun tryParse(host: String): GithubServerPath? {
    return try {
      GithubServerPath.from(host)
    }
    catch (ignored: GithubParseException) {
      null
    }
  }
}
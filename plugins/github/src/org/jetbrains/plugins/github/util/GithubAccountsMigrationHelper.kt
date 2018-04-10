// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.util

import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import org.jetbrains.plugins.github.api.GithubApiTaskExecutor
import org.jetbrains.plugins.github.api.GithubApiUtil
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.api.GithubTask
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.accounts.GithubAccountManager

internal const val GITHUB_SETTINGS_PASSWORD_KEY = "GITHUB_SETTINGS_PASSWORD_KEY"

/**
 * Temporary helper
 * Will move single-account authorization data to accounts list if it was a token-based auth and clear old settings
 */
class GithubAccountsMigrationHelper internal constructor(private val settings: GithubSettings,
                                                         private val accountManager: GithubAccountManager,
                                                         private val passwordSafe: PasswordSafe) : StartupActivity {
  override fun runActivity(project: Project) {
    val login = settings.login
    val host = settings.host ?: GithubApiUtil.DEFAULT_GITHUB_HOST
    val authType = settings.authType

    if (accountManager.accounts.isEmpty() && login != null && authType != null) {
      when (authType) {
        GithubAuthData.AuthType.BASIC -> {
          passwordSafe.getPassword(GithubSettings::class.java, GITHUB_SETTINGS_PASSWORD_KEY)?.let { password ->
            object : Task.Backgroundable(project, "Acquiring Github Token") {
              private lateinit var token: String

              override fun run(indicator: ProgressIndicator) {
                token = GithubApiTaskExecutor.execute(indicator, GithubServerPath.from(host), login, password.toCharArray(),
                                                      GithubTask { GithubApiUtil.getMasterToken(it, GithubUtil.DEFAULT_TOKEN_NOTE) })
              }

              override fun onSuccess() {
                addAccount(login, host, token)
              }

              override fun onThrowable(error: Throwable) {
                GithubNotifications.showWarning(project, "Cannot Migrate Github Account $login",
                                                "Cannot acquire token for $host.\n${error.message}",
                                                GithubNotifications.getConfigureAction(project))
              }
            }.queue()
          }
        }
        GithubAuthData.AuthType.TOKEN -> {
          passwordSafe.getPassword(GithubSettings::class.java, GITHUB_SETTINGS_PASSWORD_KEY)?.let {
            addAccount(login, host, it)
          }
        }
        else -> {
        }
      }
    }
    clearOldAuth()
  }

  private fun addAccount(login: String, host: String, token: String) {
    val account = GithubAccount(login, GithubServerPath.from(host))
    accountManager.accounts += account
    accountManager.updateAccountToken(account, token)
  }

  private fun clearOldAuth() {
    settings.clearAuth()
    passwordSafe.setPassword(GithubSettings::class.java, GITHUB_SETTINGS_PASSWORD_KEY, null)
  }
}

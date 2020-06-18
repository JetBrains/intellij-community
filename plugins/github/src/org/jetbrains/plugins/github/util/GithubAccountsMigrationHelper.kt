// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.util

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ThrowableComputable
import git4idea.DialogManager
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubApiRequests
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.accounts.GithubAccountManager
import org.jetbrains.plugins.github.authentication.ui.GithubLoginDialog
import org.jetbrains.plugins.github.i18n.GithubBundle
import java.awt.Component
import java.io.IOException

internal const val GITHUB_SETTINGS_PASSWORD_KEY = "GITHUB_SETTINGS_PASSWORD_KEY"

/**
 * Temporary helper
 * Will move single-account authorization data to accounts list if it was a token-based auth and clear old settings
 */
@Suppress("DEPRECATION")
class GithubAccountsMigrationHelper {
  private val LOG = logger<GithubAccountsMigrationHelper>()

  internal fun getOldServer(): GithubServerPath? {
    if (!GithubSettings.getInstance().hasOldAccount) return null

    val host = GithubSettings.getInstance().host ?: return GithubServerPath.DEFAULT_SERVER
    return runCatching { GithubServerPath.from(host) }.getOrNull() // could be called from AnAction.update()
  }

  private val GithubSettings.hasOldAccount: Boolean
    get() = authType == GithubAuthData.AuthType.BASIC && login != null ||
            authType == GithubAuthData.AuthType.TOKEN

  /**
   * @return false if process was cancelled by user, true otherwise
   */
  @CalledInAwt
  @JvmOverloads
  fun migrate(project: Project, parentComponent: Component? = null): Boolean {
    LOG.debug("Migrating old auth")
    val settings = GithubSettings.getInstance()
    val login = settings.login
    val host = settings.host
    val password = PasswordSafe.instance.getPassword(CredentialAttributes(GithubSettings::class.java, GITHUB_SETTINGS_PASSWORD_KEY))
    val authType = settings.authType
    LOG.debug("Old auth data: { login: $login, host: $host, authType: $authType, password null: ${password == null} }")

    if (login == null && host == null && authType == null && password == null) return true
    if (service<GithubAccountManager>().accounts.isNotEmpty()) return true

    val hostToUse = host ?: GithubServerPath.DEFAULT_HOST
    when (authType) {
      GithubAuthData.AuthType.TOKEN -> {
        LOG.debug("Migrating token auth")
        if (password == null) return true

        val executorFactory = GithubApiRequestExecutor.Factory.getInstance()
        try {
          val server = GithubServerPath.from(hostToUse)
          val progressManager = ProgressManager.getInstance()
          val accountName = progressManager.runProcessWithProgressSynchronously(ThrowableComputable<String, IOException> {
            executorFactory.create(password).execute(progressManager.progressIndicator, GithubApiRequests.CurrentUser.get(server)).login
          }, GithubBundle.message("accessing.github"), true, project)
          val account = GithubAccountManager.createAccount(accountName, server)
          registerAccount(account, password)
          return true
        }
        catch (e: Exception) {
          LOG.debug("Failed to migrate old token-based auth. Showing dialog.", e)
          val dialog = GithubLoginDialog(executorFactory, project, parentComponent)
            .withServer(hostToUse, false).withToken(password).withError(e)
          return dialog.registerAccount()
        }
      }
      GithubAuthData.AuthType.BASIC -> {
        LOG.debug("Migrating basic auth")
        val dialog = GithubLoginDialog(GithubApiRequestExecutor.Factory.getInstance(), project, parentComponent,
                                       message = GithubBundle.message("accounts.password.auth.not.supported"))
          .withServer(hostToUse, false).withCredentials(login, password)
        return dialog.registerAccount()
      }
      else -> return true
    }
  }

  private fun GithubLoginDialog.registerAccount(): Boolean {
    DialogManager.show(this)
    if (!isOK) return false

    registerAccount(GithubAccountManager.createAccount(login, server), token)
    return true
  }

  private fun registerAccount(account: GithubAccount, token: String) {
    val accountManager = service<GithubAccountManager>()
    accountManager.accounts += account
    accountManager.updateAccountToken(account, token)
    LOG.debug("Registered account $account")
  }

  companion object {
    @JvmStatic
    fun getInstance(): GithubAccountsMigrationHelper = service()
  }
}

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
import org.jetbrains.plugins.github.api.data.GithubAuthenticatedUser
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.accounts.GithubAccountManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccountManager.Companion.createAccount
import org.jetbrains.plugins.github.authentication.ui.GithubLoginDialog
import org.jetbrains.plugins.github.i18n.GithubBundle
import java.awt.Component
import java.io.IOException

private val LOG = logger<GithubAccountsMigrationHelper>()

internal const val GITHUB_SETTINGS_PASSWORD_KEY = "GITHUB_SETTINGS_PASSWORD_KEY"

/**
 * Temporary helper
 * Will move single-account authorization data to accounts list if it was a token-based auth and clear old settings
 */
@Suppress("DEPRECATION")
class GithubAccountsMigrationHelper {
  internal fun getOldServer(): GithubServerPath? {
    if (!GithubSettings.getInstance().hasOldAccount) return null

    val host = GithubSettings.getInstance().host ?: return GithubServerPath.DEFAULT_SERVER
    return runCatching { GithubServerPath.from(host) }.getOrNull() // could be called from AnAction.update()
  }

  private val GithubSettings.hasOldAccount: Boolean
    get() = authType == GithubAuthData.AuthType.BASIC && login != null ||
            authType == GithubAuthData.AuthType.TOKEN

  private val GithubSettings.oldHost: String get() = host ?: GithubServerPath.DEFAULT_HOST
  private val GithubSettings.hasAuthDetails: Boolean get() = login != null || host != null || authType != null
  private val GithubSettings.authDetails: String get() = "login: $login, host: $host, authType: $authType"

  /**
   * @return false if process was cancelled by user, true otherwise
   */
  @CalledInAwt
  @JvmOverloads
  fun migrate(project: Project, parentComponent: Component? = null): Boolean {
    LOG.debug("Migrating old auth")

    val settings = GithubSettings.getInstance()
    val password = PasswordSafe.instance.getPassword(CredentialAttributes(GithubSettings::class.java, GITHUB_SETTINGS_PASSWORD_KEY))
    LOG.debug("Old auth data: { ${settings.authDetails}, password null: ${password == null} }")

    if (!settings.hasAuthDetails && password == null) return true
    if (service<GithubAccountManager>().accounts.isNotEmpty()) return true

    val migrationResult = when (settings.authType) {
      GithubAuthData.AuthType.TOKEN -> migrateTokenAuth(project, parentComponent, password)
      GithubAuthData.AuthType.BASIC -> migrateBasicAuth(project, parentComponent, password)
      else -> NO_ACCOUNT_MIGRATED
    }

    migrationResult?.registerAccount()
    return migrationResult != null
  }

  private fun migrateTokenAuth(project: Project, parentComponent: Component?, token: String?): MigrationResult? {
    LOG.debug("Migrating token auth")

    if (token == null) return NO_ACCOUNT_MIGRATED

    return runCatching { migrateWithExistingToken(project, token) }
      .recover { migrateWithNewToken(project, parentComponent, token, it) }
      .getOrNull()
  }

  private fun migrateWithExistingToken(project: Project, token: String): MigrationResult {
    val server = GithubServerPath.from(GithubSettings.getInstance().oldHost)
    val progressManager = ProgressManager.getInstance()
    val accountDetails = progressManager.runProcessWithProgressSynchronously(
      ThrowableComputable<GithubAuthenticatedUser, IOException> {
        GithubApiRequestExecutor.Factory.getInstance().create(token).execute(
          progressManager.progressIndicator,
          GithubApiRequests.CurrentUser.get(server)
        )
      },
      GithubBundle.message("accessing.github"),
      true,
      project
    )

    return MigrationResult(createAccount(accountDetails.login, server), token)
  }

  private fun migrateWithNewToken(project: Project, parentComponent: Component?, token: String?, error: Throwable): MigrationResult? {
    LOG.debug("Failed to migrate old token-based auth. Showing dialog.", error)

    val dialog = GithubLoginDialog(GithubApiRequestExecutor.Factory.getInstance(), project, parentComponent)
      .withServer(GithubSettings.getInstance().oldHost, false)
      .withToken(token)
      .withError(error)

    return dialog.getMigrationResult()
  }

  private fun migrateBasicAuth(project: Project, parentComponent: Component?, password: String?): MigrationResult? {
    LOG.debug("Migrating basic auth")

    val dialog = GithubLoginDialog(
      GithubApiRequestExecutor.Factory.getInstance(), project, parentComponent,
      message = GithubBundle.message("accounts.password.auth.not.supported")
    ).apply {
      withServer(GithubSettings.getInstance().oldHost, false)
      withLogin(GithubSettings.getInstance().login, true)
      withPassword(password)
    }

    return dialog.getMigrationResult()
  }

  companion object {
    @JvmStatic
    fun getInstance(): GithubAccountsMigrationHelper = service()
  }
}

private val NO_ACCOUNT_MIGRATED = MigrationResult(null, null)

private class MigrationResult(val account: GithubAccount?, val token: String?)

private fun MigrationResult.registerAccount() {
  if (account == null) return

  GithubAuthenticationManager.getInstance().registerAccount(account, token!!)
    .also { LOG.debug("Registered account $it") }
}

private fun GithubLoginDialog.getMigrationResult(): MigrationResult? {
  DialogManager.show(this)

  return if (isOK) MigrationResult(createAccount(login, server), token) else null
}
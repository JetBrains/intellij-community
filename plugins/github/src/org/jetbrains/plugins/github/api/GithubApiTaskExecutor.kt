// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api

import com.intellij.openapi.application.invokeAndWaitIfNeed
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.ui.Messages
import com.intellij.util.ThrowableConvertor
import org.jetbrains.annotations.CalledInBackground
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.exceptions.GithubMissingTokenException
import org.jetbrains.plugins.github.exceptions.GithubOperationCanceledException
import org.jetbrains.plugins.github.exceptions.GithubTwoFactorAuthenticationException
import org.jetbrains.plugins.github.util.GithubAuthData
import org.jetbrains.plugins.github.util.GithubUtil
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.ScheduledFuture

typealias GithubTask<T> = ThrowableConvertor<GithubConnection, T, IOException>

/**
 * Performs Github API requests taking care of cancellation and authentication
 */
class GithubApiTaskExecutor(private val authenticationManager: GithubAuthenticationManager) {

  /**
   * Run github task using saved OAuth token to authenticate
   * If [silentFailOnMissingToken] is true - throw [GithubMissingTokenException] when token is missing, otherwise - ask user to provide one
   * TODO: Share connection between requests
   */
  @CalledInBackground
  @JvmOverloads
  @Throws(IOException::class)
  fun <T> execute(indicator: ProgressIndicator, account: GithubAccount, task: GithubTask<T>, silentFailOnMissingToken: Boolean = false): T {
    val token = if (silentFailOnMissingToken) {
      authenticationManager.getTokenForAccount(account) ?: throw GithubMissingTokenException(account)
    }
    else {
      authenticationManager.getOrRequestTokenForAccount(account, modalityStateSupplier = { indicator.modalityState })
      ?: throw ProcessCanceledException(GithubMissingTokenException(account))
    }

    return CancellableGithubConnection(indicator, GithubAuthData.createTokenAuth(account.server.toString(), token))
      .apply { setAccount(account) }
      .use(task::convert)
  }

  @CalledInBackground
  @TestOnly
  @Throws(IOException::class)
  fun <T> execute(account: GithubAccount, task: GithubTask<T>): T = execute(EmptyProgressIndicator(), account, task, true)

  companion object {
    /**
     * Run one-time github task without authentication
     */
    @CalledInBackground
    @JvmStatic
    @Throws(IOException::class)
    fun <T> execute(indicator: ProgressIndicator, server: GithubServerPath, task: GithubTask<T>): T {
      return CancellableGithubConnection(indicator, GithubAuthData.createAnonymous(server.toString())).use(task::convert)
    }

    /**
     * Run one-time github task using OAuth token to authenticate
     */
    @CalledInBackground
    @JvmStatic
    @Throws(IOException::class)
    fun <T> execute(indicator: ProgressIndicator, server: GithubServerPath, token: String, task: GithubTask<T>): T {
      return CancellableGithubConnection(indicator, GithubAuthData.createTokenAuth(server.toString(), token)).use(task::convert)
    }

    /**
     * Run one-time github task with login and password
     */
    @CalledInBackground
    @JvmStatic
    @Throws(IOException::class)
    fun <T> execute(indicator: ProgressIndicator, server: GithubServerPath, login: String, password: CharArray, task: GithubTask<T>): T {
      val basicAuth = GithubAuthData.createBasicAuth(server.toString(), login, String(password))
      CancellableGithubConnection(indicator, basicAuth).use { connection ->
        return try {
          task.convert(connection)
        }
        catch (e: GithubTwoFactorAuthenticationException) {
          val twoFactorAuthData = getTwoFactorAuthData(indicator, basicAuth, connection)
          CancellableGithubConnection(indicator, twoFactorAuthData).use(task::convert)
        }
      }
    }

    private fun getTwoFactorAuthData(indicator: ProgressIndicator,
                                     originalAuth: GithubAuthData,
                                     connection: GithubConnection): GithubAuthData {
      GithubApiUtil.askForTwoFactorCodeSMS(connection)
      val code = invokeAndWaitIfNeed(indicator.modalityState) {
        Messages.showInputDialog(null,
                                 "Authentication Code",
                                 "Github Two-Factor Authentication",
                                 null)
      }

      if (code == null) throw GithubOperationCanceledException("Can't get two factor authentication code")

      return originalAuth.copyWithTwoFactorCode(code)
    }

    @JvmStatic
    fun getInstance(): GithubApiTaskExecutor {
      return service()
    }
  }
}

private class CancellableGithubConnection(indicator: ProgressIndicator, authData: GithubAuthData)
  : GithubConnection(authData, true), Closeable {

  val cancellationFuture = scheduleCancellationListener(indicator, this)

  override fun close() {
    super.close()
    cancellationFuture.cancel(true)
  }

  companion object {
    private fun scheduleCancellationListener(indicator: ProgressIndicator, connection: GithubConnection): ScheduledFuture<*> {
      return GithubUtil.addCancellationListener({ if (indicator.isCanceled) connection.abort() })
    }
  }
}
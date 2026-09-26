// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.remote.hosting.http

import com.intellij.collaboration.auth.AccountManager
import com.intellij.collaboration.auth.ServerAccount
import com.intellij.util.AuthData
import org.jetbrains.annotations.ApiStatus

@Deprecated("Use SilentHostedGitHttpAuthDataProviderBase instead",
            replaceWith = ReplaceWith("SilentHostedGitHttpAuthDataProviderBase<A, String>",
                                      "git4idea.remote.hosting.http.SilentHostedGitHttpAuthDataProviderBase"))
@ApiStatus.Experimental
abstract class SilentHostedGitHttpAuthDataProvider<A : ServerAccount> : SilentHostedGitHttpAuthDataProviderBase<A, String>() {
  /**
   * Account manager that holds accounts and their credentials.
   *
   * In common, it is an application service.
   */
  abstract override val accountManager: AccountManager<A, String>

  /**
   * Provides login that will be passed to git remote http operation.
   * Or `null` if login cannot be acquired, in this case nothing will be passed to git http remote operations and
   * user will be asked to input username and password themselves.
   */
  abstract suspend fun getAccountLogin(account: A, credentials: String): String?

  final override suspend fun getAuthData(account: A): AuthData? {
    val token = accountManager.findCredentials(account) ?: return null
    val login = getAccountLogin(account, token) ?: return null
    return AuthData(login, token)
  }
}
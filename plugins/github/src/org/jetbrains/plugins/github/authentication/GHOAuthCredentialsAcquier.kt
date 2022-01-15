// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication

import com.intellij.collaboration.auth.credentials.Credentials
import com.intellij.collaboration.auth.credentials.SimpleCredentials
import com.intellij.collaboration.auth.services.OAuthCredentialsAcquirer
import com.intellij.collaboration.auth.services.OAuthCredentialsAcquirerHttp
import com.intellij.util.Url

internal class GHOAuthCredentialsAcquirer(
  private val codeVerifier: String
) : OAuthCredentialsAcquirer<Credentials> {
  override fun acquireCredentials(code: String): OAuthCredentialsAcquirer.AcquireCredentialsResult<Credentials> {
    val tokenUrl = ACCESS_TOKEN_URL.addParameters(mapOf(
      "code" to code,
      "code_verifier" to codeVerifier
    ))

    return OAuthCredentialsAcquirerHttp.requestToken(tokenUrl) { _, headers ->
      SimpleCredentials(headers.firstValue("X-OAuth-Token").get())
    }
  }

  companion object {
    private val ACCESS_TOKEN_URL: Url
      get() = GHOAuthService.SERVICE_URL.resolve("access_token")
  }
}
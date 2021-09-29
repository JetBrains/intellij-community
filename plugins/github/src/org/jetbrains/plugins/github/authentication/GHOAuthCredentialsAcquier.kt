// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication

import com.intellij.collaboration.auth.credentials.Credentials
import com.intellij.collaboration.auth.credentials.SimpleCredentials
import com.intellij.collaboration.auth.services.OAuthCredentialsAcquirerBase
import com.intellij.util.Url
import java.net.http.HttpHeaders

internal class GHOAuthCredentialsAcquirer(
  private val codeVerifier: String
) : OAuthCredentialsAcquirerBase<Credentials>() {
  override fun getTokenUrlWithParameters(code: String): Url {
    return ACCESS_TOKEN_URL.addParameters(mapOf(
      "code" to code,
      "code_verifier" to codeVerifier
    ))
  }

  override fun getCredentials(responseBody: String, responseHeaders: HttpHeaders): Credentials {
    return SimpleCredentials(responseHeaders.firstValue("X-OAuth-Token").get())
  }

  companion object {
    private val ACCESS_TOKEN_URL: Url
      get() = GHOAuthService.SERVICE_URL.resolve("access_token")
  }
}
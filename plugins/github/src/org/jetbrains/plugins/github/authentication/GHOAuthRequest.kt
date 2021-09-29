// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication

import com.intellij.collaboration.auth.services.OAuthPKCERequestBase
import com.intellij.util.Url
import com.intellij.util.Urls
import org.jetbrains.ide.BuiltInServerManager
import org.jetbrains.ide.RestService

internal class GHOAuthRequest : OAuthPKCERequestBase() {
  private val port: Int get() = BuiltInServerManager.getInstance().port

  override val authorizationCodeUrl: Url
    get() = Urls.newFromEncoded("http://127.0.0.1:$port/${RestService.PREFIX}/${GHOAuthService.instance.name}/authorization_code")

  override fun getAuthUrlWithParameters(): Url = AUTHORIZE_URL.addParameters(mapOf(
    "code_challenge" to generateCodeChallenge(false),
    "callback_url" to authorizationCodeUrl.toExternalForm()
  ))

  override fun getTokenUrlWithParameters(code: String): Url = ACCESS_TOKEN_URL.addParameters(mapOf(
    "code" to code,
    "code_verifier" to codeVerifier
  ))

  companion object {
    private val AUTHORIZE_URL: Url
      get() = GHOAuthService.SERVICE_URL.resolve("authorize")
    private val ACCESS_TOKEN_URL: Url
      get() = GHOAuthService.SERVICE_URL.resolve("access_token")
  }
}
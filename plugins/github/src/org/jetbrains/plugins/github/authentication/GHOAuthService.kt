// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication

import com.intellij.collaboration.auth.credentials.Credentials
import com.intellij.collaboration.auth.credentials.SimpleCredentials
import com.intellij.collaboration.auth.services.OAuthServiceBase
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.util.Url
import com.intellij.util.Urls.newFromEncoded
import com.intellij.util.io.DigestUtil.randomToken
import com.intellij.util.io.DigestUtil.sha256
import org.jetbrains.ide.BuiltInServerManager
import org.jetbrains.ide.RestService
import java.net.http.HttpHeaders
import java.util.*

@Service
internal class GHOAuthService : OAuthServiceBase<Credentials>() {
  private val codeVerifier: String = randomToken()
  private val codeChallenge: String get() = Base64.getEncoder().encodeToString(sha256().digest(codeVerifier.toByteArray()))
  private val port: Int get() = BuiltInServerManager.getInstance().port

  override val name: String get() = "github/oauth"
  override val authorizationCodeUrl: Url get() = newFromEncoded("http://127.0.0.1:$port/${RestService.PREFIX}/$name/authorization_code")
  override val successRedirectUrl: Url get() = SERVICE_URL.resolve("complete")
  override val errorRedirectUrl: Url get() = SERVICE_URL.resolve("error")

  override fun revokeToken(token: String) {
    TODO("Not yet implemented")
  }

  override fun getAuthUrlWithParameters(): Url = AUTHORIZE_URL.addParameters(mapOf(
    "code_challenge" to codeChallenge,
    "callback_url" to authorizationCodeUrl.toExternalForm()
  ))

  override fun getCredentials(responseBody: String, responseHeaders: HttpHeaders): Credentials =
    SimpleCredentials(responseHeaders.firstValue("X-OAuth-Token").get())

  override fun getTokenUrlWithParameters(code: String): Url = ACCESS_TOKEN_URL.addParameters(mapOf(
    "code" to code,
    "code_verifier" to codeVerifier
  ))

  companion object {
    val instance: GHOAuthService
      get() = service()

    private val SERVICE_URL: Url = newFromEncoded("https://account.jetbrains.com/github/oauth/intellij")

    val AUTHORIZE_URL: Url
      get() = SERVICE_URL.resolve("authorize")
    val ACCESS_TOKEN_URL: Url
      get() = SERVICE_URL.resolve("access_token")
  }
}

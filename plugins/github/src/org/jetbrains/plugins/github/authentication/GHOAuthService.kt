// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication

import com.intellij.collaboration.auth.credentials.Credentials
import com.intellij.collaboration.auth.services.OAuthCredentialsAcquirer
import com.intellij.collaboration.auth.services.OAuthRequest
import com.intellij.collaboration.auth.services.OAuthServiceBase
import com.intellij.collaboration.auth.services.PkceUtils
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.util.Url
import com.intellij.util.Urls.newFromEncoded
import org.jetbrains.ide.BuiltInServerManager
import org.jetbrains.ide.RestService
import java.util.*
import java.util.concurrent.CompletableFuture

@Service
internal class GHOAuthService : OAuthServiceBase<Credentials>() {
  override val name: String get() = SERVICE_NAME

  fun authorize(): CompletableFuture<Credentials> {
    return authorize(GHOAuthRequest())
  }

  override fun revokeToken(token: String) {
    TODO("Not yet implemented")
  }

  private class GHOAuthRequest : OAuthRequest<Credentials> {
    private val port: Int get() = BuiltInServerManager.getInstance().port

    private val codeVerifier = PkceUtils.generateCodeVerifier()

    private val codeChallenge = PkceUtils.generateShaCodeChallenge(codeVerifier, Base64.getEncoder())

    override val authorizationCodeUrl: Url
      get() = newFromEncoded("http://127.0.0.1:$port/${RestService.PREFIX}/$SERVICE_NAME/authorization_code")

    override val credentialsAcquirer: OAuthCredentialsAcquirer<Credentials> = GHOAuthCredentialsAcquirer(codeVerifier)

    override val authUrlWithParameters: Url = AUTHORIZE_URL.addParameters(mapOf(
      "code_challenge" to codeChallenge,
      "callback_url" to authorizationCodeUrl.toExternalForm()
    ))

    companion object {
      private val AUTHORIZE_URL: Url
        get() = SERVICE_URL.resolve("authorize")
    }
  }

  companion object {
    private const val SERVICE_NAME = "github/oauth"

    val instance: GHOAuthService
      get() = service()

    val SERVICE_URL: Url = newFromEncoded("https://account.jetbrains.com/github/oauth/intellij")
  }
}

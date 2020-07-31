// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication

import com.intellij.ide.BrowserUtil.browse
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.Url
import com.intellij.util.Urls.newFromEncoded
import com.intellij.util.io.DigestUtil.randomToken
import com.intellij.util.io.DigestUtil.sha256
import com.intellij.util.io.HttpRequests
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.CancellablePromise
import org.jetbrains.concurrency.rejectedCancellablePromise
import org.jetbrains.plugins.github.authentication.GHOAuthCallbackHandler.Companion.authorizationCodeUrl
import org.jetbrains.plugins.github.authentication.GHOAuthService.Companion.ACCESS_TOKEN_URL
import org.jetbrains.plugins.github.authentication.GHOAuthService.Companion.AUTHORIZE_URL
import java.io.IOException
import java.util.*
import java.util.concurrent.atomic.AtomicReference

internal fun isOAuthEnabled(): Boolean = Registry.`is`("github.use.oauth")

@Service
internal class GHOAuthService {
  private val currentRequest = AtomicReference<GHTokenRequest?>()

  fun requestToken(): CancellablePromise<String> {
    if (!currentRequest.compareAndSet(null, GHTokenRequest())) return rejectedCancellablePromise("Already running")

    val request = currentRequest.get()!!
    request.onError { } // not to log exceptions as errors
    request.onProcessed { currentRequest.set(null) }
    request.startAuthorization()
    return request
  }

  fun acceptCode(code: String): Boolean {
    val request = currentRequest.get() ?: return false

    request.processCode(code)
    return request.isSucceeded
  }

  companion object {
    val instance: GHOAuthService = service()

    private val SERVICE_URL: Url = newFromEncoded("https://account.jetbrains.com/github/oauth/intellij")
    val AUTHORIZE_URL: Url get() = SERVICE_URL.resolve("authorize")
    val ACCESS_TOKEN_URL: Url get() = SERVICE_URL.resolve("access_token")
    val SUCCESS_URL: Url get() = SERVICE_URL.resolve("complete")
    val ERROR_URL: Url get() = SERVICE_URL.resolve("error")
  }
}

private class GHTokenRequest : AsyncPromise<String>() {
  private val codeVerifier: String = randomToken()
  private val codeChallenge: String get() = Base64.getEncoder().encodeToString(sha256().digest(codeVerifier.toByteArray()))

  fun startAuthorization() {
    val authorizeUrl = AUTHORIZE_URL.addParameters(mapOf(
      "code_challenge" to codeChallenge,
      "callback_url" to authorizationCodeUrl.toExternalForm()
    ))

    browse(authorizeUrl.toExternalForm())
  }

  fun processCode(code: String) {
    try {
      val token = acquireToken(code)
      if (token != null) setResult(token) else setError("No token provided")
    }
    catch (e: IOException) {
      setError(e)
    }
  }

  private fun acquireToken(code: String): String? {
    val tokenUrl = ACCESS_TOKEN_URL.addParameters(mapOf(
      "code" to code,
      "code_verifier" to codeVerifier
    ))

    return HttpRequests.post(tokenUrl.toExternalForm(), null).connect { it.getToken() }
  }
}

private fun HttpRequests.Request.getToken(): String? = connection.getHeaderField("X-OAuth-Token")
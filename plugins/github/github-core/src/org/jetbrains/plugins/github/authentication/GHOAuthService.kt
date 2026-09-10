// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.authentication

import com.intellij.collaboration.auth.credentials.Credentials
import com.intellij.collaboration.auth.services.OAuthCredentialsAcquirer
import com.intellij.collaboration.auth.services.OAuthRequest
import com.intellij.collaboration.auth.services.OAuthServiceBase
import com.intellij.collaboration.auth.services.PkceUtils
import com.intellij.collaboration.util.resolveRelative
import com.intellij.collaboration.util.withQuery
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.Url
import com.intellij.util.Urls.newFromEncoded
import com.intellij.util.io.DigestUtil
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http.QueryStringDecoder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.asDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.ide.BuiltInServerManager
import org.jetbrains.ide.RestService
import org.jetbrains.ide.RestService.Companion.sendResponse
import org.jetbrains.io.response
import org.jetbrains.plugins.github.api.GithubApiContentHelper
import org.jetbrains.plugins.github.api.GithubApiRequest
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.api.util.GithubApiUrlQueryBuilder
import org.jetbrains.plugins.github.authentication.util.GHSecurityUtil
import org.jetbrains.plugins.github.i18n.GithubBundle
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

private const val GITHUB_OAUTH_CLIENT_ID = "58566862bd2a5ff748fb"
private const val GITHUB_OAUTH_CLIENT_SECRET = "5851e458e4836ca209f0f9e26ab2e143bb7806fb"

private const val CODE_CHALLENGE_METHOD = "S256"
private const val SERVICE_NAME = "github/pkce"
private const val OLD_SERVICE_NAME = "github/oauth"

private const val RESPONSE_PAGE_PATH = "html/oauth-response.html"
private const val MESSAGE_PLACEHOLDER = "{{message}}"
private const val STATUS_CLASS_PLACEHOLDER = "{{statusClass}}"
private const val FALLBACK_RESPONSE_PAGE = """<!DOCTYPE html>
        <html lang="en">
          <head><meta charset="utf-8"><title>JetBrains</title><style>
          body {
            margin: 0; padding: 24px; background: #fff; font-size: 1.5em;
            font-family: system-ui;
          }
          .status.success { color: #2e7d32; }
          .status.failure { color: #c7222d; }
          </style></head>
          <body><p class="status $STATUS_CLASS_PLACEHOLDER">$MESSAGE_PLACEHOLDER</p></body>
        </html>"""

@Service(Service.Level.APP)
internal class GHOAuthService(cs: CoroutineScope) {
  val pkceImpl = GHOAuthServicePkceImpl(cs)
  val oldImpl = GHOAuthServiceOldImpl()

  suspend fun authorize(): String =
    if (Registry.`is`("github.pkce.oauth")) pkceImpl.authorize() else oldImpl.acquireToken()

  companion object {
    val instance: GHOAuthService
      get() = service()
  }
}

internal class GHOAuthServicePkceImpl(private val cs: CoroutineScope) {
  private val responsePageTemplate by lazy { loadResponsePageTemplate() }
  private val pendingLogins = ConcurrentHashMap<String, PendingLoginWithRequest>()

  private val redirectUri
    get() = "http://127.0.0.1:${BuiltInServerManager.getInstance().port}/${RestService.PREFIX}/$SERVICE_NAME"

  suspend fun authorize(): String {
    val state = DigestUtil.randomToken()
    val codeVerifier = ByteArray(32).also { DigestUtil.random.nextBytes(it) }.let {
      Base64.getUrlEncoder().withoutPadding().encodeToString(it)
    }
    val request = CompletableDeferred<String>()

    pendingLogins[state] = PendingLoginWithRequest(codeVerifier, request)
    try {
      BrowserUtil.browse(buildAuthorizationURI(codeVerifier, state))
      return request.await()
    }
    catch (ce: CancellationException) {
      request.cancel(ce)
      throw ce
    }
    finally {
      pendingLogins.remove(state)
    }
  }

  private fun buildAuthorizationURI(codeVerifier: String, state: String): URI {
    val codeChallenge = DigestUtil.sha256().digest(codeVerifier.toByteArray()).let {
      Base64.getUrlEncoder().withoutPadding().encodeToString(it)
    }
    return GithubServerPath.DEFAULT_SERVER.toURI().resolveRelative("login/oauth/authorize").withQuery(GithubApiUrlQueryBuilder.urlQuery {
      param("client_id", GITHUB_OAUTH_CLIENT_ID)
      param("redirect_uri", redirectUri)
      param("scope", GHSecurityUtil.MASTER_SCOPES.joinToString(" "))
      param("state", state)
      param("code_challenge", codeChallenge)
      param("code_challenge_method", CODE_CHALLENGE_METHOD)
    }.removePrefix("?"))
  }

  private suspend fun exchangeCodeForToken(code: String, codeVerifier: String): String {
    val requestBody = GHOAuthTokenRequest(clientId = GITHUB_OAUTH_CLIENT_ID,
                                          clientSecret = GITHUB_OAUTH_CLIENT_SECRET,
                                          grantType = "authorization_code",
                                          code = code,
                                          redirectUri = redirectUri,
                                          codeVerifier = codeVerifier)
    val request =
      GithubApiRequest.Post.json<GHOAuthTokenResponse>(GithubServerPath.DEFAULT_SERVER.toURI().resolveRelative("login/oauth/access_token")
                                                         .toString(), requestBody, GithubApiContentHelper.JSON_MIME_TYPE)
    return withContext(Dispatchers.IO) {
      GithubApiRequestExecutor.Factory.getInstance().create().execute(request)
    }.accessToken
  }

  private fun loadResponsePageTemplate(): String {
    val stream = GHOAuthService::class.java.classLoader.getResourceAsStream(RESPONSE_PAGE_PATH)
    return try {
      stream?.use { it.readAllBytes().decodeToString() } ?: FALLBACK_RESPONSE_PAGE
    }
    catch (_: IOException) {
      FALLBACK_RESPONSE_PAGE
    }
  }

  //region CallbackHandler
  class GHOAuthCallbackHandler : RestService() {
    override fun getServiceName() = SERVICE_NAME

    override fun execute(
      urlDecoder: QueryStringDecoder,
      request: FullHttpRequest,
      context: ChannelHandlerContext,
    ): String? {
      val parameters = urlDecoder.parameters()
      val result = AuthorizationResult.of(parameters["result"]?.firstOrNull(), parameters["message"]?.firstOrNull())
      if (result != null) {
        // Landing page of the redirect sent by sendResultRedirect: rendered from these parameters, so
        // reloading it or navigating back to it keeps reporting the same outcome
        sendHTMLResponse(context, request, buildResponsePage(result))
        return null
      }
      GHOAuthService.instance.pkceImpl.onCallback(
        parameters["state"]?.firstOrNull(),
        parameters["code"]?.firstOrNull(),
        parameters["error"]?.firstOrNull(),
        parameters["error_description"]?.firstOrNull(),
        context,
        request,
      )
      return null
    }

    private fun sendHTMLResponse(context: ChannelHandlerContext, request: FullHttpRequest, html: String) {
      val resp = response("text/html", Unpooled.wrappedBuffer(html.toByteArray())).apply {
        headers().set("Referrer-Policy", "no-referrer")
      }
      sendResponse(request, context, resp)
    }

    private fun buildResponsePage(result: AuthorizationResult): String =
      GHOAuthService.instance.pkceImpl.responsePageTemplate.replace(STATUS_CLASS_PLACEHOLDER, result.value)
        .replace(MESSAGE_PLACEHOLDER, StringUtil.escapeXmlEntities(result.message))
  }

  private fun onCallback(
    state: String?,
    code: String?,
    error: String?,
    errorDescription: String?,
    context: ChannelHandlerContext,
    httpRequest: FullHttpRequest,
  ) {
    val pending = state?.let { pendingLogins.remove(it) }
    when (val decision = decideCallback(pending != null, code, error, errorDescription)) {
      GHOAuthCallbackDecision.UnknownState -> {
        sendResultRedirect(context, httpRequest, AuthorizationResult.Failure())
      }
      is GHOAuthCallbackDecision.Failure -> {
        val message = decision.reason?.let { GithubBundle.message("oauth.error.authorization.failed.with.reason", it) }
                      ?: GithubBundle.message("oauth.error.authorization.failed")
        pending?.request?.completeExceptionally(GHOAuthFlowException(message))
        sendResultRedirect(context, httpRequest, AuthorizationResult.Failure(message))
      }
      is GHOAuthCallbackDecision.Exchange -> {
        val login = pending ?: return
        cs.launch {
          val result = try {
            login.request.complete(exchangeCodeForToken(decision.code, login.codeVerifier))
            AuthorizationResult.Success
          }
          catch (ce: CancellationException) {
            login.request.cancel(ce)
            throw ce
          }
          catch (e: Exception) {
            val message = GithubBundle.message("oauth.error.token.exchange.failed")
            login.request.completeExceptionally(GHOAuthFlowException(message, e))
            AuthorizationResult.Failure(message)
          }
          sendResultRedirect(context, httpRequest, result)
        }
      }
    }
  }

  private fun decideCallback(
    stateIsKnown: Boolean,
    code: String?,
    error: String?,
    errorDescription: String?,
  ): GHOAuthCallbackDecision = when {
    !stateIsKnown -> GHOAuthCallbackDecision.UnknownState
    error != null -> GHOAuthCallbackDecision.Failure(errorDescription ?: error)
    code == null -> GHOAuthCallbackDecision.Failure(null)
    else -> GHOAuthCallbackDecision.Exchange(code)
  }

  /**
   * Answers the callback with a redirect to a code-free result URL, so that navigating back to it or reloading re-renders
   * the same outcome instead of re-entering the callback handler with a code that has already been used.
   */
  private fun sendResultRedirect(context: ChannelHandlerContext, request: FullHttpRequest, result: AuthorizationResult) {
    val location = buildString {
      append("/${RestService.PREFIX}/$SERVICE_NAME?result=${result.value}")
      if (result is AuthorizationResult.Failure && result.reason != null) {
        append("&message=").append(URLEncoder.encode(result.message, StandardCharsets.UTF_8))
      }
    }
    val response = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.FOUND, Unpooled.EMPTY_BUFFER).apply {
      headers().set(HttpHeaderNames.LOCATION, location)
      headers().set("Referrer-Policy", "no-referrer")
    }
    sendResponse(request, context, response)
  }
  //endregion
}

private sealed interface GHOAuthCallbackDecision {
  object UnknownState : GHOAuthCallbackDecision
  data class Failure(val reason: String?) : GHOAuthCallbackDecision
  data class Exchange(val code: String) : GHOAuthCallbackDecision
}

private data class GHOAuthTokenResponse(
  val accessToken: String,
)

private data class GHOAuthTokenRequest(
  val clientId: String,
  val clientSecret: String,
  val grantType: String,
  val code: String,
  val redirectUri: String,
  val codeVerifier: String,
)

private data class PendingLoginWithRequest(
  val codeVerifier: String,
  val request: CompletableDeferred<String>,
)

private sealed class AuthorizationResult(val value: String) {
  abstract val message: String

  data object Success : AuthorizationResult(SUCCESS) {
    override val message: String
      get() = GithubBundle.message("oauth.response.success")
  }

  data class Failure(val reason: String? = null) : AuthorizationResult(FAILURE) {
    override val message: String
      get() = reason ?: GithubBundle.message("oauth.response.failure")
  }

  companion object {
    private const val SUCCESS = "success"
    private const val FAILURE = "failure"

    fun of(value: String?, message: String?): AuthorizationResult? = when (value) {
      SUCCESS -> Success
      FAILURE -> Failure(message)
      else -> null
    }
  }
}

internal class GHOAuthServiceOldImpl : OAuthServiceBase<Credentials>() {
  override val name: String get() = OLD_SERVICE_NAME

  suspend fun acquireToken(): String {
    val credentialsFuture = authorize(GHOAuthRequest())
    try {
      return credentialsFuture.asDeferred().await().accessToken
    }
    catch (ce: CancellationException) {
      credentialsFuture.completeExceptionally(ProcessCanceledException(ce))
      throw ce
    }
  }

  override fun revokeToken(token: String) {
    TODO("Not yet implemented")
  }

  private class GHOAuthRequest : OAuthRequest<Credentials> {
    private val port: Int get() = BuiltInServerManager.getInstance().port

    private val codeVerifier = PkceUtils.generateCodeVerifier()

    private val codeChallenge = PkceUtils.generateShaCodeChallenge(codeVerifier, Base64.getEncoder())

    override val authorizationCodeUrl: Url
      get() = newFromEncoded("http://127.0.0.1:$port/${RestService.PREFIX}/$OLD_SERVICE_NAME/authorization_code")

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
    val SERVICE_URL: Url = newFromEncoded("https://account.jetbrains.com/github/oauth/intellij")
  }
}

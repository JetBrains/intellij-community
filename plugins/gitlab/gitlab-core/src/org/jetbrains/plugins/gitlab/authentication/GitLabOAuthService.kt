// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.authentication

import com.intellij.collaboration.api.httpclient.HttpClientUtil
import com.intellij.collaboration.api.json.loadJsonValue
import com.intellij.collaboration.util.resolveRelative
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.util.text.StringUtil
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.ide.BuiltInServerManager
import org.jetbrains.ide.RestService
import org.jetbrains.ide.RestService.Companion.sendResponse
import org.jetbrains.io.response
import org.jetbrains.plugins.gitlab.api.GitLabApiManager
import org.jetbrains.plugins.gitlab.api.GitLabApiUriQueryBuilder
import org.jetbrains.plugins.gitlab.api.GitLabServerPath
import org.jetbrains.plugins.gitlab.api.withQuery
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpRequest
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

private const val GITLAB_DOT_COM_OAUTH_CLIENT_ID = "0ec01bcbbfce083d0ad842f9e5896e8a38ab837b5630dfe2f41a1af50d1556a9"
private const val CODE_CHALLENGE_METHOD = "S256"
private const val TOKEN_SCOPE = "api read_user write_repository openid"
private const val SERVICE_NAME = "gitlab/oauth"

private const val RESPONSE_PAGE_PATH = "html/oauth-response.html"
private const val MESSAGE_PLACEHOLDER = "{{message}}"
private const val STATUS_CLASS_PLACEHOLDER = "{{statusClass}}"
private const val FALLBACK_RESPONSE_PAGE =
  """<!DOCTYPE html>
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
internal class GitLabOAuthService(private val cs: CoroutineScope) {
  private val responsePageTemplate by lazy { loadResponsePageTemplate() }
  private val pendingLogins = ConcurrentHashMap<String, PendingLoginWithRequest>()
  private val redirectUri
    get() = "http://127.0.0.1:${BuiltInServerManager.getInstance().port}/${RestService.PREFIX}/$SERVICE_NAME"

  @Throws(GitLabOAuthFlowException::class)
  suspend fun authorizeToGitLabDotCom() = authorize(GitLabServerPath.DEFAULT_SERVER, GITLAB_DOT_COM_OAUTH_CLIENT_ID)

  @Throws(GitLabOAuthFlowException::class)
  suspend fun authorize(server: GitLabServerPath, clientId: String): GitLabCredentials.OAuth {
    val requestId = DigestUtil.randomToken()
    val codeVerifier = ByteArray(32).also { DigestUtil.random.nextBytes(it) }
    val codeVerifierEncoded = Base64.getUrlEncoder().withoutPadding().encodeToString(codeVerifier)
    val request = CompletableDeferred<GitLabCredentials.OAuth>()

    val pending = PendingLoginWithRequest(server, clientId, codeVerifierEncoded, request)
    pendingLogins[requestId] = pending
    try {
      val uri = server.buildAuthorizationURI(requestId, clientId, redirectUri, codeVerifierEncoded)
      BrowserUtil.browse(uri)
      return request.await()
    }
    catch (ce: CancellationException) {
      request.cancel(ce)
      throw ce
    }
    finally {
      pendingLogins.remove(requestId)
    }
  }

  private fun GitLabServerPath.buildAuthorizationURI(requestId: String, clientId: String, redirectUri: String, codeVerifier: String): URI {
    val codeChallenge = DigestUtil.sha256().digest(codeVerifier.toByteArray())
    val codeChallengeEncoded = Base64.getUrlEncoder()
      .withoutPadding()
      .encodeToString(codeChallenge)
    return oauthUri.resolveRelative("authorize").withQuery {
      "client_id" eq clientId
      "state" eq requestId
      "response_type" eq "code"
      "code_challenge" eq codeChallengeEncoded
      "code_challenge_method" eq CODE_CHALLENGE_METHOD
      "scope" eq TOKEN_SCOPE
      "redirect_uri" eq redirectUri
    }
  }

  @Throws(GitLabOAuthFlowException::class)
  suspend fun refreshToken(server: GitLabServerPath, refreshToken: String, clientId: String): GitLabCredentials.OAuth {
    val api = service<GitLabApiManager>().getUnauthenticatedClient(server)
    val uri = api.server.oauthUri.resolveRelative("token")
    val request = api.request(uri)
      .postForm {
        "client_id" eq clientId
        "grant_type" eq "refresh_token"
        "refresh_token" eq refreshToken
        "redirect_uri" eq redirectUri
      }
      .header(HttpClientUtil.ACCEPT_HEADER, HttpClientUtil.CONTENT_TYPE_JSON)
      .build()
    return withContext(Dispatchers.IO) {
      api.rest.loadJsonValue<GitLabOAuthResponseDTO>(request).body()
    }.let {
      GitLabCredentials.OAuth.fromDTO(it, clientId)
    }
  }

  private fun onCallback(
    requestId: String?,
    code: String?,
    error: String?,
    errorDescription: String?,
    context: ChannelHandlerContext,
    httpRequest: FullHttpRequest,
  ) {
    val (server, clientId, codeVerifier, request) = requestId?.let { pendingLogins[it] }
                                                    ?: return sendResultRedirect(context, httpRequest, AuthorizationResult.Failure())
    if (error != null) {
      val reason = errorDescription ?: error
      val errorMessage = "Authorization process failed - $reason"
      request.completeExceptionally(GitLabOAuthFlowException(errorMessage))
      return sendResultRedirect(context, httpRequest, AuthorizationResult.Failure(errorMessage))
    }
    if (code == null) {
      val errorMessage = "Authorization process failed - no code in response"
      request.completeExceptionally(GitLabOAuthFlowException(errorMessage))
      return sendResultRedirect(context, httpRequest, AuthorizationResult.Failure(errorMessage))
    }
    cs.launch {
      val result = try {
        val tokenResponse = exchangeCodeForToken(server, clientId, code, redirectUri, codeVerifier)
        request.complete(tokenResponse)
        AuthorizationResult.Success
      }
      catch (ce: CancellationException) {
        request.cancel(ce)
        throw ce
      }
      catch (e: Exception) {
        val errorMessage = "Token exchange failed"
        request.completeExceptionally(GitLabOAuthFlowException(errorMessage, e))
        AuthorizationResult.Failure(errorMessage)
      }
      sendResultRedirect(context, httpRequest, result)
    }
  }

  private suspend fun exchangeCodeForToken(
    serverPath: GitLabServerPath,
    clientId: String,
    code: String,
    redirectUri: String,
    codeVerifier: String,
  ): GitLabCredentials.OAuth {
    val api = service<GitLabApiManager>().getUnauthenticatedClient(serverPath)
    val uri = api.server.oauthUri.resolveRelative("token")
    val request = api.request(uri)
      .postForm {
        "client_id" eq clientId
        "grant_type" eq "authorization_code"
        "code" eq code
        "code_verifier" eq codeVerifier
        "redirect_uri" eq redirectUri
      }
      .header(HttpClientUtil.ACCEPT_HEADER, HttpClientUtil.CONTENT_TYPE_JSON)
      .build()

    return withContext(Dispatchers.IO) {
      api.rest.loadJsonValue<GitLabOAuthResponseDTO>(request).body()
    }.let {
      GitLabCredentials.OAuth.fromDTO(it, clientId)
    }
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
    val response =
      DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.FOUND, Unpooled.EMPTY_BUFFER).apply {
        headers().set(HttpHeaderNames.LOCATION, location)
        headers().set("Referrer-Policy", "no-referrer")
      }
    sendResponse(request, context, response)
  }

  private fun loadResponsePageTemplate(): String {
    val stream = GitLabOAuthService::class.java.classLoader.getResourceAsStream(RESPONSE_PAGE_PATH)
    return try {
      stream?.use { it.readAllBytes().decodeToString() } ?: FALLBACK_RESPONSE_PAGE
    }
    catch (_: IOException) {
      FALLBACK_RESPONSE_PAGE
    }
  }

  class GitLabOAuthCallbackHandler : RestService() {
    override fun getServiceName() = SERVICE_NAME

    override fun execute(
      urlDecoder: QueryStringDecoder,
      request: FullHttpRequest,
      context: ChannelHandlerContext,
    ): String? {
      val parameters = urlDecoder.parameters()
      val result = AuthorizationResult.of(parameters["result"]?.firstOrNull(), parameters["message"]?.firstOrNull())
      if (result != null) {
        // Landing page of the redirect sent by sendResultRedirect: rendered from these parameters, so reloading
        // it or navigating back to it keeps reporting the same outcome
        val html = buildResponsePage(result)
        sendHTMLResponse(context, request, html)
        return null
      }
      val requestId = parameters["state"]?.firstOrNull()
      val code = parameters["code"]?.firstOrNull()
      val error = parameters["error"]?.firstOrNull()
      val errorDescription = parameters["error_description"]?.firstOrNull()
      instance.onCallback(requestId, code, error, errorDescription, context, request)
      return null
    }


    private fun sendHTMLResponse(
      context: ChannelHandlerContext,
      request: FullHttpRequest,
      html: String,
    ) {
      val resp = response("text/html", Unpooled.wrappedBuffer(html.toByteArray())).apply {
        headers().set("Referrer-Policy", "no-referrer")
      }
      sendResponse(request, context, resp)
    }

    private fun buildResponsePage(result: AuthorizationResult): String = instance.responsePageTemplate
      .replace(STATUS_CLASS_PLACEHOLDER, result.value)
      .replace(MESSAGE_PLACEHOLDER, StringUtil.escapeXmlEntities(result.message))
  }

  companion object {
    val instance: GitLabOAuthService
      get() = service()
  }
}

private sealed class AuthorizationResult(val value: String) {
  abstract val message: String

  data object Success : AuthorizationResult(SUCCESS) {
    override val message: String = SUCCESS_RESPONSE_TEXT
  }

  data class Failure(val reason: String? = null) : AuthorizationResult(FAILURE) {
    override val message: String
      get() = reason ?: FAILURE_RESPONSE_TEXT
  }

  companion object {
    private const val SUCCESS = "success"
    private const val FAILURE = "failure"
    private const val SUCCESS_RESPONSE_TEXT = "You have been successfully authorized in GitLab. You can close the page."
    private const val FAILURE_RESPONSE_TEXT = "Authorization failed."

    fun of(value: String?, message: String?): AuthorizationResult? = when (value) {
      SUCCESS -> Success
      FAILURE -> Failure(message)
      else -> null
    }
  }
}

private data class PendingLoginWithRequest(
  val serverPath: GitLabServerPath,
  val clientId: String,
  val codeVerifier: String,
  val request: CompletableDeferred<GitLabCredentials.OAuth>,
)

private fun HttpRequest.Builder.postForm(buildQuery: GitLabApiUriQueryBuilder.() -> Unit): HttpRequest.Builder {
  val body = GitLabApiUriQueryBuilder.build(buildQuery)
  return header(HttpClientUtil.CONTENT_TYPE_HEADER, HttpClientUtil.CONTENT_TYPE_ENCODED_FORM)
    .POST(HttpRequest.BodyPublishers.ofString(body))
}
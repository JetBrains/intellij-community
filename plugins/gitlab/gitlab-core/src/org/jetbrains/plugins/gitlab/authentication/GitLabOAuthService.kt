// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.authentication

import com.intellij.collaboration.api.httpclient.HttpClientUtil
import com.intellij.collaboration.api.json.loadJsonValue
import com.intellij.collaboration.util.resolveRelative
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.util.io.DigestUtil
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
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
import java.net.URI
import java.net.http.HttpRequest
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

private const val OAUTH_CLIENT_ID = "0ec01bcbbfce083d0ad842f9e5896e8a38ab837b5630dfe2f41a1af50d1556a9"
private const val CODE_CHALLENGE_METHOD = "S256"
private const val TOKEN_SCOPE = "api read_user write_repository openid"
private const val SUCCESS_HTML = "<p><strong>Authentication Successful!</strong></p>"
private const val FAILURE_HTML = "<p><strong>Authentication Failed.</strong></p>"
private const val SERVICE_NAME = "gitlab/oauth"

@Service(Service.Level.APP)
internal class GitLabOAuthService(private val cs: CoroutineScope) {
  private val pendingLogins = ConcurrentHashMap<String, PendingLoginWithRequest>()

  private val redirectUri
    get() = "http://127.0.0.1:${BuiltInServerManager.getInstance().port}/${RestService.PREFIX}/$SERVICE_NAME"

  @Throws(GitLabOAuthFlowException::class)
  suspend fun authorize(server: GitLabServerPath): GitLabCredentials.OAuth {
    check(server.isDefault)
    val requestId = DigestUtil.randomToken()
    val codeVerifier = ByteArray(32).also { DigestUtil.random.nextBytes(it) }
    val codeVerifierEncoded = Base64.getUrlEncoder().withoutPadding().encodeToString(codeVerifier)
    val request = CompletableDeferred<GitLabCredentials.OAuth>()

    val pending = PendingLoginWithRequest(server, codeVerifierEncoded, request)
    pendingLogins[requestId] = pending
    try {
      val uri = server.buildAuthorizationURI(requestId, redirectUri, codeVerifierEncoded)
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

  private fun GitLabServerPath.buildAuthorizationURI(requestId: String, redirectUri: String, codeVerifier: String): URI {
    val codeChallenge = DigestUtil.sha256().digest(codeVerifier.toByteArray())
    val codeChallengeEncoded = Base64.getUrlEncoder()
      .withoutPadding()
      .encodeToString(codeChallenge)
    return oauthUri.resolveRelative("authorize").withQuery {
      "client_id" eq OAUTH_CLIENT_ID
      "state" eq requestId
      "response_type" eq "code"
      "code_challenge" eq codeChallengeEncoded
      "code_challenge_method" eq CODE_CHALLENGE_METHOD
      "scope" eq TOKEN_SCOPE
      "redirect_uri" eq redirectUri
    }
  }

  @Throws(GitLabOAuthFlowException::class)
  suspend fun refreshToken(server: GitLabServerPath, refreshToken: String): GitLabCredentials.OAuth {
    check(server.isDefault)
    val api = service<GitLabApiManager>().getUnauthenticatedClient(server)
    val uri = api.server.oauthUri.resolveRelative("token")
    val request = api.request(uri)
      .postForm {
        "client_id" eq OAUTH_CLIENT_ID
        "grant_type" eq "refresh_token"
        "refresh_token" eq refreshToken
        "redirect_uri" eq redirectUri
      }
      .header(HttpClientUtil.ACCEPT_HEADER, HttpClientUtil.CONTENT_TYPE_JSON)
      .build()
    return withContext(Dispatchers.IO) {
      api.rest.loadJsonValue<GitLabOAuthResponseDTO>(request).body()
    }.let {
      GitLabCredentials.OAuth.fromDTO(it)
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
    val (server, codeVerifier, request) = requestId?.let { pendingLogins[it] }
                                          ?: return sendHTMLResponse(context, httpRequest, FAILURE_HTML)
    if (error != null) {
      val reason = errorDescription ?: error
      request.completeExceptionally(GitLabOAuthFlowException("Authorization process failed - $reason"))
      return sendHTMLResponse(context, httpRequest, FAILURE_HTML)
    }
    if (code == null) {
      request.completeExceptionally(GitLabOAuthFlowException("Authorization process failed - no code in response"))
      return sendHTMLResponse(context, httpRequest, FAILURE_HTML)
    }
    cs.launch {
      val html = try {
        val tokenResponse = exchangeCodeForToken(server, code, redirectUri, codeVerifier)
        request.complete(tokenResponse)
        SUCCESS_HTML
      }
      catch (ce: CancellationException) {
        request.cancel(ce)
        throw ce
      }
      catch (e: Exception) {
        request.completeExceptionally(GitLabOAuthFlowException("Token exchange failed", e))
        FAILURE_HTML
      }
      sendHTMLResponse(context, httpRequest, html)
    }
  }

  private suspend fun exchangeCodeForToken(
    serverPath: GitLabServerPath,
    code: String,
    redirectUri: String,
    codeVerifier: String,
  ): GitLabCredentials.OAuth {
    val api = service<GitLabApiManager>().getUnauthenticatedClient(serverPath)
    val uri = api.server.oauthUri.resolveRelative("token")
    val request = api.request(uri)
      .postForm {
        "client_id" eq OAUTH_CLIENT_ID
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
      GitLabCredentials.OAuth.fromDTO(it)
    }
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

  class GitLabOAuthCallbackHandler : RestService() {
    override fun getServiceName() = SERVICE_NAME

    override fun execute(
      urlDecoder: QueryStringDecoder,
      request: FullHttpRequest,
      context: ChannelHandlerContext,
    ): String? {
      val parameters = urlDecoder.parameters()
      val requestId = parameters["state"]?.firstOrNull()
      val code = parameters["code"]?.firstOrNull()
      val error = parameters["error"]?.firstOrNull()
      val errorDescription = parameters["error_description"]?.firstOrNull()
      instance.onCallback(requestId, code, error, errorDescription, context, request)
      return null
    }
  }

  companion object {
    val instance: GitLabOAuthService
      get() = service()
  }
}

private data class PendingLoginWithRequest(
  val serverPath: GitLabServerPath,
  val codeVerifier: String,
  val request: CompletableDeferred<GitLabCredentials.OAuth>,
)

private fun HttpRequest.Builder.postForm(buildQuery: GitLabApiUriQueryBuilder.() -> Unit): HttpRequest.Builder {
  val body = GitLabApiUriQueryBuilder.build(buildQuery)
  return header(HttpClientUtil.CONTENT_TYPE_HEADER, HttpClientUtil.CONTENT_TYPE_ENCODED_FORM)
    .POST(HttpRequest.BodyPublishers.ofString(body))
}
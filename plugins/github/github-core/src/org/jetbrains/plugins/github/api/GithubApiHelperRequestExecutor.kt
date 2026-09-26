// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api

import com.intellij.collaboration.api.HttpApiHelper
import com.intellij.collaboration.api.HttpStatusErrorException
import com.intellij.collaboration.api.httpclient.CompoundRequestConfigurer
import com.intellij.collaboration.api.httpclient.HttpClientFactoryBase
import com.intellij.collaboration.api.httpclient.HttpClientUtil
import com.intellij.collaboration.api.httpclient.HttpRequestConfigurer
import com.intellij.collaboration.api.httpclient.RequestTimeoutConfigurer
import com.intellij.collaboration.api.httpclient.checkStatusCodeWithLogging
import com.intellij.collaboration.api.httpclient.forceAcceptGzipEncoding
import com.intellij.collaboration.api.httpclient.readBodyWithLogging
import com.intellij.collaboration.api.logName
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.checkCanceled
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.util.Computable
import com.intellij.util.ThrowableConvertor
import com.intellij.util.io.HttpSecurityUtil
import com.intellij.util.net.PlatformHttpClient
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor.Companion.PLUGIN_USER_AGENT_NAME
import org.jetbrains.plugins.github.exceptions.GithubConfusingException
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.GHPRStatisticsCollector
import org.jetbrains.plugins.github.util.GithubSettings
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.Charset
import kotlin.jvm.optionals.getOrNull

/**
 * [HttpApiHelper]-based executor
 */
internal class GithubApiHelperRequestExecutor(
  private val helper: HttpApiHelper,
) : GithubApiRequestExecutor() {

  constructor(useProxy: Boolean, tokenSupplier: (URL) -> String?) : this(createHelper(useProxy, tokenSupplier))

  @Suppress("UsagesOfObsoleteApi")
  override fun <T> execute(indicator: ProgressIndicator, request: GithubApiRequest<T>): T =
    ProgressManager.getInstance().runProcess(Computable { runBlockingCancellable { execute(request) } }, indicator)

  override suspend fun <T> execute(request: GithubApiRequest<T>): T {
    check(!service<GHRequestExecutorBreaker>().isRequestsShouldFail) {
      "Request failure was triggered by user action. This a pretty long description of this failure that should resemble some long error which can go out of bounds."
    }

    val activity = GHPRStatisticsCollector.logApiRequestStart(request.operation)
    val httpRequest = createHttpRequest(request).forceAcceptGzipEncoding()
    val requestName = httpRequest.logName()

    if (LOG.isTraceEnabled && request is GithubApiRequest.WithBody) {
      LOG.trace("$requestName : Request body: ${request.body}")
    }

    val response = try {
      helper.sendAndAwait(httpRequest, requestName, HttpResponse.BodyHandlers.ofInputStream())
    }
    catch (e: IOException) {
      /**
       * GitHub server is not up to spec - it does not send WWW-Authenticate header with a 401 status code like it should.
       * If this header is missing, the JDK11 HTTP client throws an `IOException` from [jdk.internal.net.http.AuthenticationFilter.response]
       *
       * Generally this should be very rare - only for unauthorized requests which do need authorization. We don't expect such requests.
       */
      if (e.message?.contains("response code 401") == true && PlatformHttpClient.isAuthenticationFilterError(e)) {
        throw createApiException(requestName, HttpURLConnection.HTTP_UNAUTHORIZED, GithubBundle.message("authorization.failed"), null)
      }
      throw e
    }

    GHPRStatisticsCollector.logApiResponseReceived(
      activity = activity,
      remaining = response.headers().firstValue(RATE_LIMIT_REMAINING_HEADER).getOrNull()?.toIntOrNull() ?: -1,
      resourceName = response.headers().firstValue(RATE_LIMIT_RESOURCE_HEADER).getOrNull() ?: "unknown",
      statusCode = response.statusCode(),
    )

    try {
      response.checkStatusCodeWithLogging(LOG, requestName)
      checkCanceled()
      checkServerVersion(response)

      return response.readBodyWithLogging(LOG, requestName) { _, charset ->
        val apiResponse = createResponse(response, this, charset)
        val (result, rates) = if (request is GithubApiRequest.Post.GQLQuery) {
          request.extractResultWithCost(apiResponse)
        }
        else {
          request.extractResult(apiResponse) to null
        }
        val cost = rates?.cost

        GHPRStatisticsCollector.logApiResponseRates(request.operation, cost ?: 1, isGuessed = cost == null)
        result
      }
    }
    catch (e: HttpStatusErrorException) {
      @Suppress("UNCHECKED_CAST")
      if (request is GithubApiRequest.Get.Optional<*> && e.statusCode == HttpURLConnection.HTTP_NOT_FOUND) return null as T

      val responseContentType = response.headers().firstValue(HttpClientUtil.CONTENT_TYPE_HEADER).getOrNull()
      throw createApiException(requestName, e.statusCode, e.body, responseContentType)
    }
    catch (e: GithubConfusingException) {
      if (request.operationName != null) {
        val errorText = "Can't ${request.operationName}"
        e.setDetails(errorText)
        LOG.debug(errorText, e)
      }
      throw e
    }
  }

  private suspend fun createHttpRequest(request: GithubApiRequest<*>): HttpRequest {
    val builder = helper.request(URI.create(request.url))
    request.acceptMimeType?.let { builder.header(HttpClientUtil.ACCEPT_HEADER, it) }
    for ((name, value) in request.additionalHeaders) {
      // the client computes the length from the body publisher
      if (name.equals(HttpClientUtil.CONTENT_LENGTH_HEADER, ignoreCase = true)) continue
      builder.header(name, value)
    }
    return when (request) {
      is GithubApiRequest.Get -> builder.GET()
      is GithubApiRequest.Head -> builder.HEAD()
      is GithubApiRequest.Patch -> builder.withBody("PATCH", request)
      is GithubApiRequest.Post -> builder.withBody("POST", request)
      is GithubApiRequest.Put -> builder.withBody("PUT", request)
      is GithubApiRequest.Delete -> builder.withBody("DELETE", request)
      else -> throw UnsupportedOperationException("${request.javaClass} is not supported")
    }.build()
  }

  private fun HttpRequest.Builder.withBody(method: String, request: GithubApiRequest.WithBody<*>): HttpRequest.Builder {
    header(HttpClientUtil.CONTENT_TYPE_HEADER, request.bodyMimeType)
    val body = request.body
    return method(method, if (body == null) HttpRequest.BodyPublishers.noBody() else HttpRequest.BodyPublishers.ofString(body))
  }

  private fun checkServerVersion(response: HttpResponse<*>) {
    // let's assume it's not ghe if header is missing or it's *.ghe.com
    val versionHeader = response.headers().firstValue(GHEServerVersionChecker.ENTERPRISE_VERSION_HEADER).getOrNull() ?: return
    if (versionHeader.contains("ghe.com", ignoreCase = true)) return // Kinda fragile...

    GHEServerVersionChecker.checkVersionSupported(versionHeader)
  }

  private fun createResponse(response: HttpResponse<*>, body: InputStream, charset: Charset?): GithubApiResponse {
    return object : GithubApiResponse {
      override fun findHeader(headerName: String): String? = response.headers().firstValue(headerName).getOrNull()

      override fun <T> readBody(converter: ThrowableConvertor<Reader, T, IOException>): T =
        converter.convert(InputStreamReader(body, charset ?: Charsets.UTF_8))

      override fun <T> handleBody(converter: ThrowableConvertor<InputStream, T, IOException>): T = converter.convert(body)
    }
  }
}

private fun createHelper(
  useProxy: Boolean = true,
  tokenSupplier: (suspend (URL) -> String?)? = null,
): HttpApiHelper {
  val configurers = buildList {
    add(RequestTimeoutConfigurer())
    add(GHHeadersConfigurer())
    if (tokenSupplier != null) {
      add(GHAuthorizationConfigurer(tokenSupplier))
    }
  }
  return HttpApiHelper(logger = GithubApiRequestExecutor.LOG,
                       clientFactory = GHHttpClientFactory(useProxy),
                       requestConfigurer = CompoundRequestConfigurer(configurers))
}

private class GHHttpClientFactory(override val useProxy: Boolean) : HttpClientFactoryBase() {
  override val connectionTimeoutMillis: Long
    get() = GithubSettings.getInstance().connectionTimeout.toLong()
}

private class GHHeadersConfigurer : HttpRequestConfigurer {
  override suspend fun configureSuspend(builder: HttpRequest.Builder): HttpRequest.Builder =
    builder.apply {
      header(HttpClientUtil.ACCEPT_ENCODING_HEADER, HttpClientUtil.CONTENT_ENCODING_GZIP)
      header(HttpClientUtil.USER_AGENT_HEADER, HttpClientUtil.getUserAgentValue(PLUGIN_USER_AGENT_NAME))
    }
}

private class GHAuthorizationConfigurer(
  private val tokenSupplier: suspend (URL) -> String?,
) : HttpRequestConfigurer {
  override suspend fun configureSuspend(builder: HttpRequest.Builder): HttpRequest.Builder {
    val url = builder.build().uri().toURL()
    val token = tokenSupplier(url)
    return if (token == null) {
      builder
    }
    else {
      val headerValue = HttpSecurityUtil.createBearerAuthHeaderValue(token)
      builder.header(HttpSecurityUtil.AUTHORIZATION_HEADER_NAME, headerValue)
    }
  }
}
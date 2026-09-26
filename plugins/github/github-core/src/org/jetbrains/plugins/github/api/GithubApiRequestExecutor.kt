// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api

import com.intellij.collaboration.api.httpclient.HttpClientUtil
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.ThrowableConvertor
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.intellij.util.io.HttpRequests
import com.intellij.util.io.HttpSecurityUtil
import com.intellij.util.io.RequestBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.github.api.data.GithubErrorMessage
import org.jetbrains.plugins.github.exceptions.GithubAuthenticationException
import org.jetbrains.plugins.github.exceptions.GithubConfusingException
import org.jetbrains.plugins.github.exceptions.GithubJsonException
import org.jetbrains.plugins.github.exceptions.GithubRateLimitExceededException
import org.jetbrains.plugins.github.exceptions.GithubStatusCodeException
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.GHPRStatisticsCollector
import org.jetbrains.plugins.github.util.GithubSettings
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/**
 * Executes API requests taking care of authentication, headers, proxies, timeouts, etc.
 */
@ApiStatus.NonExtendable
sealed class GithubApiRequestExecutor {
  @RequiresBackgroundThread(generateAssertion = false /* IJPL-115548 */)
  @RequiresBlockingContext(replaceWith = ReplaceWith("GithubApiRequestExecutor.execute"))
  @Throws(IOException::class, ProcessCanceledException::class)
  abstract fun <T> execute(indicator: ProgressIndicator, request: GithubApiRequest<T>): T

  @Throws(IOException::class)
  abstract suspend fun <T> execute(request: GithubApiRequest<T>): T

  internal class WithTokenAuth(
    githubSettings: GithubSettings,
    private val tokenSupplier: (URL) -> String?,
    private val useProxy: Boolean,
  ) : Base(githubSettings) {

    @Throws(IOException::class, ProcessCanceledException::class)
    override fun <T> execute(indicator: ProgressIndicator, request: GithubApiRequest<T>): T {
      check(!service<GHRequestExecutorBreaker>().isRequestsShouldFail) {
        "Request failure was triggered by user action. This a pretty long description of this failure that should resemble some long error which can go out of bounds."
      }

      indicator.checkCanceled()
      return createRequestBuilder(request)
        .tuner { connection ->
          request.additionalHeaders.forEach(connection::addRequestProperty)
          val token = tokenSupplier(connection.url)
          if (token != null) {
            connection.addRequestProperty(HttpSecurityUtil.AUTHORIZATION_HEADER_NAME, HttpSecurityUtil.createBearerAuthHeaderValue(token))
          }
        }
        .useProxy(useProxy)
        .execute(request, indicator)
    }
  }

  internal class NoAuth(githubSettings: GithubSettings) : Base(githubSettings) {
    override fun <T> execute(indicator: ProgressIndicator, request: GithubApiRequest<T>): T {
      indicator.checkCanceled()
      return createRequestBuilder(request)
        .tuner { connection ->
          request.additionalHeaders.forEach(connection::addRequestProperty)
        }
        .useProxy(true)
        .execute(request, indicator)
    }
  }

  @Deprecated("Was never intended to be public")
  abstract class Base(private val githubSettings: GithubSettings) : GithubApiRequestExecutor() {
    final override suspend fun <T> execute(request: GithubApiRequest<T>): T {
      return withContext(Dispatchers.IO) {
        coroutineToIndicator {
          execute(it, request)
        }
      }
    }

    protected fun <T> RequestBuilder.execute(request: GithubApiRequest<T>, indicator: ProgressIndicator): T {
      indicator.checkCanceled()
      try {
        LOG.debug("Request: ${request.url} ${request.operationName} : Connecting")
        val activity = GHPRStatisticsCollector.logApiRequestStart(request.operation)
        return connect {
          val connection = it.connection as HttpURLConnection
          if (request is GithubApiRequest.WithBody) {
            LOG.debug("Request: ${connection.requestMethod} ${connection.url} with body:\n${request.body} : Connected")
            request.body?.let { body -> it.write(body) }
          }
          else {
            LOG.debug("Request: ${connection.requestMethod} ${connection.url} : Connected")
          }

          GHPRStatisticsCollector.logApiResponseReceived(
            activity = activity,
            remaining = connection.getHeaderFieldInt(RATE_LIMIT_REMAINING_HEADER, -1),
            resourceName = connection.getHeaderField(RATE_LIMIT_RESOURCE_HEADER) ?: "unknown",
            statusCode = connection.responseCode,
          )

          checkResponseCode(connection)
          checkServerVersion(connection)

          indicator.checkCanceled()

          val (result, rates) = if (request is GithubApiRequest.Post.GQLQuery) {
            request.extractResultWithCost(createResponse(it, indicator))
          }
          else {
            request.extractResult(createResponse(it, indicator)) to null
          }
          val cost = rates?.cost

          GHPRStatisticsCollector.logApiResponseRates(request.operation, cost ?: 1, isGuessed = cost == null)

          LOG.debug("Request: ${connection.requestMethod} ${connection.url} : Result extracted")
          result
        }
      }
      catch (e: GithubStatusCodeException) {
        @Suppress("UNCHECKED_CAST")
        if (request is GithubApiRequest.Get.Optional<*> && e.statusCode == HttpURLConnection.HTTP_NOT_FOUND) return null as T else throw e
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

    protected fun createRequestBuilder(request: GithubApiRequest<*>): RequestBuilder {
      return when (request) {
        is GithubApiRequest.Get -> HttpRequests.request(request.url)
        is GithubApiRequest.Patch -> HttpRequests.patch(request.url, request.bodyMimeType)
        is GithubApiRequest.Post -> HttpRequests.post(request.url, request.bodyMimeType)
        is GithubApiRequest.Put -> HttpRequests.put(request.url, request.bodyMimeType)
        is GithubApiRequest.Head -> HttpRequests.head(request.url)
        is GithubApiRequest.Delete -> {
          if (request.body == null) HttpRequests.delete(request.url) else HttpRequests.delete(request.url, request.bodyMimeType)
        }

        else -> throw UnsupportedOperationException("${request.javaClass} is not supported")
      }
        .connectTimeout(githubSettings.connectionTimeout)
        .userAgent(HttpClientUtil.getUserAgentValue(PLUGIN_USER_AGENT_NAME))
        .throwStatusCodeException(false)
        .forceHttps(false)
        .accept(request.acceptMimeType)
    }

    @Throws(IOException::class)
    private fun checkResponseCode(connection: HttpURLConnection) {
      if (connection.responseCode < 400) return
      val requestName = "Request: ${connection.requestMethod} ${connection.url}"
      val errorText = getErrorText(connection)
      LOG.debug("$requestName : Error ${connection.responseCode} body:\n$errorText")
      throw createApiException(requestName, connection.responseCode, errorText, connection.contentType)
    }

    private fun checkServerVersion(connection: HttpURLConnection) {
      // let's assume it's not ghe if header is missing or it's *.ghe.com
      val versionHeader = connection.getHeaderField(GHEServerVersionChecker.ENTERPRISE_VERSION_HEADER) ?: return
      if (versionHeader.contains("ghe.com", ignoreCase = true)) return // Kinda fragile...

      GHEServerVersionChecker.checkVersionSupported(versionHeader)
    }

    private fun getErrorText(connection: HttpURLConnection): String? {
      val errorStream = connection.errorStream ?: return null
      val stream = if (connection.contentEncoding == "gzip") GZIPInputStream(errorStream) else errorStream
      return InputStreamReader(stream, Charsets.UTF_8).use { it.readText() }
    }

    private fun createResponse(request: HttpRequests.Request, indicator: ProgressIndicator): GithubApiResponse {
      return object : GithubApiResponse {
        override fun findHeader(headerName: String): String? = request.connection.getHeaderField(headerName)

        override fun <T> readBody(converter: ThrowableConvertor<Reader, T, IOException>): T = request.getReader(indicator).use {
          converter.convert(it)
        }

        override fun <T> handleBody(converter: ThrowableConvertor<InputStream, T, IOException>): T = request.inputStream.use {
          converter.convert(it)
        }
      }
    }
  }

  @Service
  class Factory {

    fun create(serverPath: GithubServerPath, token: String): GithubApiRequestExecutor = create(true, serverPath, token)

    internal fun create(tokenSupplier: MutableTokenSupplier): GithubApiRequestExecutor =
      create(true, tokenSupplier.serverPath) { tokenSupplier.token }

    fun create(useProxy: Boolean = true, serverPath: GithubServerPath, token: String): GithubApiRequestExecutor =
      create(useProxy, serverPath) { token }

    private fun create(useProxy: Boolean = true, serverPath: GithubServerPath, tokenSupplier: () -> String?): GithubApiRequestExecutor {
      val guardedSupplier = { url: URL ->
        if (isAuthorizedUrl(serverPath, url)) tokenSupplier() else null
      }
      return if (Registry.`is`(JDK11_CLIENT_REGISTRY_KEY)) {
        GithubApiHelperRequestExecutor(useProxy, guardedSupplier)
      }
      else {
        WithTokenAuth(GithubSettings.getInstance(), guardedSupplier, useProxy)
      }
    }

    fun create(): GithubApiRequestExecutor {
      return if (Registry.`is`(JDK11_CLIENT_REGISTRY_KEY)) {
        GithubApiHelperRequestExecutor(true, { null })
      }
      else {
        NoAuth(GithubSettings.getInstance())
      }
    }

    companion object {
      @JvmStatic
      fun getInstance(): Factory = service()

      @VisibleForTesting
      internal const val JDK11_CLIENT_REGISTRY_KEY = "github.jdk11.api.client"
    }
  }

  companion object {
    internal const val PLUGIN_USER_AGENT_NAME = "IntelliJ-GitHub-Plugin"
    internal val LOG = logger<GithubApiRequestExecutor>()

    internal const val RATE_LIMIT_REMAINING_HEADER : String = "X-RateLimit-Remaining"
    internal const val RATE_LIMIT_RESOURCE_HEADER : String = "X-RateLimit-Resource"

    internal fun isAuthorizedUrl(serverPath: GithubServerPath, url: URL): Boolean {
      val targetHost = url.host
      val apiHost = serverPath.apiHost
      val mainHost = serverPath.host

      val ghDotComRawHost = "raw.githubusercontent.com"
      val enterpriseRawHost = "raw.$mainHost" // If GH Enterprise serves raw files from raw.<enterprise-host>
      val enterpriseAvatarHost = "avatars.$mainHost" // GHE serves avatars from avatars.<enterprise-host>
      val enterpriseMediaHost = "media.$mainHost" // GHE serves media attachments from media.<enterprise-host>

      val hostMatches = when {
        targetHost == mainHost -> true
        targetHost == apiHost -> true
        targetHost == enterpriseRawHost -> true
        targetHost == enterpriseAvatarHost -> true
        targetHost == enterpriseMediaHost -> true
        serverPath.isGithubDotCom || serverPath.isGheDataResidency -> targetHost == ghDotComRawHost
        else -> false
      }

      if (!hostMatches) {
        // do not log for avatars to avoid log pollution
        if (targetHost != "avatars.githubusercontent.com") {
          LOG.info("URL $url host does not match the server $serverPath. Authorization will not be granted")
        }
        return false
      }
      if (url.port != (serverPath.port ?: -1)) {
        LOG.info("URL $url port does not match the server $serverPath. Authorization will not be granted")
        return false
      }
      if (url.protocol != null && url.protocol != serverPath.schema) {
        LOG.info("URL $url protocol does not match the server $serverPath. Authorization will not be granted")
        return false
      }
      if (serverPath.schema == "http") {
        LOG.warn("URL $url use HTTP, not HTTPS, token leak is possible")
      }
      return true
    }

    internal fun createApiException(
      requestName: String,
      responseCode: Int,
      responseText: String?,
      responseContentType: String?,
    ): IOException {
      val jsonError = if (responseText != null && responseContentType != null) {
        getJsonError(requestName, responseText, responseContentType)
      }
      else {
        null
      }

      val exception = when (responseCode) {
        HttpURLConnection.HTTP_UNAUTHORIZED,
        HttpURLConnection.HTTP_PAYMENT_REQUIRED,
        HttpURLConnection.HTTP_FORBIDDEN,
          -> {
          if (jsonError?.containsReasonMessage("API rate limit exceeded") == true) {
            GithubRateLimitExceededException(jsonError.presentableError)
          }
          else GithubAuthenticationException(
            GithubBundle.message("request.response.0", jsonError?.presentableError ?: responseText ?: responseCode))
        }

        else -> {
          if (jsonError != null) {
            GithubStatusCodeException("$responseCode - ${jsonError.presentableError}", jsonError, responseCode)
          }
          else {
            GithubStatusCodeException("$responseCode - ${responseText}", responseCode)
          }
        }
      }
      return exception
    }

    internal fun getJsonError(requestName: String, errorText: String, contentType: String): GithubErrorMessage? {
      if (!contentType.startsWith(GithubApiContentHelper.JSON_MIME_TYPE)) return null
      return try {
        return GithubApiContentHelper.fromJson(errorText)
      }
      catch (jse: GithubJsonException) {
        LOG.debug("$requestName : Unable to parse JSON error from text \n$errorText", jse)
        null
      }
    }
  }

  internal class MutableTokenSupplier(val serverPath: GithubServerPath, @Volatile var token: String)
}

@Deprecated(message = "Suspending method is now a part of the interface",
            replaceWith = ReplaceWith("GithubApiRequestExecutor.execute"))
suspend fun <T> GithubApiRequestExecutor.executeSuspend(request: GithubApiRequest<T>): T = execute(request)
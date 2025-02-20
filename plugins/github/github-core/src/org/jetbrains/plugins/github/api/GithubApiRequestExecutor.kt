// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api

import com.intellij.collaboration.api.httpclient.HttpClientUtil
import com.intellij.collaboration.ui.SimpleEventListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.*
import com.intellij.util.EventDispatcher
import com.intellij.util.ThrowableConvertor
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.io.HttpRequests
import com.intellij.util.io.HttpSecurityUtil
import com.intellij.util.io.RequestBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.github.api.data.GithubErrorMessage
import org.jetbrains.plugins.github.exceptions.*
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
sealed class GithubApiRequestExecutor {

  open fun addListener(disposable: Disposable, listener: () -> Unit) = Unit

  @RequiresBackgroundThread
  @Throws(IOException::class, ProcessCanceledException::class)
  abstract fun <T> execute(indicator: ProgressIndicator, request: GithubApiRequest<T>): T

  @TestOnly
  @RequiresBackgroundThread
  @Throws(IOException::class, ProcessCanceledException::class)
  fun <T> execute(request: GithubApiRequest<T>): T = execute(EmptyProgressIndicator(), request)

  internal class WithTokenAuth(githubSettings: GithubSettings,
                               private val tokenSupplier: (URL) -> String?,
                               private val useProxy: Boolean) : Base(githubSettings) {

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

    override fun addListener(disposable: Disposable, listener: () -> Unit) {
      if (tokenSupplier is MutableTokenSupplier) {
        tokenSupplier.addListener(disposable, listener)
      }
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

  abstract class Base(private val githubSettings: GithubSettings) : GithubApiRequestExecutor() {
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
            remaining = connection.getHeaderFieldInt("x-ratelimit-remaining", -1),
            resourceName = connection.getHeaderField("x-ratelimit-resource") ?: "unknown",
            statusCode = connection.responseCode,
          )

          checkResponseCode(connection)
          checkServerVersion(connection)

          indicator.checkCanceled()

          val (result, rates) = if (request is GithubApiRequest.Post.GQLQuery) {
            request.extractResultWithCost(createResponse(it, indicator))
          } else {
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
      val statusLine = "${connection.responseCode} ${connection.responseMessage}"
      val errorText = getErrorText(connection)
      LOG.debug("Request: ${connection.requestMethod} ${connection.url} : Error ${statusLine} body:\n${errorText}")

      val jsonError = errorText?.let { getJsonError(connection, it) }
      jsonError ?: LOG.debug("Request: ${connection.requestMethod} ${connection.url} : Unable to parse JSON error")

      throw when (connection.responseCode) {
        HttpURLConnection.HTTP_UNAUTHORIZED,
        HttpURLConnection.HTTP_PAYMENT_REQUIRED,
        HttpURLConnection.HTTP_FORBIDDEN -> {
          if (jsonError?.containsReasonMessage("API rate limit exceeded") == true) {
            GithubRateLimitExceededException(jsonError.presentableError)
          }
          else GithubAuthenticationException(
            GithubBundle.message("request.response.0", jsonError?.presentableError ?: errorText ?: statusLine))
        }

        else -> {
          if (jsonError != null) {
            GithubStatusCodeException("$statusLine - ${jsonError.presentableError}", jsonError, connection.responseCode)
          }
          else {
            GithubStatusCodeException("$statusLine - ${errorText}", connection.responseCode)
          }
        }
      }
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

    private fun getJsonError(connection: HttpURLConnection, errorText: String): GithubErrorMessage? {
      val contentType = connection.contentType
      if (contentType == null || !contentType.startsWith(GithubApiContentHelper.JSON_MIME_TYPE)) return null
      return try {
        return GithubApiContentHelper.fromJson(errorText)
      }
      catch (jse: GithubJsonException) {
        null
      }
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
    @Deprecated("Server must be provided to match URL for authorization")
    fun create(token: String): GithubApiRequestExecutor = create(true) { token }

    fun create(serverPath: GithubServerPath, token: String): GithubApiRequestExecutor = create(true, serverPath, token)

    internal fun create(tokenSupplier: MutableTokenSupplier): GithubApiRequestExecutor = create(true, tokenSupplier)

    fun create(useProxy: Boolean = true, serverPath: GithubServerPath, token: String): GithubApiRequestExecutor =
      create(useProxy) {
        if (isAuthorizedUrl(serverPath, it)) token else null
      }

    private fun create(useProxy: Boolean = true, tokenSupplier: (URL) -> String?): GithubApiRequestExecutor =
      WithTokenAuth(GithubSettings.getInstance(), tokenSupplier, useProxy)

    fun create(): GithubApiRequestExecutor = NoAuth(GithubSettings.getInstance())

    companion object {
      @JvmStatic
      fun getInstance(): Factory = service()
    }
  }

  companion object {
    private const val PLUGIN_USER_AGENT_NAME = "IntelliJ-GitHub-Plugin"
    private val LOG = logger<GithubApiRequestExecutor>()

    private fun isAuthorizedUrl(serverPath: GithubServerPath, url: URL): Boolean {
      if (url.host != serverPath.host && url.host != serverPath.apiHost) {
        LOG.debug("URL $url host does not match the server $serverPath. Authorization will not be granted")
        return false
      }
      if (url.port != (serverPath.port ?: -1)) {
        LOG.debug("URL $url port does not match the server $serverPath. Authorization will not be granted")
        return false
      }
      if (url.protocol != null && url.protocol != serverPath.schema) {
        LOG.debug("URL $url protocol does not match the server $serverPath. Authorization will not be granted")
        return false
      }
      return true
    }
  }

  internal class MutableTokenSupplier(private val serverPath: GithubServerPath, token: String) : (URL) -> String? {
    private val authDataChangedEventDispatcher = EventDispatcher.create(SimpleEventListener::class.java)

    @Volatile
    var token: String = token
      set(value) {
        field = value
        runInEdt(ModalityState.any()) {
          authDataChangedEventDispatcher.multicaster.eventOccurred()
        }
      }

    override fun invoke(url: URL): String? = if (isAuthorizedUrl(serverPath, url)) token else null

    fun addListener(disposable: Disposable, listener: () -> Unit) =
      SimpleEventListener.addDisposableListener(authDataChangedEventDispatcher, disposable, listener)
  }
}

suspend fun <T> GithubApiRequestExecutor.executeSuspend(request: GithubApiRequest<T>): T =
  withContext(Dispatchers.IO) {
    coroutineToIndicator {
      val indicator = ProgressManager.getInstance().progressIndicator ?: EmptyProgressIndicator()
      execute(indicator, request)
    }
  }
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
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.util.EventDispatcher
import com.intellij.util.ThrowableConvertor
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.io.HttpRequests
import com.intellij.util.io.HttpSecurityUtil
import com.intellij.util.io.RequestBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly
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
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

/**
 * Executes API requests taking care of authentication, headers, proxies, timeouts, etc.
 */
sealed class GithubApiRequestExecutor {

  open fun addListener(disposable: Disposable, listener: () -> Unit) = Unit

  @RequiresBackgroundThread(generateAssertion = false /* IJPL-115548 */)
  @Throws(IOException::class, ProcessCanceledException::class)
  abstract fun <T> execute(indicator: ProgressIndicator, request: GithubApiRequest<T>): T

  @TestOnly
  @RequiresBackgroundThread(generateAssertion = false /* IJPL-115548 */)
  @Throws(IOException::class, ProcessCanceledException::class)
  fun <T> execute(request: GithubApiRequest<T>): T = execute(EmptyProgressIndicator(), request)

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

  abstract class Base(
    private val githubSettings: GithubSettings,
    clock: Clock = Clock.systemUTC(),
  ) : GithubApiRequestExecutor() {

    @VisibleForTesting
    internal val rateLimitState = RateLimitState(clock)

    private val graphQLGate = Semaphore(MAX_CONCURRENT_GRAPHQL_REQUESTS)

    protected fun <T> RequestBuilder.execute(request: GithubApiRequest<T>, indicator: ProgressIndicator): T =
      executeRequest(request, indicator) { processor -> connect(processor) }

    @VisibleForTesting
    internal fun <T> executeRequest(
      request: GithubApiRequest<T>,
      indicator: ProgressIndicator,
      connect: (HttpRequests.RequestProcessor<T>) -> T,
    ): T {
      indicator.checkCanceled()
      val resource = RateLimitState.resourceOf(request)
      // no point in sending a request before the reset instant, the budget is per user
      rateLimitState.checkNotExhausted(resource)

      try {
        LOG.debug("Request: ${request.url} ${request.operationName} : Connecting")
        val activity = GHPRStatisticsCollector.logApiRequestStart(request.operation)
        return withGraphQLGate(request, indicator) {
          connect {
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
              remaining = connection.getHeaderFieldInt(HEADER_RATE_LIMIT_REMAINING, -1),
              resourceName = connection.getHeaderField(HEADER_RATE_LIMIT_RESOURCE) ?: "unknown",
              statusCode = connection.responseCode,
            )

            updateRateLimitState(connection, resource)
            checkResponseCode(connection, resource)
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

            // REST /rate_limit does not reflect the GraphQL budget, only this block does
            val remaining = rates?.remaining
            if (remaining != null) {
              rateLimitState.update(RateLimitState.RESOURCE_GRAPHQL, remaining, rates.resetAt?.toInstant())
            }

            LOG.debug("Request: ${connection.requestMethod} ${connection.url} : Result extracted")
            result
          }
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

    private fun <T> withGraphQLGate(request: GithubApiRequest<*>, indicator: ProgressIndicator, body: () -> T): T {
      if (request !is GithubApiRequest.Post.GQLQuery) return body()
      while (!graphQLGate.tryAcquire(GATE_POLL_INTERVAL_MS, TimeUnit.MILLISECONDS)) {
        indicator.checkCanceled()
      }
      try {
        return body()
      }
      finally {
        graphQLGate.release()
      }
    }

    private fun updateRateLimitState(connection: HttpURLConnection, requestResource: String) {
      val remaining = connection.getHeaderFieldInt(HEADER_RATE_LIMIT_REMAINING, -1)
      if (remaining < 0) return
      val resource = connection.getHeaderField(HEADER_RATE_LIMIT_RESOURCE) ?: requestResource
      rateLimitState.update(resource, remaining, getResetInstant(connection))
    }

    private fun getResetInstant(connection: HttpURLConnection): Instant? {
      val resetEpochSeconds = connection.getHeaderFieldLong(HEADER_RATE_LIMIT_RESET, -1)
      if (resetEpochSeconds > 0) return Instant.ofEpochSecond(resetEpochSeconds)
      val retryAfterSeconds = connection.getHeaderFieldLong(HEADER_RETRY_AFTER, -1)
      if (retryAfterSeconds > 0) return rateLimitState.now().plusSeconds(retryAfterSeconds)
      return null
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
    private fun checkResponseCode(connection: HttpURLConnection, requestResource: String) {
      if (connection.responseCode < 400) return
      val statusLine = "${connection.responseCode} ${connection.responseMessage}"
      val errorText = getErrorText(connection)
      LOG.debug("Request: ${connection.requestMethod} ${connection.url} : Error ${statusLine} body:\n${errorText}")

      val jsonError = errorText?.let { getJsonError(connection, it) }
      jsonError ?: LOG.debug("Request: ${connection.requestMethod} ${connection.url} : Unable to parse JSON error")

      throw when (connection.responseCode) {
        HttpURLConnection.HTTP_UNAUTHORIZED,
        HttpURLConnection.HTTP_PAYMENT_REQUIRED,
        HttpURLConnection.HTTP_FORBIDDEN,
          -> {
          if (jsonError?.containsReasonMessage("API rate limit exceeded") == true) {
            val resource = connection.getHeaderField(HEADER_RATE_LIMIT_RESOURCE) ?: requestResource
            val resetAt = rateLimitState.markExhausted(resource, getResetInstant(connection))
            GithubRateLimitExceededException(jsonError.presentableError, resetAt)
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

    companion object {
      @VisibleForTesting
      internal const val MAX_CONCURRENT_GRAPHQL_REQUESTS = 8
      private const val GATE_POLL_INTERVAL_MS = 100L

      private const val HEADER_RATE_LIMIT_REMAINING = "x-ratelimit-remaining"
      private const val HEADER_RATE_LIMIT_RESOURCE = "x-ratelimit-resource"
      private const val HEADER_RATE_LIMIT_RESET = "x-ratelimit-reset"
      private const val HEADER_RETRY_AFTER = "Retry-After"
    }
  }

  @VisibleForTesting
  internal class RateLimitState(private val clock: Clock) {
    private val exhaustedUntil = ConcurrentHashMap<String, Instant>()

    fun now(): Instant = clock.instant()

    fun exhaustedUntil(resource: String): Instant? {
      val until = exhaustedUntil[resource] ?: return null
      if (!until.isAfter(now())) {
        exhaustedUntil.remove(resource, until)
        return null
      }
      return until
    }

    @Throws(GithubRateLimitExceededException::class)
    fun checkNotExhausted(resource: String) {
      val until = exhaustedUntil(resource) ?: return
      val message = GithubBundle.message("request.rate.limit.exceeded.resets.at", RESET_TIME_FORMAT.format(until))
      throw GithubRateLimitExceededException(message, until)
    }

    fun update(resource: String, remaining: Int, resetAt: Instant?) {
      if (remaining > 0) {
        exhaustedUntil.remove(resource)
      }
      else {
        markExhausted(resource, resetAt)
      }
    }

    fun markExhausted(resource: String, resetAt: Instant?): Instant {
      val now = now()
      val until = resetAt?.takeIf { it.isAfter(now) }
                  ?: exhaustedUntil[resource]?.takeIf { it.isAfter(now) }
                  ?: now.plus(DEFAULT_COOLDOWN)
      exhaustedUntil[resource] = until
      return until
    }

    companion object {
      const val RESOURCE_GRAPHQL = "graphql"
      const val RESOURCE_CORE = "core"

      @VisibleForTesting
      internal val DEFAULT_COOLDOWN: Duration = Duration.ofMinutes(1)

      private val RESET_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

      fun resourceOf(request: GithubApiRequest<*>): String =
        if (request is GithubApiRequest.Post.GQLQuery) RESOURCE_GRAPHQL else RESOURCE_CORE
    }
  }

  @Service
  class Factory {

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

    @VisibleForTesting
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
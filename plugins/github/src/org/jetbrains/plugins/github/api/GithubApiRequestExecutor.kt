// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api

import com.google.gson.JsonParseException
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.util.ThrowableConvertor
import com.intellij.util.io.HttpRequests
import com.intellij.util.io.HttpSecurityUtil
import com.intellij.util.io.RequestBuilder
import org.jetbrains.annotations.CalledInAny
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.github.api.data.GithubErrorMessage
import org.jetbrains.plugins.github.exceptions.*
import org.jetbrains.plugins.github.util.GithubSettings
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.net.HttpURLConnection
import java.util.function.Supplier

/**
 * Executes API requests taking care of authentication, headers, proxies, timeouts, etc.
 */
sealed class GithubApiRequestExecutor {
  @Throws(IOException::class, ProcessCanceledException::class)
  abstract fun <T> execute(indicator: ProgressIndicator, request: GithubApiRequest<T>): T

  @TestOnly
  @Throws(IOException::class, ProcessCanceledException::class)
  fun <T> execute(request: GithubApiRequest<T>): T = execute(EmptyProgressIndicator(), request)

  class WithTokenAuth internal constructor(githubSettings: GithubSettings,
                                           private val token: String,
                                           private val useProxy: Boolean) : Base(githubSettings) {
    @Throws(IOException::class, ProcessCanceledException::class)
    override fun <T> execute(indicator: ProgressIndicator, request: GithubApiRequest<T>): T {
      indicator.checkCanceled()
      return createRequestBuilder(request)
        .tuner { connection -> connection.addRequestProperty(HttpSecurityUtil.AUTHORIZATION_HEADER_NAME, "Token $token") }
        .useProxy(useProxy)
        .execute(request, indicator)
    }
  }

  class WithBasicAuth internal constructor(githubSettings: GithubSettings,
                                           private val login: String,
                                           private val password: CharArray,
                                           private val twoFactorCodeSupplier: Supplier<String?>) : Base(githubSettings) {
    private var twoFactorCode: String? = null

    @Throws(IOException::class, ProcessCanceledException::class)
    override fun <T> execute(indicator: ProgressIndicator, request: GithubApiRequest<T>): T {
      indicator.checkCanceled()
      val basicHeaderValue = HttpSecurityUtil.createBasicAuthHeaderValue(login, password)
      return executeWithBasicHeader(indicator, request, basicHeaderValue)
    }

    private fun <T> executeWithBasicHeader(indicator: ProgressIndicator, request: GithubApiRequest<T>, header: String): T {
      indicator.checkCanceled()
      return try {
        createRequestBuilder(request)
          .tuner { connection ->
            connection.addRequestProperty(HttpSecurityUtil.AUTHORIZATION_HEADER_NAME, "Basic $header")
            twoFactorCode?.let { connection.addRequestProperty(OTP_HEADER_NAME, it) }
          }
          .execute(request, indicator)
      }
      catch (e: GithubTwoFactorAuthenticationException) {
        twoFactorCode = twoFactorCodeSupplier.get() ?: throw e
        executeWithBasicHeader(indicator, request, header)
      }
    }
  }

  abstract class Base(private val githubSettings: GithubSettings) : GithubApiRequestExecutor() {
    protected fun <T> RequestBuilder.execute(request: GithubApiRequest<T>, indicator: ProgressIndicator): T {
      indicator.checkCanceled()
      try {
        return connect {
          val connection = it.connection as HttpURLConnection
          if (request is GithubApiRequest.WithBody) {
            LOG.debug("Request: ${connection.requestMethod} ${connection.url} with body:\n${request.body}")
            it.write(request.body)
          }
          else {
            LOG.debug("Request: ${connection.requestMethod} ${connection.url}")
          }
          checkResponseCode(connection)
          indicator.checkCanceled()

          val result = request.extractResult(createResponse(it, indicator))
          LOG.debug("Request: ${connection.requestMethod} ${connection.url}: Success")
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
        is GithubApiRequest.Post -> HttpRequests.post(request.url, request.bodyMimeType)
        is GithubApiRequest.Patch -> HttpRequests.patch(request.url, request.bodyMimeType)
        is GithubApiRequest.Head -> HttpRequests.head(request.url)
        is GithubApiRequest.Delete -> HttpRequests.delete(request.url)
        else -> throw UnsupportedOperationException("${request.javaClass} is not supported")
      }
        .connectTimeout(githubSettings.connectionTimeout)
        .userAgent("Intellij IDEA Github Plugin")
        .throwStatusCodeException(false)
        .forceHttps(true)
        .accept(request.acceptMimeType)
    }

    @Throws(IOException::class)
    private fun checkResponseCode(connection: HttpURLConnection) {
      if (connection.responseCode < 400) return
      val statusLine = "${connection.responseCode} ${connection.responseMessage}"
      val errorText = getErrorText(connection)
      val jsonError = getJsonError(connection, errorText)

      LOG.debug("Request: ${connection.requestMethod} ${connection.url}: Error ${statusLine} body:\n${errorText}")
      throw when (connection.responseCode) {
        HttpURLConnection.HTTP_UNAUTHORIZED,
        HttpURLConnection.HTTP_PAYMENT_REQUIRED,
        HttpURLConnection.HTTP_FORBIDDEN -> {
          val otpHeader = connection.getHeaderField(OTP_HEADER_NAME)
          if (otpHeader != null && otpHeader.contains("required", true)) {
            GithubTwoFactorAuthenticationException(jsonError?.message ?: errorText)
          }
          else if (jsonError?.containsReasonMessage("API rate limit exceeded") == true) {
            GithubRateLimitExceededException(jsonError.message)
          }
          else GithubAuthenticationException("Request response: " + (jsonError?.message ?: errorText))
        }
        else -> {
          if (jsonError != null) {
            GithubStatusCodeException("$statusLine - ${jsonError.message}", jsonError, connection.responseCode)
          }
          else {
            GithubStatusCodeException("$statusLine - ${errorText}", connection.responseCode)
          }
        }
      }
    }

    private fun getErrorText(connection: HttpURLConnection): String {
      return connection.errorStream?.let { InputStreamReader(it).use { it.readText() } } ?: ""
    }

    private fun getJsonError(connection: HttpURLConnection, errorText: String): GithubErrorMessage? {
      if (!connection.contentType.startsWith(GithubApiContentHelper.JSON_MIME_TYPE)) return null
      return try {
        return GithubApiContentHelper.fromJson(errorText)
      }
      catch (jse: JsonParseException) {
        LOG.debug(jse)
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

  class Factory internal constructor(private val githubSettings: GithubSettings) {
    @CalledInAny
    fun create(token: String): WithTokenAuth {
      return create(token, true)
    }

    @CalledInAny
    fun create(token: String, useProxy: Boolean = true): WithTokenAuth {
      return GithubApiRequestExecutor.WithTokenAuth(githubSettings, token, useProxy)
    }

    @CalledInAny
    internal fun create(login: String, password: CharArray, twoFactorCodeSupplier: Supplier<String?>): WithBasicAuth {
      return GithubApiRequestExecutor.WithBasicAuth(githubSettings, login, password, twoFactorCodeSupplier)
    }

    companion object {
      @JvmStatic
      fun getInstance(): Factory = service()
    }
  }

  companion object {
    private val LOG = logger<GithubApiRequestExecutor>()

    private const val OTP_HEADER_NAME = "X-GitHub-OTP"
  }
}
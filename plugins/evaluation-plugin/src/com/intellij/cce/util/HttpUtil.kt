package com.intellij.cce.util

import com.intellij.openapi.progress.runBlockingCancellable
import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess

private val httpClient: HttpClient by lazy {
  val log = com.intellij.openapi.diagnostic.Logger.getInstance("com.intellij.cce.util.httpClient")
  HttpClient(Java) {
    expectSuccess = true
    install(HttpRequestRetry) {
      retryOnExceptionOrServerErrors(maxRetries = 10)
      exponentialDelay()
    }
    install(HttpTimeout) {
      connectTimeoutMillis = System.getProperty("idea.connection.timeout")?.toLongOrNull() ?: 10_000
    }
    install(Logging) {
      logger = object: Logger {
        override fun log(message: String) {
          log.info(message)
        }
      }
      level = LogLevel.INFO
    }
  }
}

private suspend fun httpGetSuspend(url: String, authToken: String?): ByteArray {
  val response = httpClient.get(url) {
    headers {
      if (authToken != null) {
        append(HttpHeaders.Authorization, "Bearer $authToken")
      }
    }
  }

  if (!response.status.isSuccess()) {
    error("failed to access $url. status=${response.status}")
  }

  return response.bodyAsBytes()
}

fun httpGet(url: String, authToken: String?): ByteArray {
  //todo refac eval framework to make it work with suspend funs
  return runBlockingCancellable {
    httpGetSuspend(url, authToken)
  }
}
package com.intellij.cce.util

import com.intellij.openapi.progress.runBlockingCancellable
import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess

private val httpClient: HttpClient by lazy {
  HttpClient(Java) {
    expectSuccess = true
    install(HttpRequestRetry) {
      retryOnExceptionOrServerErrors(maxRetries = 3)
      exponentialDelay()
    }
    install(HttpTimeout) {
      connectTimeoutMillis = System.getProperty("idea.connection.timeout")?.toLongOrNull() ?: 10_000
    }
  }
}

private suspend fun httpGetSuspend(url: String, authToken: String): String {
  val response = httpClient.get(url) {
    headers {
      append(HttpHeaders.Authorization, "Bearer $authToken")
    }
  }

  if (!response.status.isSuccess()) {
    error("failed to access $url. status=${response.status}")
  }

  return response.bodyAsText()
}

fun httpGet(url: String, authToken: String): String {
  //todo refac eval framework to make it work with suspend funs
  return runBlockingCancellable {
    httpGetSuspend(url, authToken)
  }
}
// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.http

import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.content.OutputStreamContent
import org.jetbrains.annotations.ApiStatus
import java.io.OutputStream

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

sealed interface ContentType {
  companion object {
    // ktor type for protobuf uses `protobuf`, but go otlp requires "x-" prefix
    val XProtobuf: ContentType = ContentTypeImpl(io.ktor.http.ContentType("application", "x-protobuf"))
  }
}

private class ContentTypeImpl(@JvmField val contentType: io.ktor.http.ContentType) : ContentType

@ApiStatus.Internal
suspend fun httpPost(url: String, contentLength: Long, contentType: ContentType, body: suspend OutputStream.() -> Unit, authorizationHeader: String? = null) {
  httpClient.post(url) {
    headers {
      authorizationHeader?.let { append("Authorization", it) }
    }
    setBody(OutputStreamContent(body, (contentType as ContentTypeImpl).contentType, status = null, contentLength))
  }
}

@ApiStatus.Internal
suspend fun httpPost(url: String, contentType: ContentType, body: ByteArray, authorizationHeader: String? = null) {
  httpClient.post(url) {
    headers {
      authorizationHeader?.let { append("Authorization", it) }
    }
    setBody(ByteArrayContent(body, (contentType as ContentTypeImpl).contentType))
  }
}

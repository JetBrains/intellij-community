// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.http

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.content.ByteArrayContent
import io.ktor.http.content.OutputStreamContent
import org.jetbrains.annotations.ApiStatus
import java.io.OutputStream

@get:ApiStatus.Internal
@get:ApiStatus.Experimental
val httpClient: HttpClient by lazy {
  // HttpTimeout is not used - CIO engine handles that
  HttpClient(CIO) {
    expectSuccess = true

    install(HttpRequestRetry) {
      retryOnExceptionOrServerErrors(maxRetries = 3)
      exponentialDelay()
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
@ApiStatus.Experimental
suspend fun httpPost(url: String, contentLength: Long, contentType: ContentType, body: suspend OutputStream.() -> Unit) {
  httpClient.post(url) {
    setBody(OutputStreamContent(contentType = (contentType as ContentTypeImpl).contentType,
                                contentLength = contentLength,
                                body = body))
  }
}

@ApiStatus.Internal
@ApiStatus.Experimental
suspend fun httpPost(url: String, contentType: ContentType, body: ByteArray) {
  httpClient.post(url) {
    setBody(ByteArrayContent(body, contentType = (contentType as ContentTypeImpl).contentType))
  }
}
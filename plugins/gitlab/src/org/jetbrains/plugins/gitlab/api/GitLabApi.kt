// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api

import com.intellij.collaboration.api.HttpClientFactory
import com.intellij.collaboration.api.HttpRequestConfigurer
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest

class GitLabApi(private val clientFactory: HttpClientFactory,
                private val requestConfigurer: HttpRequestConfigurer) {

  val client: HttpClient
    get() = clientFactory.createClient()

  fun request(server: GitLabServerPath): HttpRequest.Builder =
    request(server.gqlApiUri)

  fun request(uri: String): HttpRequest.Builder =
    request(URI.create(uri))

  fun request(uri: URI): HttpRequest.Builder =
    HttpRequest.newBuilder(uri).apply(requestConfigurer::configure)
}
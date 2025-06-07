// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.http

import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.sun.net.httpserver.HttpServer
import org.jetbrains.annotations.TestOnly
import java.net.InetSocketAddress

private const val LOCALHOST: String = "127.0.0.1"

@TestOnly
fun localhostHttpServer(): TestFixture<HttpServer> = testFixture {
  val server = HttpServer.create()
  server.bind(InetSocketAddress(LOCALHOST, 0), 1)
  server.start()
  initialized(server) {
    server.stop(0)
  }
}

@get:TestOnly
val HttpServer.url: String
  get() = @Suppress("HttpUrlsUsage") "http://${LOCALHOST}:${this.address.port}"

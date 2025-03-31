// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistics

import com.sun.net.httpserver.HttpServer
import java.io.OutputStream
import java.net.InetSocketAddress

internal class TestHttpServerProcessing(val config: String) {
  private var assignedPort: Int? = null
  private var server: HttpServer = createHttpServer()

  private fun createHttpServer(): HttpServer {
    val server = HttpServer.create(InetSocketAddress(0), 0)
    assignedPort = server.address.port
    // Define a handler for serving the content
    server.createContext("/") { exchange ->
      // Send HTTP headers with a 200 status code and the content length
      exchange.sendResponseHeaders(200, config.length.toLong())
      // Write content to the HTTP response
      val outputStream: OutputStream = exchange.responseBody
      outputStream.write(config.toByteArray())
      outputStream.close()
      exchange.close()
    }
    return server
  }

  fun serverStart() {
    server.start()
  }

  fun serverStop() {
    server.stop(0)
  }

  fun getUrl(): String = "http://localhost:${assignedPort}/"
}
// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.eel

import com.intellij.platform.eel.download
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.localEel
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.file.Path
import kotlin.io.path.readBytes
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

@TestApplication
class EelLocalHttpApiTest {
  private val body = ByteArray(300_000) { it.toByte() }

  @Test
  fun `download writes the response body`(@TempDir dir: Path): Unit = timeoutRunBlocking {
    withServer { server ->
      server.createContext("/file") { exchange ->
        exchange.sendResponseHeaders(200, body.size.toLong())
        exchange.responseBody.use { it.write(body) }
      }
      val target = dir.resolve("file.bin")
      localEel.http.download(target.asEelPath(), "${server.url}/file").eelIt()
      assertContentEquals(body, target.readBytes())
    }
  }

  @Test
  fun `download throws on an http error`(@TempDir dir: Path): Unit = timeoutRunBlocking {
    withServer { server ->
      server.createContext("/missing") { exchange ->
        exchange.sendResponseHeaders(404, -1)
        exchange.close()
      }
      val target = dir.resolve("missing.bin")
      assertFailsWith<IOException> {
        localEel.http.download(target.asEelPath(), "${server.url}/missing").eelIt()
      }
    }
  }

  private suspend fun withServer(block: suspend (HttpServer) -> Unit) {
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.start()
    try {
      block(server)
    }
    finally {
      server.stop(0)
    }
  }

  private val HttpServer.url: String get() = "http://127.0.0.1:${address.port}"
}

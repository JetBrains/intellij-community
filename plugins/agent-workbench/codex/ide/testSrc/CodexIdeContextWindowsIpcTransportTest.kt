// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.ide

import com.intellij.openapi.util.SystemInfoRt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Timeout(value = 2, unit = TimeUnit.MINUTES)
class CodexIdeContextWindowsIpcTransportTest {
  @Test
  fun idleClientDoesNotBlockNextRequest(): Unit = runBlocking(Dispatchers.Default) {
    assumeTrue(SystemInfoRt.isWindows)
    val pipePath = """\\.\pipe\codex-ipc-test-${System.nanoTime()}"""

    withRunningServer(
      pipePath = pipePath,
      settings = CodexIdeContextIpcTransportSettings(maxActiveClients = 1, readTimeout = 100.milliseconds),
    ) {
      openPipe(pipePath).use {
        var response: String? = null
        withTimeout(5.seconds) {
          while (response == null) {
            response = runCatching { requestResponse(pipePath) }.getOrNull()
            if (response == null) {
              delay(20.milliseconds)
            }
          }
        }

        assertThat(response!!).contains("\"resultType\":\"success\"")
      }
    }
  }

  @Test
  fun closeStopsServingPipe(): Unit = runBlocking(Dispatchers.Default) {
    assumeTrue(SystemInfoRt.isWindows)
    val pipePath = """\\.\pipe\codex-ipc-test-${System.nanoTime()}"""
    val transport = WindowsCodexIdeContextIpcTransport(pipePath = pipePath)
    val protocol = CodexIdeContextIpcProtocol(contextCollector = { context() })
    val serverJob = launch { transport.serve(protocol) }

    try {
      withTimeout(5.seconds) {
        while (runCatching { openPipe(pipePath).use { } }.isFailure) {
          delay(10.milliseconds)
        }
      }

      transport.close()

      withTimeout(5.seconds) {
        serverJob.join()
      }
    }
    finally {
      transport.close()
      serverJob.cancelAndJoin()
    }
  }

  private suspend fun withRunningServer(
    pipePath: String,
    settings: CodexIdeContextIpcTransportSettings = CodexIdeContextIpcTransportSettings(),
    block: suspend () -> Unit,
  ): Unit = coroutineScope {
    val transport = WindowsCodexIdeContextIpcTransport(pipePath = pipePath, settings = settings)
    val protocol = CodexIdeContextIpcProtocol(contextCollector = { context() })
    val serverJob = launch { transport.serve(protocol) }

    try {
      block()
    }
    finally {
      transport.close()
      serverJob.cancelAndJoin()
    }
  }

  private suspend fun requestResponse(pipePath: String): String {
    openPipe(pipePath).use { pipe ->
      withContext(Dispatchers.IO) {
        writeFrame(pipe, request())
      }

      val response = coroutineScope {
        val readJob = async(Dispatchers.IO) { readFrame(pipe) }
        try {
          withTimeout(5.seconds) { readJob.await() }
        }
        finally {
          pipe.close()
          readJob.cancelAndJoin()
        }
      }
      return response.toString(StandardCharsets.UTF_8)
    }
  }

  private suspend fun openPipe(pipePath: String): RandomAccessFile {
    val deadlineNanos = System.nanoTime() + 5.seconds.inWholeNanoseconds
    var lastException: IOException? = null
    while (System.nanoTime() < deadlineNanos) {
      try {
        return withContext(Dispatchers.IO) { RandomAccessFile(pipePath, "rw") }
      }
      catch (e: IOException) {
        lastException = e
        delay(10.milliseconds)
      }
    }
    throw IOException("Timed out opening Codex IDE context named pipe $pipePath", lastException)
  }

  private fun writeFrame(pipe: RandomAccessFile, payload: ByteArray) {
    val lengthBytes = ByteBuffer.allocate(Int.SIZE_BYTES)
      .order(ByteOrder.LITTLE_ENDIAN)
      .putInt(payload.size)
      .array()
    pipe.write(lengthBytes)
    pipe.write(payload)
  }

  private fun readFrame(pipe: RandomAccessFile): ByteArray {
    val lengthBytes = ByteArray(Int.SIZE_BYTES)
    pipe.readFully(lengthBytes)
    val length = ByteBuffer.wrap(lengthBytes).order(ByteOrder.LITTLE_ENDIAN).int
    val payload = ByteArray(length)
    pipe.readFully(payload)
    return payload
  }

  private fun request(): ByteArray {
    return """
      {"type":"request","requestId":"request-1","sourceClientId":"codex-tui","version":0,"method":"ide-context","params":{"workspaceRoot":"/repo"}}
    """.trimIndent().toByteArray(StandardCharsets.UTF_8)
  }

  private fun context(): CodexIdeContext {
    return CodexIdeContext(
      activeFile = null,
      openTabs = listOf(CodexIdeFileDescriptor(label = "README.md", path = "README.md", fsPath = "/repo/README.md")),
    )
  }
}

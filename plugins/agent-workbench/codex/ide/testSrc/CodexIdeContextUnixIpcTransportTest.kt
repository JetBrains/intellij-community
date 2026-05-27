// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.ide

import com.intellij.openapi.util.SystemInfoRt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.net.ConnectException
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Timeout(value = 2, unit = TimeUnit.MINUTES)
class CodexIdeContextUnixIpcTransportTest {
  @Test
  fun servesCodexIdeContextRequest(@TempDir tempDir: Path): Unit = runBlocking(Dispatchers.Default) {
    assumeFalse(SystemInfoRt.isWindows)
    val socketPath = tempDir.resolve("codex.sock")

    withRunningServer(socketPath) {
      val response = requestResponse(socketPath)

      assertThat(response).contains("\"resultType\":\"success\"")
      assertThat(response).contains("\"openTabs\":[{\"label\":\"README.md\",\"path\":\"README.md\"")
    }
  }

  @Test
  fun keepsServingAfterMalformedClientFrame(@TempDir tempDir: Path): Unit = runBlocking(Dispatchers.Default) {
    assumeFalse(SystemInfoRt.isWindows)
    val socketPath = tempDir.resolve("codex.sock")

    withRunningServer(socketPath) {
      openClient(socketPath).use { channel ->
        writeFully(channel, ByteBuffer.allocate(Int.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN).putInt(-1).flip())
      }

      val response = requestResponse(socketPath)

      assertThat(response).contains("\"resultType\":\"success\"")
    }
  }

  @Test
  fun keepsServingAfterOversizedClientFrame(@TempDir tempDir: Path): Unit = runBlocking(Dispatchers.Default) {
    assumeFalse(SystemInfoRt.isWindows)
    val socketPath = tempDir.resolve("codex.sock")

    withRunningServer(socketPath, settings = CodexIdeContextIpcTransportSettings(maxRequestFrameBytes = 256)) {
      openClient(socketPath).use { channel ->
        writeFully(channel, ByteBuffer.allocate(Int.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN).putInt(257).flip())
      }

      val response = requestResponse(socketPath)

      assertThat(response).contains("\"resultType\":\"success\"")
    }
  }

  @Test
  fun manyIdleClientsDoNotBlockRequestsOrTimeOut(@TempDir tempDir: Path): Unit = runBlocking(Dispatchers.Default) {
    assumeFalse(SystemInfoRt.isWindows)
    val socketPath = tempDir.resolve("codex.sock")

    withRunningServer(
      socketPath = socketPath,
      settings = CodexIdeContextIpcTransportSettings(maxActiveClients = 128, readTimeout = 100.milliseconds),
    ) {
      val idleClients = ArrayList<SocketChannel>()
      try {
        repeat(64) {
          idleClients += openClient(socketPath)
        }

        val response = requestResponse(socketPath)
        assertThat(response).contains("\"resultType\":\"success\"")

        val firstIdleClient = idleClients.first()
        writeFrame(firstIdleClient, request())
        assertThat(readFrame(firstIdleClient).toString(StandardCharsets.UTF_8)).contains("\"resultType\":\"success\"")
      }
      finally {
        idleClients.forEach(::closeIgnoringErrors)
      }
    }
  }

  @Test
  fun partialClientFrameTimesOutWithoutStoppingServer(@TempDir tempDir: Path): Unit = runBlocking(Dispatchers.Default) {
    assumeFalse(SystemInfoRt.isWindows)
    val socketPath = tempDir.resolve("codex.sock")

    withRunningServer(socketPath, settings = CodexIdeContextIpcTransportSettings(readTimeout = 100.milliseconds)) {
      openClient(socketPath).use { channel ->
        writeFully(channel, ByteBuffer.wrap(byteArrayOf(1, 0)))
        assertEventuallyClosed(channel)
      }

      val response = requestResponse(socketPath)

      assertThat(response).contains("\"resultType\":\"success\"")
    }
  }

  @Test
  fun servesPipelinedClientRequestsSequentially(@TempDir tempDir: Path): Unit = runBlocking(Dispatchers.Default) {
    assumeFalse(SystemInfoRt.isWindows)
    val socketPath = tempDir.resolve("codex.sock")

    withRunningServer(socketPath) {
      openClient(socketPath).use { channel ->
        writeFrame(channel, request("request-1"))
        writeFrame(channel, request("request-2"))

        assertThat(readFrame(channel).toString(StandardCharsets.UTF_8)).contains("\"requestId\":\"request-1\"")
        assertThat(readFrame(channel).toString(StandardCharsets.UTF_8)).contains("\"requestId\":\"request-2\"")
      }
    }
  }

  @Test
  fun failedSecondTransportDoesNotDeleteLiveSocket(@TempDir tempDir: Path): Unit = runBlocking(Dispatchers.Default) {
    assumeFalse(SystemInfoRt.isWindows)
    val socketPath = tempDir.resolve("codex.sock")

    withRunningServer(socketPath) {
      UnixCodexIdeContextIpcTransport(socketPath = socketPath).use { secondTransport ->
        val failure = runCatching { withTimeout(5.seconds) { secondTransport.serve(protocol()) } }.exceptionOrNull()

        assertThat(failure).isInstanceOf(CodexIdeContextIpcAddressInUseException::class.java)
        secondTransport.close()
        assertThat(Files.exists(socketPath)).isTrue()
        assertThat(requestResponse(socketPath)).contains("\"resultType\":\"success\"")
      }
    }
  }

  @Test
  fun closeStopsServingSelector(@TempDir tempDir: Path): Unit = runBlocking(Dispatchers.Default) {
    assumeFalse(SystemInfoRt.isWindows)
    val socketPath = tempDir.resolve("codex.sock")
    val transport = UnixCodexIdeContextIpcTransport(socketPath = socketPath)
    val serverJob = launch { transport.serve(protocol()) }

    try {
      withTimeout(5.seconds) {
        while (!Files.exists(socketPath)) {
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

  @Test
  fun closeClosesActiveClients(@TempDir tempDir: Path): Unit = runBlocking(Dispatchers.Default) {
    assumeFalse(SystemInfoRt.isWindows)
    val socketPath = tempDir.resolve("codex.sock")
    val transport = UnixCodexIdeContextIpcTransport(socketPath = socketPath)
    val serverJob = launch { transport.serve(protocol()) }

    try {
      withTimeout(5.seconds) {
        while (!Files.exists(socketPath)) {
          delay(10.milliseconds)
        }
      }
      openClient(socketPath).use { channel ->
        transport.close()

        assertEventuallyClosed(channel)
      }
      withTimeout(5.seconds) {
        serverJob.join()
      }
    }
    finally {
      transport.close()
      serverJob.cancelAndJoin()
    }
  }

  @Test
  fun createsOwnerOnlySocketParentAndSocket(@TempDir tempDir: Path): Unit = runBlocking(Dispatchers.Default) {
    assumeFalse(SystemInfoRt.isWindows)
    val socketParent = tempDir.resolve("codex-ipc")
    val socketPath = socketParent.resolve("ipc-test.sock")

    withRunningServer(socketPath) {
      val parentPermissions = Files.getPosixFilePermissions(socketParent)
      val socketPermissions = Files.getPosixFilePermissions(socketPath)

      assertThat(parentPermissions).containsExactlyInAnyOrder(
        PosixFilePermission.OWNER_READ,
        PosixFilePermission.OWNER_WRITE,
        PosixFilePermission.OWNER_EXECUTE,
      )
      assertThat(socketPermissions).containsExactlyInAnyOrder(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
    }
  }

  @Test
  fun repairsPermissiveSocketParent(@TempDir tempDir: Path): Unit = runBlocking(Dispatchers.Default) {
    assumeFalse(SystemInfoRt.isWindows)
    val socketParent = tempDir.resolve("codex-ipc")
    Files.createDirectories(socketParent)
    Files.setPosixFilePermissions(socketParent, PosixFilePermissions.fromString("rwxrwxrwx"))
    val socketPath = socketParent.resolve("ipc-test.sock")

    withRunningServer(socketPath) {
      assertThat(Files.getPosixFilePermissions(socketParent)).containsExactlyInAnyOrder(
        PosixFilePermission.OWNER_READ,
        PosixFilePermission.OWNER_WRITE,
        PosixFilePermission.OWNER_EXECUTE,
      )
      assertThat(requestResponse(socketPath)).contains("\"resultType\":\"success\"")
    }
  }

  private suspend fun withRunningServer(
    socketPath: Path,
    settings: CodexIdeContextIpcTransportSettings = CodexIdeContextIpcTransportSettings(),
    block: suspend () -> Unit,
  ): Unit = coroutineScope {
    val transport = UnixCodexIdeContextIpcTransport(socketPath = socketPath, settings = settings)
    val serverJob = launch { transport.serve(protocol()) }

    try {
      withTimeout(5.seconds) {
        while (!Files.exists(socketPath)) {
          delay(10.milliseconds)
        }
      }

      block()
    }
    finally {
      transport.close()
      serverJob.cancelAndJoin()
    }
  }

  private suspend fun requestResponse(socketPath: Path): String = withTimeout(5.seconds) {
    openClient(socketPath).use { channel ->
      writeFrame(channel, request())
      readFrame(channel).toString(StandardCharsets.UTF_8)
    }
  }

  private suspend fun openClient(socketPath: Path): SocketChannel = withTimeout(5.seconds) {
    var connectedChannel: SocketChannel? = null
    while (connectedChannel == null) {
      val channel = SocketChannel.open(StandardProtocolFamily.UNIX)
      try {
        channel.configureBlocking(false)
        channel.connect(UnixDomainSocketAddress.of(socketPath))
        while (!channel.finishConnect()) {
          delay(10.milliseconds)
        }
        connectedChannel = channel
      }
      catch (_: ConnectException) {
        closeIgnoringErrors(channel)
        delay(10.milliseconds)
      }
      catch (e: Throwable) {
        closeIgnoringErrors(channel)
        throw e
      }
    }
    connectedChannel
  }

  private suspend fun writeFrame(channel: SocketChannel, payload: ByteArray) {
    writeFully(channel, ByteBuffer.allocate(Int.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN).putInt(payload.size).flip())
    writeFully(channel, ByteBuffer.wrap(payload))
  }

  private suspend fun writeFully(channel: SocketChannel, buffer: ByteBuffer) {
    while (buffer.hasRemaining()) {
      if (channel.write(buffer) == 0) {
        delay(10.milliseconds)
      }
    }
  }

  private suspend fun readFrame(channel: SocketChannel): ByteArray {
    val lengthBuffer = ByteBuffer.allocate(Int.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
    readFully(channel, lengthBuffer)
    lengthBuffer.flip()
    val payload = ByteArray(lengthBuffer.int)
    readFully(channel, ByteBuffer.wrap(payload))
    return payload
  }

  private suspend fun readFully(channel: SocketChannel, buffer: ByteBuffer) {
    while (buffer.hasRemaining()) {
      val bytesRead = channel.read(buffer)
      check(bytesRead >= 0) { "Socket closed before frame was complete" }
      if (bytesRead == 0) {
        delay(10.milliseconds)
      }
    }
  }

  private suspend fun assertEventuallyClosed(channel: SocketChannel) {
    val buffer = ByteBuffer.allocate(1)
    withTimeout(5.seconds) {
      while (true) {
        val bytesRead = try {
          channel.read(buffer)
        }
        catch (_: IOException) {
          return@withTimeout
        }
        if (bytesRead < 0) {
          return@withTimeout
        }
        buffer.clear()
        delay(10.milliseconds)
      }
    }
  }

  private fun closeIgnoringErrors(channel: SocketChannel) {
    try {
      channel.close()
    }
    catch (_: Throwable) {
    }
  }

  private fun protocol(): CodexIdeContextIpcProtocol {
    return CodexIdeContextIpcProtocol(contextCollector = { context() })
  }

  private fun request(requestId: String = "request-1"): ByteArray {
    return """
      {"type":"request","requestId":"$requestId","sourceClientId":"codex-tui","version":0,"method":"ide-context","params":{"workspaceRoot":"/repo"}}
    """.trimIndent().toByteArray(StandardCharsets.UTF_8)
  }

  private fun context(): CodexIdeContext {
    return CodexIdeContext(
      activeFile = null,
      openTabs = listOf(CodexIdeFileDescriptor(label = "README.md", path = "README.md", fsPath = "/repo/README.md")),
    )
  }
}

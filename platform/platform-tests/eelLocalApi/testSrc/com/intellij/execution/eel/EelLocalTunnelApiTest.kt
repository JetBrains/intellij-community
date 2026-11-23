// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.eel

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.platform.eel.ConfigurableClientSocket
import com.intellij.platform.eel.EelConnectionError
import com.intellij.platform.eel.EelIpPreference
import com.intellij.platform.eel.EelProcess
import com.intellij.platform.eel.EelTunnelsApi
import com.intellij.platform.eel.ReadResult
import com.intellij.platform.eel.channels.sendWholeBuffer
import com.intellij.platform.eel.getAcceptorForRemotePort
import com.intellij.platform.eel.getConnectionToRemotePort
import com.intellij.platform.eel.impl.local.tunnels.ConnectionAcceptorImpl
import com.intellij.platform.eel.listenOnUnixSocket
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.localEel
import com.intellij.platform.eel.provider.utils.consumeAsInputStream
import com.intellij.platform.eel.provider.utils.sendWholeText
import com.intellij.platform.tests.eelHelpers.EelHelper
import com.intellij.platform.tests.eelHelpers.network.NetworkConstants
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.sun.management.UnixOperatingSystemMXBean
import io.ktor.util.decodeString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.AnnotatedElementContext
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.io.TempDirFactory
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.lang.management.ManagementFactory
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.ClosedChannelException
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executor
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Creates a temp directory with a short path for Unix socket tests.
 *
 * Bazel sets java.io.tmpdir under the test tree. That path can be too long for Unix socket files.
 * Use /tmp on Unix to keep the socket path below the platform limit.
 */
private class ShortUnixSocketTempDirFactory : TempDirFactory {
  override fun createTempDirectory(elementContext: AnnotatedElementContext, extensionContext: ExtensionContext): Path {
    return if (SystemInfoRt.isWindows) {
      Files.createTempDirectory("eel-test-sockets-")
    }
    else {
      Files.createTempDirectory(Path.of("/tmp"), "eel-test-sockets-")
    }
  }
}

@TestApplication
class EelLocalTunnelApiTest {
  companion object {
    // lazy: only the tests that run the helper need its class path.
    // helper is SERVER (we connect)
    private val serverExecutor by lazy { JavaMainClassExecutor(EelHelper::class.java, EelHelper.HelperMode.NETWORK_SERVER.name) }
    // helper is CLIENT (we are the server)
    private val clientExecutor by lazy { JavaMainClassExecutor(EelHelper::class.java, EelHelper.HelperMode.NETWORK_CLIENT.name) }
  }

  @Test
  fun testDescriptor() {
    Assertions.assertEquals(LocalEelDescriptor, localEel.tunnels.descriptor)
  }

  @Test
  fun testCheckFailureConnection(): Unit = timeoutRunBlocking(5.minutes) {
    try {
      localEel.tunnels.getConnectionToRemotePort().port(22U).hostname("google.com").timeout(5.seconds).eelIt()
      Assertions.fail("Connection should fail")
    } catch (_: EelConnectionError) {
      // ok
    }
  }

  @Test
  fun testClientSuccessConnection(): Unit = timeoutRunBlocking(1.minutes) {
    withServer { connection, _ ->
      val buffer = ByteBuffer.allocate(4096)
      connection.receiveChannel.receive(buffer)
      Assertions.assertEquals(NetworkConstants.HELLO_FROM_SERVER, NetworkConstants.fromByteBuffer(buffer.flip()))
      connection.sendChannel.sendWholeBuffer(NetworkConstants.HELLO_FROM_CLIENT.toBuffer()) //      Helper closes the stream, so does the channel
      Assertions.assertEquals(ReadResult.EOF, connection.receiveChannel.receive(ByteBuffer.allocate(1)))
    }
  }

  @Test
  fun testSocketClosedBeforeAcceptorStarts(): Unit = timeoutRunBlocking {
    val tasks = ArrayDeque<Runnable>()
    val dispatcher = Executor { tasks.addLast(it) }.asCoroutineDispatcher()
    ServerSocketChannel.open().use { socket ->
      socket.bind(InetSocketAddress("127.0.0.1", 0))
      val acceptor = ConnectionAcceptorImpl(socket, this + dispatcher)
      try {
        socket.close()
        tasks.removeFirst().run()
        val result = acceptor.incomingConnections.tryReceive()
        Assertions.assertTrue(result.isClosed)
        Assertions.assertInstanceOf(ClosedChannelException::class.java, result.exceptionOrNull())
      }
      finally {
        acceptor.close()
      }
    }
  }

  @Test
  fun testServerListensForConnection(): Unit = timeoutRunBlocking(1.minutes) {
    val helper = clientExecutor.createBuilderToExecuteMain(localEel.exec).eelIt()
    val acceptor = localEel.tunnels.getAcceptorForRemotePort().eelIt()
    try {
      helper.stdin.sendWholeText(acceptor.boundAddress.port.toString() + "\n")
      val conn = acceptor.incomingConnections.receive()
      try {
        val buff = ByteBuffer.allocate(1024)
        conn.receiveChannel.receive(buff)
        val fromServer = NetworkConstants.fromByteBuffer(buff.flip())
        assertEquals(NetworkConstants.HELLO_FROM_CLIENT, fromServer)
      }
      finally {
        conn.close()
      }
    }
    finally {
        acceptor.close()
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = [true, false])
  fun testUnixSocket(explicitSocket: Boolean, @TempDir(factory = ShortUnixSocketTempDirFactory::class) tempDir: Path): Unit = timeoutRunBlocking {
    repeat(5) {
      val unixSocketResult =
        if (explicitSocket)
          localEel.tunnels.listenOnUnixSocket(tempDir.resolve("file.sock").asEelPath())
        else
          localEel.tunnels.listenOnUnixSocket().parentDirectory(tempDir.asEelPath()).eelIt()
      val socketPathStr = unixSocketResult.unixSocketPath.toString()
      val tx = unixSocketResult.tx
      val rx = unixSocketResult.rx
      val socketPath = Path.of(socketPathStr)
      val helloFromClient = "fromClient".encodeToByteArray()
      val helloFromServer = "fromServer".encodeToByteArray()
      val client = async(Dispatchers.IO) {
        SocketChannel.open(UnixDomainSocketAddress.of(socketPath)).use {
          val bufferRecv = ByteBuffer.allocate(helloFromServer.size)
          while (bufferRecv.hasRemaining()) {
            it.read(bufferRecv)
          }
          Assertions.assertEquals(helloFromServer.decodeToString(), bufferRecv.flip().decodeString())
          val bufferSnd = ByteBuffer.wrap(helloFromClient)
          while (bufferSnd.hasRemaining()) {
            it.write(bufferSnd)
          }
        }
      }
      tx.sendWholeBuffer(ByteBuffer.wrap(helloFromServer))
      val bufferRecv = ByteBuffer.allocate(helloFromClient.size)
      while (bufferRecv.hasRemaining()) {
        rx.receive(bufferRecv)
      }
      Assertions.assertEquals(helloFromClient.decodeToString(), bufferRecv.flip().decodeString())
      client.join()
      rx.closeForReceive()
      tx.close(null)
    }
  }

  @Test
  fun testFailedConnectionsLeaveNoOpenFiles(): Unit = timeoutRunBlocking(1.minutes) {
    assumeTrue(!SystemInfoRt.isWindows, "counts open file descriptors, Unix only")
    // a refusal does not fit here: the JDK closes the channel itself when connect fails.
    assertFewOpenFilesLeft {
      try {
        localEel.tunnels.getConnectionToRemotePort(connectArgs(1) { throw IllegalStateException("configure failed") }).close()
        Assertions.fail("connection should fail")
      }
      catch (_: IllegalStateException) {
        // ok
      }
    }
  }

  @Test
  fun testCancelledConnectionsLeaveNoOpenFiles(): Unit = timeoutRunBlocking(1.minutes) {
    assumeTrue(!SystemInfoRt.isWindows, "counts open file descriptors, Unix only")
    withSilentServer { port ->
      assertFewOpenFilesLeft {
        coroutineScope {
          // the caller is cancelled while the channel is open: the call fails in the connect loop,
          // or `withContext` drops a ready connection.
          lateinit var caller: Job
          caller = launch(start = CoroutineStart.LAZY) {
            localEel.tunnels.getConnectionToRemotePort(connectArgs(port) { caller.cancel() }).close()
          }
          caller.join()
          Assertions.assertTrue(caller.isCancelled)
        }
      }
    }
  }

  private fun connectArgs(port: Int, configure: ConfigurableClientSocket.() -> Unit): EelTunnelsApi.GetConnectionToRemotePortArgs {
    return object : EelTunnelsApi.GetConnectionToRemotePortArgs {
      override val port: UShort = port.toUShort()
      override val hostname: String = "127.0.0.1"
      override val protocolPreference: EelIpPreference = EelIpPreference.PREFER_V4
      override val configureSocketBeforeConnection: ConfigurableClientSocket.() -> Unit = configure
    }
  }

  /** a server that never accepts: connects queue in its backlog and open no files in this process. */
  private suspend fun withSilentServer(block: suspend (port: Int) -> Unit) {
    ServerSocket(0, 200, InetAddress.getLoopbackAddress()).use { server ->
      block(server.localPort)
    }
  }

  private suspend fun assertFewOpenFilesLeft(connect: suspend () -> Unit) {
    val os = ManagementFactory.getOperatingSystemMXBean() as UnixOperatingSystemMXBean
    connect() // warm up class loading and dispatcher threads.
    val before = os.openFileDescriptorCount
    val attempts = 50
    repeat(attempts) { connect() }
    val growth = os.openFileDescriptorCount - before
    Assertions.assertTrue(growth < attempts / 2, "open file descriptors grew by $growth after $attempts failed connects")
  }

  private suspend fun withServer(block: suspend CoroutineScope.(EelTunnelsApi.Connection, EelProcess) -> Unit) {

    val helper = serverExecutor.createBuilderToExecuteMain(localEel.exec).eelIt()
    try {
      val port = helper.stdout.consumeAsInputStream().bufferedReader().readLine().trim().toInt()
      val connection = localEel.tunnels.getConnectionToRemotePort().port(port.toUShort()).preferV4().eelIt()
      coroutineScope {
        block(connection, helper)
      }
    }
    finally {
      helper.kill()
    }
  }
}

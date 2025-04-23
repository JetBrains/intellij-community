// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.eel

import com.intellij.platform.eel.*
import com.intellij.platform.eel.EelTunnelsApi.CreateFilePath
import com.intellij.platform.eel.channels.sendWholeBuffer
import com.intellij.platform.eel.provider.localEel
import com.intellij.platform.eel.provider.utils.consumeAsInputStream
import com.intellij.platform.eel.provider.utils.sendWholeText
import com.intellij.platform.tests.eelHelpers.EelHelper
import com.intellij.platform.tests.eelHelpers.network.NetworkConstants
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import io.ktor.util.decodeString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import java.nio.file.Path
import kotlin.io.path.pathString
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@TestApplication
class EelLocalTunnelApiTest {
  companion object {

    private lateinit var serverExecutor: JavaMainClassExecutor
    private lateinit var clientExecutor: JavaMainClassExecutor

    @BeforeAll
    @JvmStatic
    fun createExecutor() { // helper is SERVER (we connect)
      serverExecutor = JavaMainClassExecutor(EelHelper::class.java, EelHelper.HelperMode.NETWORK_SERVER.name) // helper is CLIENT (we are the server)
      clientExecutor = JavaMainClassExecutor(EelHelper::class.java, EelHelper.HelperMode.NETWORK_CLIENT.name)
    }
  }

  @Test
  fun testCheckFailureConnection(): Unit = timeoutRunBlocking(5.minutes) {
    when (localEel.tunnels.getConnectionToRemotePort().port(22U).hostname("google.com").timeout(5.seconds).eelIt()) {
      is EelResult.Error -> Unit
      is EelResult.Ok -> Assertions.fail("Connection should fail")
    }
  }

  @Test
  fun testClientSuccessConnection(): Unit = timeoutRunBlocking(1.minutes) {
    withServer { connection, _ ->
      val buffer = ByteBuffer.allocate(4096)
      connection.receiveChannel.receive(buffer).getOrThrow()
      Assertions.assertEquals(NetworkConstants.HELLO_FROM_SERVER, NetworkConstants.fromByteBuffer(buffer.flip()))
      connection.sendChannel.sendWholeBuffer(NetworkConstants.HELLO_FROM_CLIENT.toBuffer()).getOrThrow() //      Helper closes the stream, so does the channel
      Assertions.assertEquals(ReadResult.EOF, connection.receiveChannel.receive(ByteBuffer.allocate(1)).getOrThrow())
    }
  }

  @Test
  fun testServerListensForConnection(): Unit = timeoutRunBlocking(1.minutes) {
    val helper = clientExecutor.createBuilderToExecuteMain(localEel.exec).getOrThrow()
    val acceptor = localEel.tunnels.getAcceptorForRemotePort().getOrThrow()
    helper.stdin.sendWholeText(acceptor.boundAddress.port.toString() + "\n").getOrThrow()
    val conn = acceptor.incomingConnections.receive()
    try {
      val buff = ByteBuffer.allocate(1024)
      conn.receiveChannel.receive(buff).getOrThrow()
      val fromServer = NetworkConstants.fromByteBuffer(buff.flip())
      assertEquals(NetworkConstants.HELLO_FROM_CLIENT, fromServer)
    }
    finally {
      conn.close()
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = [true, false])
  fun testUnixSocket(explicitSocket: Boolean, @TempDir tempDir: Path): Unit = timeoutRunBlocking {
    val path = if (explicitSocket) {
      CreateFilePath.Fixed(tempDir.resolve("file.sock").pathString)
    }
    else {
      CreateFilePath.MkTemp()
    }
    repeat(5) {
      val (socketPathStr, tx, rx) = localEel.tunnels.listenOnUnixSocket(path)
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
      tx.sendWholeBuffer(ByteBuffer.wrap(helloFromServer)).getOrThrow()
      val bufferRecv = ByteBuffer.allocate(helloFromClient.size)
      while (bufferRecv.hasRemaining()) {
        rx.receive(bufferRecv)
      }
      Assertions.assertEquals(helloFromClient.decodeToString(), bufferRecv.flip().decodeString())
      client.join()
      rx.close()
      tx.close()
    }
  }

  private suspend fun withServer(block: suspend CoroutineScope.(EelTunnelsApi.Connection, EelProcess) -> Unit) {

    val helper = serverExecutor.createBuilderToExecuteMain(localEel.exec).getOrThrow()
    try {
      val port = helper.stdout.consumeAsInputStream().bufferedReader().readLine().trim().toInt()
      val connection = localEel.tunnels.getConnectionToRemotePort().port(port.toUShort()).preferV4().getOrThrow()
      coroutineScope {
        block(connection, helper)
      }
    }
    finally {
      helper.kill()
    }
  }
}
// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.registry.Registry
import io.ktor.network.selector.ActorSelectorManager
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext

/**
 * The problem is covered here: PY-50689.
 *
 * Intellij debuggers opens port and waits debugee to connect to it.
 * But Windows firewall blocks connections from WSL, so we launch special proxy process that accepts connections from Windows
 * and debugee and connects them.
 * See `wslproxy.c` readme and there is also svg file there which covers this process.
 *
 * If you listen [applicationPort] on Windows, create object of this class to open port on WSL [wslIngressPort].
 * One instance -- one ingress port
 * Any WSL process can connect to [wslIngressPort] and data will be forwarded to your [applicationPort].
 * [dispose] this object to stop forwarding.
 *
 */
@ApiStatus.Internal
class WslProxy(distro: AbstractWslDistribution, private val applicationAddress: InetSocketAddress) : Disposable {
  @Deprecated("Use the construction with the application address." +
              " This constructor can lead to sporadic 'connection refused' errors in case of IPv4/IPv6 confusion.")
  constructor(distro: AbstractWslDistribution, applicationPort: Int) : this(distro, InetSocketAddress("127.0.0.1", applicationPort))

  private companion object {
    private val LOG = logger<WslProxy>()

    /**
     * Server might not be opened yet. Since non-blocking Ktor API doesn't wait for it but throws exception instead, we retry
     */
    private tailrec suspend fun TcpSocketBuilder.tryConnect(host: String, port: Int, attemptRemains: Int = 10): Socket {
      try {
        return connect(host, port)
      }
      catch (e: IOException) {
        if (attemptRemains <= 0) throw e
        LOG.warn("Can't connect to $host $port , will retry", e)
        delay(100)
      }
      return tryConnect(host, port, attemptRemains - 1)
    }


    suspend fun connectChannels(source: ByteReadChannel, dest: ByteWriteChannel) {
      val buffer = ByteArray(64800)
      while (coroutineContext.isActive) {
        val bytesRead = source.readAvailable(buffer)
        if (bytesRead < 1) {
          dest.close()
          return
        }
        try {
          dest.writeFully(buffer, 0, bytesRead)
        }
        catch (e: IOException) {
          dest.close()
          return
        }
      }
    }

    private fun Process.destroyWslProxy() {
      try {
        outputStream.close() // Closing stream should stop process
      }
      catch (e: Exception) {
        Logger.getInstance(WslProxy::class.java).warn(e)
      }
      GlobalScope.launch(Dispatchers.IO) {
        // Wait for process to die. If not -- kill it
        delay(1000)
        if (isAlive) {
          Logger.getInstance(WslProxy::class.java).warn("Process still alive, destroying")
          destroy()
        }
        val exitCode = exitValue()
        if (exitCode != 0) {
          Logger.getInstance(WslProxy::class.java).warn("Exit code was $exitCode")
        }
      }
    }
  }

  val wslIngressPort: Int
  private var wslLinuxIp: String
  private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

  private suspend fun readToBuffer(channel: ByteReadChannel, bufferSize: Int): ByteBuffer {
    val buffer = ByteBuffer.allocate(bufferSize).order(ByteOrder.LITTLE_ENDIAN)
    try {
      channel.readFully(buffer)
    }
    catch (e: ClosedReceiveChannelException) {
      throw Exception("wslproxy closed stream unexpectedly. See idea.log for errors", e)
    }
    buffer.rewind()
    return buffer
  }

  private suspend fun readPortFromChannel(channel: ByteReadChannel): Int = readToBuffer(channel, 2).short.toUShort().toInt()

  init {
    val args = if (Registry.`is`("wsl.proxy.connect.localhost")) arrayOf("--loopback") else emptyArray()
    val wslCommandLine = distro.getTool("wslproxy", *args)
    val process =
      if (WslIjentAvailabilityService.getInstance().runWslCommandsViaIjent())
        wslCommandLine.createProcess()
      else
        Runtime.getRuntime().exec(wslCommandLine.commandLineString)
    val log = Logger.getInstance(WslProxy::class.java)

    scope.launch {
      process.errorStream.toByteReadChannel().readUTF8Line()?.let {
        // Collect stderr
        log.error(it)
      }
    }
    try {
      val stdoutChannel = process.inputStream.toByteReadChannel()
      wslLinuxIp = runBlocking {
        readToBuffer(stdoutChannel, 4)
      }.let { InetAddress.getByAddress(it.array()).hostAddress }

      wslIngressPort = runBlocking {
        readPortFromChannel(stdoutChannel)
      }

      // wslproxy.c reports egress port, connect to it
      scope.launch {
        while (isActive) {
          val linuxEgressPort = readPortFromChannel(stdoutChannel)
          clientConnected(linuxEgressPort)
        }
      }.invokeOnCompletion {
        process.destroyWslProxy()
      }
    }
    catch (e: Exception) {
      process.destroyWslProxy()
      scope.cancel()
      throw e
    }
  }

  private suspend fun clientConnected(linuxEgressPort: Int) {
    val selector = ActorSelectorManager(scope.coroutineContext)
    val winToLin = scope.async {
      LOG.info("Connecting to WSL: $wslLinuxIp:$linuxEgressPort")
      val socket = aSocket(selector).tcp().tryConnect(wslLinuxIp, linuxEgressPort)
      LOG.info("Connected to WSL")
      socket
    }
    val winToWin = scope.async {
      LOG.info("Connecting to app: $applicationAddress")
      val socket = aSocket(ActorSelectorManager(scope.coroutineContext)).tcp()
        .tryConnect(applicationAddress.hostString, applicationAddress.port)
      LOG.info("Connected to app")
      socket
    }
    scope.launch {
      val winToLinSocket = winToLin.await()
      val winToWinSocket = winToWin.await()
      val closeSockets = {
        winToWinSocket.close()
        winToLinSocket.close()
      }
      launch(CoroutineName("WinWin->WinLin $linuxEgressPort")) {
        connectChannels(winToWinSocket.openReadChannel(), winToLinSocket.openWriteChannel(true))
      }.invokeOnCompletion { closeSockets() }
      launch(CoroutineName("WinLin->WinWin $applicationAddress")) {
        connectChannels(winToLinSocket.openReadChannel(), winToWinSocket.openWriteChannel(true))
      }.invokeOnCompletion { closeSockets() }
    }
  }


  override fun dispose() {
    scope.cancel()
  }
}

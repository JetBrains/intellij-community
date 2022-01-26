// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import io.ktor.network.selector.ActorSelectorManager
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.close
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.*
import java.io.IOException
import java.net.InetAddress
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
class WslProxy(distro: AbstractWslDistribution, private val applicationPort: Int) : Disposable {
  private companion object {
    suspend fun connectChannels(source: ByteReadChannel, dest: ByteWriteChannel) {
      val buffer = ByteBuffer.allocate(4096)
      while (coroutineContext.isActive) {
        buffer.rewind()
        val bytesRead = source.readAvailable(buffer)
        if (bytesRead < 1) {
          dest.close()
          return
        }
        buffer.rewind()
        try {
          dest.writeFully(buffer.array(), 0, bytesRead)
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
      CoroutineScope(Dispatchers.IO).launch {
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
  private val scope = CoroutineScope(Dispatchers.IO)

  private suspend fun readToBuffer(channel: ByteReadChannel, bufferSize: Int): ByteBuffer {
    val buffer = ByteBuffer.allocate(bufferSize).order(ByteOrder.LITTLE_ENDIAN)
    channel.readFully(buffer)
    buffer.rewind()
    return buffer
  }

  private suspend fun readPortFromChannel(channel: ByteReadChannel): Int = readToBuffer(channel, 2).short.toUShort().toInt()

  init {
    val file = PathManager.findBinFileWithException("wslproxy").toString()
    val wspPath = distro.getWslPath(file) ?: throw AssertionError("Can't access $file from Linux")
    val wslCommandLine = distro.createWslCommandLine(wspPath)
    val process = Runtime.getRuntime().exec(wslCommandLine.commandLineString)
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
    val winToLin = scope.async {
      aSocket(ActorSelectorManager(scope.coroutineContext)).tcp().connect(wslLinuxIp, linuxEgressPort)
    }
    val winToWin = scope.async {
      aSocket(ActorSelectorManager(scope.coroutineContext)).tcp().connect("127.0.0.1", applicationPort)
    }
    scope.launch {
      val winToLinSocket = winToLin.await()
      val winToWinSocket = winToWin.await()
      launch(CoroutineName("WinWin->WinLin $linuxEgressPort")) {
        connectChannels(winToWinSocket.openReadChannel(), winToLinSocket.openWriteChannel(true))
      }
      launch(CoroutineName("WinLin->WinWin $applicationPort")) {
        connectChannels(winToLinSocket.openReadChannel(), winToWinSocket.openWriteChannel(true))
      }
    }
  }


  override fun dispose() {
    scope.cancel()
  }
}

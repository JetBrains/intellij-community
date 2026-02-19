// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.tests.eelHelpers.network

import org.jetbrains.annotations.TestOnly
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel

/**
 * Prints port, accepts connections, sends hello, receives the answer
 */
@TestOnly
fun startNetworkServer() {
  val serverSocketChannel = ServerSocketChannel.open().also { channel ->
    channel.bind(InetSocketAddress(0))
  }
  println(serverSocketChannel.socket().localPort)
  System.out.flush()
  val clientChannel = serverSocketChannel.accept()
  clientChannel.write(NetworkConstants.HELLO_FROM_SERVER.toBuffer())
  val readBuffer = ByteBuffer.allocate(4096)
  try {
    clientChannel.read(readBuffer)
  }
  catch (e: IOException) {
    e.printStackTrace()
    return
  }
  readBuffer.flip()

  if (NetworkConstants.fromByteBuffer(readBuffer) == NetworkConstants.HELLO_FROM_CLIENT) {
    clientChannel.shutdownOutput()
    clientChannel.shutdownInput()
    clientChannel.close()
  }
  else {
    error("Wrong data from client")
  }
}

/**
 * Read port number from stdin, connect and write hello there
 */
@TestOnly
fun startNetworkClient() {
  val port = readLine()!!.trim().toInt()
  SocketChannel.open().use {
    it.connect(InetSocketAddress(port))
    it.write(NetworkConstants.HELLO_FROM_CLIENT.toBuffer())
  }
}
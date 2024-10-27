// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.tests.eelHelpers.network

import org.jetbrains.annotations.TestOnly
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.ServerSocketChannel

/**
 * Prints port, accepts connections, sends hello, receives the answer
 */
@TestOnly
fun startNetworkHelper() {
  val serverSocketChannel = ServerSocketChannel.open().also { channel ->
    channel.bind(InetSocketAddress(0))
  }
  println(serverSocketChannel.socket().localPort)
  System.out.flush()
  val clientChannel = serverSocketChannel.accept()
  clientChannel.write(NetworkConstants.HELLO_FROM_SERVER.toBuffer())
  val readBuffer = ByteBuffer.allocate(4096)
  clientChannel.read(readBuffer)
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
// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import java.net.InetAddress
import java.net.ServerSocket

object NetworkUtils {

  private const val FIND_PORT_ATTEMPTS_COUNT = 5

  private fun isPortFree(port: Int): Boolean {
    var socket: ServerSocket? = null
    try {
      socket = ServerSocket(port, 0, InetAddress.getByName("127.0.0.1"))
      socket.reuseAddress = true
      return true
    }
    catch (e: Exception) {
      return false
    }
    finally {
      socket?.close()
    }
  }

  fun findFreePort(): Int {
    // 0 means any free port; OS chooses it automatically
    return findFreePort(0)
  }

  fun findFreePort(port: Int): Int {
    if (port > 0 && isPortFree(port))
      return port
    val socket1 = ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))
    val result = socket1.localPort
    socket1.reuseAddress = true
    socket1.close()
    return result
  }

  fun findFreePort(port: Int, bannedPorts: Set<Int>): Int {
    if (port > 0 && isPortFree(port) && !bannedPorts.contains(port))
      return port

    for (attempt in 0 until FIND_PORT_ATTEMPTS_COUNT) {
      val socket1 = ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))
      val result = socket1.localPort
      socket1.reuseAddress = true
      socket1.close()

      if (!bannedPorts.contains(result)) {
        return result
      }
    }

    throw IllegalStateException("Failed to find free ports (base port $port) using $FIND_PORT_ATTEMPTS_COUNT attempts")
  }
}
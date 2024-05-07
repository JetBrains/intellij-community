// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.util

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.SystemInfo
import kotlinx.coroutines.delay
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.Socket
import java.net.URL
import kotlin.time.Duration.Companion.seconds

object DebuggerUtil {
  private val LOG = logger<DebuggerUtil>()

  private const val DEFAULT_IDEA_PORT = 63342
  private const val INSTANCES_TO_ASK_FOR_DEBUG = 6

  private const val TIMEOUT_FOR_OPENING_DEBUGGER_PORT_MILLIS = 2_000L

  /**
   * We are going to send http request to the IDE that has started the test
   * to attach a new debug session with debug name debugSessionName and port debuggerPort
   */
  suspend fun attachDebuggerToProcess(debuggerPort: Int, debugSessionName: String): Boolean {
    if (!waitABitForPortOpening(debuggerPort)) return false
    if (SystemInfo.isWindows) {
      // apply additional delay, otherwise the debugger fails to connect with "handshake failed - connection prematurally closed" message
      delay(10.seconds)
    }
    return tryAttachDebuggerToProcess(debuggerPort, debugSessionName)
  }

  /**
   * Requests IDE to attach debugger to JAVA process which agent listens on specified port
   */
  fun tryAttachDebuggerToProcess(debuggerPort: Int, debugSessionName: String): Boolean {
    val firstPort = DEFAULT_IDEA_PORT
    val lastPort = DEFAULT_IDEA_PORT + INSTANCES_TO_ASK_FOR_DEBUG
    for (possibleWebServerPortOfIDEStartedTheTest in firstPort..lastPort) {
      if (attachIDEToProcess(debuggerPort, debugSessionName, possibleWebServerPortOfIDEStartedTheTest))
        return true
    }

    LOG.error("Can not find IDE to attach from")
    return false
  }

  private fun attachIDEToProcess(debuggerPort: Int, debuggerSessionName: String, idePort: Int): Boolean {
    try {
      LOG.info("Trying to ask our running ide with possible web server port $idePort " +
               "to create new debug session to the process $debuggerSessionName with debugger port $debuggerPort")
      // Create connection
      val url = URL("http://localhost:$idePort/debug/attachToTestProcess")
      val connection = url.openConnection() as HttpURLConnection
      connection.requestMethod = "POST"
      connection.useCaches = false
      connection.doOutput = true
      connection.connectTimeout = 3_000
      connection.readTimeout = 3_000

      // Send request to connect debugger to 'port'
      DataOutputStream(connection.outputStream).use {
        it.writeBytes(debuggerPort.toString())
        it.writeBytes("\n")
        it.writeBytes(debuggerSessionName)
      }

      // Wait for response
      return connection.responseCode == 200
    }
    catch (_: Throwable) {
      return false
    }
  }

  private fun waitABitForPortOpening(debuggerPort: Int): Boolean {
    val startTimeMillis = System.currentTimeMillis()
    var isOpened = false
    while (!isOpened && System.currentTimeMillis() - startTimeMillis < TIMEOUT_FOR_OPENING_DEBUGGER_PORT_MILLIS) {
      try {
        Socket(InetAddress.getByName("127.0.0.1"), debuggerPort).use {
          isOpened = true
        }
      }
      catch (_: Exception) {
        // ignore
      }
    }

    return isOpened.also {
      if (it) {
        LOG.info("Debugger port $debuggerPort is opened")
      }
      else {
        LOG.warn("Couldn't wait for debugger port=$debuggerPort opening by other process.")
      }
    }
  }
}
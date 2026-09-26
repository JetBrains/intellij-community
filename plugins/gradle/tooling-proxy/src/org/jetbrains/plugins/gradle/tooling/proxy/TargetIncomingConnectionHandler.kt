// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling.proxy

import org.gradle.launcher.daemon.protocol.BuildEvent
import org.gradle.launcher.daemon.protocol.Message
import org.gradle.launcher.daemon.server.IncomingConnectionHandler
import org.gradle.launcher.daemon.server.SynchronizedDispatchConnection
import org.slf4j.LoggerFactory

class TargetIncomingConnectionHandler : IncomingConnectionHandler {
  private lateinit var connection: SynchronizedDispatchConnection<Message>
  private lateinit var buildParameters: TargetBuildParameters

  fun isConnected() = ::connection.isInitialized
  fun isBuildParametersReceived() = ::buildParameters.isInitialized

  fun targetBuildParameters() = buildParameters

  override fun handle(connection: SynchronizedDispatchConnection<Message>) {
    LOG.debug("connection got $connection")
    this.connection = connection
    val message = connection.receive() as BuildEvent
    this.buildParameters = message.payload as TargetBuildParameters
  }

  fun dispatch(message: Message) = connection.dispatchAndFlush(message)
  fun receiveResultAck() {
    val buildEvent = connection.receive() as BuildEvent
    LOG.debug("Result ack received: ${buildEvent.payload}")
  }

  companion object {
    private val LOG = LoggerFactory.getLogger(TargetIncomingConnectionHandler::class.java)
  }
}
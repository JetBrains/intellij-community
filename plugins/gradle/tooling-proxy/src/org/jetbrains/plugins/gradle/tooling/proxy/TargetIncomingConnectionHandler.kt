// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling.proxy

import org.gradle.internal.remote.internal.RemoteConnection
import org.gradle.launcher.daemon.protocol.BuildEvent
import org.gradle.launcher.daemon.protocol.Message
import org.gradle.launcher.daemon.server.IncomingConnectionHandler
import org.slf4j.LoggerFactory

class TargetIncomingConnectionHandler : IncomingConnectionHandler {
  private lateinit var connection: RemoteConnection<Message>
  private lateinit var myTargetBuildParameters: TargetBuildParameters

  fun isConnected() : Boolean = ::connection.isInitialized

  fun targetBuildParameters() = myTargetBuildParameters

  override fun handle(connection: RemoteConnection<Message>) {
    LOG.debug("connection got $connection")
    this.connection = connection
    val message = connection.receive() as BuildEvent
    this.myTargetBuildParameters = message.payload as TargetBuildParameters
  }

  fun dispatch(message: Message) = connection.run {
    dispatch(message)
    flush()
  }

  companion object {
    private val LOG = LoggerFactory.getLogger(TargetIncomingConnectionHandler::class.java)
  }
}
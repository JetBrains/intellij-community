// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling.proxy

import org.gradle.api.Action
import org.gradle.api.UncheckedIOException
import org.gradle.internal.concurrent.CompositeStoppable
import org.gradle.internal.remote.Address
import org.gradle.internal.remote.ConnectionAcceptor
import org.gradle.internal.remote.internal.ConnectCompletion
import org.gradle.internal.remote.internal.IncomingConnector
import org.gradle.internal.remote.internal.RemoteConnection
import org.gradle.internal.serialize.Serializer
import org.gradle.internal.serialize.Serializers
import org.gradle.launcher.daemon.protocol.Message
import org.gradle.launcher.daemon.server.DaemonServerConnector
import org.gradle.launcher.daemon.server.IncomingConnectionHandler
import org.gradle.launcher.daemon.server.SynchronizedDispatchConnection
import org.jetbrains.plugins.gradle.tooling.proxy.Main.LOCAL_BUILD_PROPERTY
import org.slf4j.LoggerFactory
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

class TargetTcpServerConnector(private val serializer: Serializer<Message>) : DaemonServerConnector {
  private val incomingConnector: IncomingConnector = TargetIncomingConnector()
  private var started = false
  private var stopped = false
  private val lifecycleLock: Lock = ReentrantLock()
  private var acceptor: ConnectionAcceptor? = null

  override fun start(handler: IncomingConnectionHandler, connectionErrorHandler: Runnable): Address {
    lifecycleLock.lock()
    try {
      check(!stopped) { "server connector cannot be started as it is either stopping or has been stopped" }
      check(!started) { "server connector cannot be started as it has already been started" }
      val connectEvent = Action<ConnectCompletion> { completion ->
        LOG.debug("ConnectCompletion $completion")
        val remoteConnection: RemoteConnection<Message> = try {
          completion.create(Serializers.stateful(serializer))
        }
        catch (e: UncheckedIOException) {
          connectionErrorHandler.run()
          throw e
        }
        handler.handle(SynchronizedDispatchConnection(remoteConnection))
      }

      val allowRemote = !System.getProperty(LOCAL_BUILD_PROPERTY, "false").toBoolean()
      LOG.debug("Allow remote $allowRemote")
      val connectionAcceptor = incomingConnector.accept(connectEvent, allowRemote)
      acceptor = connectionAcceptor
      started = true
      return connectionAcceptor.address
    }
    finally {
      lifecycleLock.unlock()
    }
  }

  override fun stop() {
    lifecycleLock.lock()
    try {
      stopped = true
    }
    finally {
      lifecycleLock.unlock()
    }
    CompositeStoppable.stoppable(acceptor!!, incomingConnector).stop()
  }

  companion object {
    private val LOG = LoggerFactory.getLogger(TargetTcpServerConnector::class.java)
  }
}

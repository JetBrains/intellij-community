// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.target

import com.intellij.execution.target.HostPort
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.service.remote.MultiLoaderObjectInputStream
import com.intellij.openapi.externalSystem.util.wsl.connectRetrying
import org.gradle.initialization.BuildEventConsumer
import org.gradle.internal.remote.internal.RemoteConnection
import org.gradle.internal.remote.internal.inet.SocketInetAddress
import org.gradle.internal.remote.internal.inet.TcpOutgoingConnector
import org.gradle.internal.serialize.Serializers
import org.gradle.launcher.daemon.protocol.BuildEvent
import org.gradle.launcher.daemon.protocol.DaemonMessageSerializer
import org.gradle.launcher.daemon.protocol.Failure
import org.gradle.launcher.daemon.protocol.Message
import org.gradle.launcher.daemon.protocol.Success
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.ResultHandler
import org.gradle.tooling.internal.provider.action.BuildActionSerializer
import java.io.ByteArrayInputStream
import java.net.InetAddress
import java.util.concurrent.Future

internal class GradleServerEventsListener(
  private val serverEnvironmentSetup: GradleServerEnvironmentSetup,
  private val connectionAddressResolver: (HostPort) -> HostPort,
  private val classloaderHolder: GradleToolingProxyClassloaderHolder,
  private val buildEventConsumer: BuildEventConsumer,
) {

  private lateinit var listenerTask: Future<*>

  fun start(hostName: String, port: Int, resultHandler: ResultHandler<Any?>) {
    check(!::listenerTask.isInitialized) { "Gradle server events listener has already been started" }
    listenerTask = ApplicationManager.getApplication().executeOnPooledThread {
      try {
        doRun(hostName, port, resultHandler)
      }
      catch (t: Throwable) {
        resultHandler.onFailure(GradleConnectionException(t.message, t))
      }
    }
  }

  private fun createConnection(hostName: String, port: Int): RemoteConnection<Message> {
    val hostPort = connectionAddressResolver.invoke(HostPort(hostName, port))
    val inetAddress = InetAddress.getByName(hostPort.host)
    val connectCompletion = connectRetrying(5000) { TcpOutgoingConnector().connect(SocketInetAddress(inetAddress, hostPort.port)) }
    val serializer = DaemonMessageSerializer.create(BuildActionSerializer.create())
    return connectCompletion.create(Serializers.stateful(serializer))
  }

  private fun doRun(hostName: String, port: Int, resultHandler: ResultHandler<Any?>) {

    val connection = createConnection(hostName, port)

    connection.dispatch(BuildEvent(serverEnvironmentSetup.getTargetBuildParameters()))
    connection.flush()

    try {
      loop@ while (true) {
        val message = connection.receive() ?: break
        when (message) {
          is Success -> {
            val value = deserializeIfNeeded(message.value)
            resultHandler.onComplete(value)
            break@loop
          }
          is Failure -> {
            resultHandler.onFailure(message.value as? GradleConnectionException ?: GradleConnectionException(message.value.message))
            break@loop
          }
          is BuildEvent -> {
            buildEventConsumer.dispatch(message.payload)
          }
          is org.jetbrains.plugins.gradle.tooling.proxy.Output -> {
            buildEventConsumer.dispatch(message)
          }
          is org.jetbrains.plugins.gradle.tooling.proxy.IntermediateResult -> {
            val value = deserializeIfNeeded(message.value)
            serverEnvironmentSetup.getTargetIntermediateResultHandler().onResult(message.type, value)
          }
          else -> {
            break@loop
          }
        }
      }
    }
    finally {
      connection.sendResultAck()
    }
  }

  private fun deserializeIfNeeded(value: Any?): Any? {
    val bytes = value as? ByteArray ?: return value
    val deserialized = MultiLoaderObjectInputStream(ByteArrayInputStream(bytes), classloaderHolder.getClassloaders()).use {
      it.readObject()
    }
    return deserialized
  }

  private fun RemoteConnection<Message>.sendResultAck() {
    dispatch(BuildEvent("ack"))
    flush()
    stop()
  }

  fun stop() {
    if (::listenerTask.isInitialized && !listenerTask.isDone) {
      listenerTask.cancel(true)
    }
  }

  fun waitForResult(handler: () -> Boolean) {
    val startTime = System.currentTimeMillis()
    while (!handler.invoke() &&
           (::listenerTask.isInitialized.not() || !listenerTask.isDone) &&
           System.currentTimeMillis() - startTime < 10000) {
      val lock = Object()
      synchronized(lock) {
        try {
          lock.wait(100)
        }
        catch (_: InterruptedException) {
        }
      }
    }
  }
}

// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.target

import com.intellij.execution.target.HostPort
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.externalSystem.service.remote.MultiLoaderObjectInputStream
import com.intellij.openapi.externalSystem.util.wsl.connectRetrying
import com.intellij.openapi.progress.ProgressManager
import kotlinx.coroutines.CancellationException
import org.gradle.initialization.BuildEventConsumer
import org.gradle.internal.remote.internal.RemoteConnection
import org.gradle.internal.remote.internal.inet.SocketInetAddress
import org.gradle.internal.remote.internal.inet.TcpOutgoingConnector
import org.gradle.internal.serialize.Serializers
import org.gradle.launcher.daemon.protocol.*
import org.gradle.tooling.BuildCancelledException
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.ResultHandler
import org.gradle.tooling.internal.provider.action.BuildActionSerializer
import org.jetbrains.plugins.gradle.service.execution.GradleServerConfigurationProvider
import org.jetbrains.plugins.gradle.tooling.proxy.TargetBuildParameters
import org.jetbrains.plugins.gradle.tooling.proxy.TargetIntermediateResultHandler
import java.io.ByteArrayInputStream
import java.net.InetAddress

internal class ToolingProxyConnector(
  private val hostPort: HostPort,
  private val classloaderHolder: GradleToolingProxyClassloaderHolder,
) {

  private companion object {
    private val log = logger<ToolingProxyConnector>()
  }

  fun collectAllEvents(
    targetBuildParameters: TargetBuildParameters,
    resultHandler: ResultHandler<Any?>,
    buildEventConsumer: BuildEventConsumer,
    intermediateResultHandler: TargetIntermediateResultHandler,
  ) {
    try {
      withConnection(hostPort) {
        dispatch(BuildEvent(targetBuildParameters))
        flush()
        listen(resultHandler, buildEventConsumer, intermediateResultHandler)
        dispatch(BuildEvent("ack"))
        flush()
      }
    }
    catch (e: CancellationException) {
      resultHandler.onFailure(BuildCancelledException("Build cancelled."))
      throw e
    }
    catch (e: Exception) {
      resultHandler.onFailure(GradleConnectionException("An error occurred", e))
      throw e
    }
  }

  private fun withConnection(hostPort: HostPort, handler: RemoteConnection<Message>.() -> Unit) {
    val inetAddress = InetAddress.getByName(hostPort.host)
    val connectCompletion = connectRetrying(5000) {
      ProgressManager.checkCanceled()
      TcpOutgoingConnector().connect(SocketInetAddress(inetAddress, hostPort.port))
    }
    val serializer = DaemonMessageSerializer.create(BuildActionSerializer.create())
    val connection = connectCompletion.create(Serializers.stateful(serializer))
    try {
      handler(connection)
    }
    finally {
      connection.stop()
      log.info("The connection to $hostPort was closed")
    }
  }

  private fun processMessages(
    message: Message,
    resultHandler: ResultHandler<Any?>,
    buildEventConsumer: BuildEventConsumer,
    intermediateResultHandler: TargetIntermediateResultHandler,
  ): Boolean {
    when (message) {
      is Success -> {
        val value = deserializeIfNeeded(message.value)
        resultHandler.onComplete(value)
        return true
      }
      is Failure -> {
        resultHandler.onFailure(message.value as? GradleConnectionException ?: GradleConnectionException(message.value.message))
        return true
      }
      is BuildEvent -> {
        buildEventConsumer.dispatch(message.payload)
        return false
      }
      is org.jetbrains.plugins.gradle.tooling.proxy.Output -> {
        buildEventConsumer.dispatch(message)
        return false
      }
      is org.jetbrains.plugins.gradle.tooling.proxy.IntermediateResult -> {
        val value = deserializeIfNeeded(message.value)
        intermediateResultHandler.onResult(message.type, value)
        return false
      }
      else -> {
        log.warn("An unexpected message of type [${message.javaClass}] was received from the daemon")
        return true
      }
    }
  }

  private fun RemoteConnection<Message>.listen(
    resultHandler: ResultHandler<Any?>,
    buildEventConsumer: BuildEventConsumer,
    intermediateResultHandler: TargetIntermediateResultHandler,
  ) {
    while (true) {
      ProgressManager.checkCanceled()
      val message = receive()
      if (message != null) {
        if (processMessages(message, resultHandler, buildEventConsumer, intermediateResultHandler)) {
          return
        }
      }
      Thread.yield()
    }
  }

  private fun deserializeIfNeeded(value: Any?): Any? {
    val bytes = value as? ByteArray ?: return value
    val deserialized = MultiLoaderObjectInputStream(ByteArrayInputStream(bytes), classloaderHolder.getClassloaders()).use {
      it.readObject()
    }
    return deserialized
  }

  class ToolingProxyConnectorFactory(
    private val classloaderHolder: GradleToolingProxyClassloaderHolder,
    private val serverEnvironmentSetup: GradleServerEnvironmentSetup,
    private val configurationProvider: GradleServerConfigurationProvider?,
  ) {

    fun getConnector(host: String, port: Int): ToolingProxyConnector {
      val address = resolveRemoteAddress(host, port)
      return ToolingProxyConnector(address, classloaderHolder)
    }

    private fun resolveRemoteAddress(host: String, port: Int): HostPort {
      val serverBindingPort = serverEnvironmentSetup.getServerBindingPort()
      val localPort = serverBindingPort?.localValue?.blockingGet(0)
      val targetPort = serverBindingPort?.targetValue?.blockingGet(0)
      val hostPort = if (targetPort == port && localPort != null) {
        HostPort(host, localPort)
      }
      else {
        HostPort(host, port)
      }
      val communicationAddress = configurationProvider?.getClientCommunicationAddress(
        serverEnvironmentSetup.getEnvironmentConfiguration(),
        hostPort
      )
      return communicationAddress ?: hostPort
    }
  }
}

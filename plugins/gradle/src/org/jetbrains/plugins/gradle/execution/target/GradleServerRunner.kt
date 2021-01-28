// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.target

import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.target.TargetEnvironment
import com.intellij.execution.target.TargetEnvironmentAwareRunProfileState.TargetProgressIndicator
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.target.TargetedCommandLine
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.util.io.BaseOutputReader
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.gradle.initialization.BuildEventConsumer
import org.gradle.internal.remote.internal.inet.SocketInetAddress
import org.gradle.internal.remote.internal.inet.TcpOutgoingConnector
import org.gradle.internal.serialize.Serializers
import org.gradle.launcher.cli.action.BuildActionSerializer
import org.gradle.launcher.daemon.protocol.BuildEvent
import org.gradle.launcher.daemon.protocol.DaemonMessageSerializer
import org.gradle.launcher.daemon.protocol.Failure
import org.gradle.launcher.daemon.protocol.Success
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.ResultHandler
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters
import org.jetbrains.plugins.gradle.tooling.proxy.TargetBuildParameters
import java.net.InetAddress

class GradleServerRunner(project: Project,
                         private val consumerOperationParameters: ConsumerOperationParameters) {
  private val serverEnvironmentSetup = GradleServerEnvironmentSetupImpl(project)

  fun run(environmentConfiguration: TargetEnvironmentConfiguration,
          targetBuildParametersBuilder: TargetBuildParameters.Builder,
          progressIndicator: TargetProgressIndicator,
          resultHandler: ResultHandler<Any?>) {
  val commandLine = serverEnvironmentSetup.prepareEnvironment(environmentConfiguration, targetBuildParametersBuilder,
                                                                consumerOperationParameters, progressIndicator)
    runTargetProcess(commandLine, serverEnvironmentSetup.targetEnvironment, progressIndicator, resultHandler)
  }

  private fun runTargetProcess(targetedCommandLine: TargetedCommandLine,
                               remoteEnvironment: TargetEnvironment,
                               targetProgressIndicator: TargetProgressIndicator,
                               resultHandler: ResultHandler<Any?>) {
    val process = remoteEnvironment.createProcess(targetedCommandLine, EmptyProgressIndicator())
    val processHandler: CapturingProcessHandler = object :
      CapturingProcessHandler(process, targetedCommandLine.charset, targetedCommandLine.getCommandPresentation(remoteEnvironment)) {
      override fun readerOptions(): BaseOutputReader.Options {
        return BaseOutputReader.Options.forMostlySilentProcess()
      }
    }
    val targetBuildParameters = serverEnvironmentSetup.targetBuildParameters
    val gradleServerEventsListener = GradleServerEventsListener(targetBuildParameters) {
      consumerOperationParameters.buildProgressListener.onEvent(it)
    }
    processHandler.addProcessListener(GradleServerProcessListener(targetProgressIndicator, resultHandler, gradleServerEventsListener))
    processHandler.runProcessWithProgressIndicator(EmptyProgressIndicator(), -1, true)
  }


  private class GradleServerEventsListener(private val targetBuildParameters: TargetBuildParameters,
                          //                 private val targetHostIp: String?,
                                           private val buildEventConsumer: BuildEventConsumer) {
    private lateinit var job: Job
    fun start(hostName: String, port: Int, resultHandler: ResultHandler<Any?>) {
      check(!::job.isInitialized) { "Gradle server events listener has already been started" }
      job = GlobalScope.launch {
        val inetAddress = /*if (targetHostIp != null) InetAddress.getByName(targetHostIp) else */InetAddress.getByName(hostName)
        val connectCompletion = TcpOutgoingConnector().connect(SocketInetAddress(inetAddress, port))
        val serializer = DaemonMessageSerializer.create(BuildActionSerializer.create())
        val connection = connectCompletion.create(Serializers.stateful(serializer))
        connection.dispatch(BuildEvent(targetBuildParameters))
        connection.flush()

        loop@ while (true) {
          val message = connection.receive()
          if (message == null) break
          when (message) {
            is Success -> resultHandler.onComplete(message.value)
            is Failure -> {
              resultHandler.onFailure(message.value as? GradleConnectionException ?: GradleConnectionException(message.value.message))
            }
            !is BuildEvent -> break@loop
            else -> buildEventConsumer.dispatch(message.payload)
          }
        }
      }
    }

    fun stop() {
      if (::job.isInitialized && job.isActive) {
        job.cancel()
      }
    }
  }

  private class GradleServerProcessListener(private val targetProgressIndicator: TargetProgressIndicator,
                                            private val resultHandler: ResultHandler<Any?>,
                                            private val gradleServerEventsListener: GradleServerEventsListener) : ProcessListener {
    @Volatile private var connectionAddressReceived = false
    @Volatile var resultReceived = false
    val resultHandlerWrapper: ResultHandler<Any?> = object : ResultHandler<Any?> {
      override fun onComplete(result: Any?) {
        resultReceived = true
        resultHandler.onComplete(result)
      }

      override fun onFailure(gradleConnectionException: GradleConnectionException?) {
        resultReceived = true
        resultHandler.onFailure(gradleConnectionException)
      }
    }

    override fun startNotified(event: ProcessEvent) {}
    override fun processTerminated(event: ProcessEvent) {
      if (!resultReceived) {
        resultHandler.onFailure(GradleConnectionException("Operation result has not been received."))
      }
      gradleServerEventsListener.stop()
    }

    override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
      // tooling proxy process output
      targetProgressIndicator.addText(event.text, outputType)
      if (connectionAddressReceived) return
      val prefix = "Gradle target server hostName: "
      if (event.text.startsWith(prefix)) {
        connectionAddressReceived = true
        val hostName = event.text.substringAfter(prefix).substringBefore(" port: ")
        val port = event.text.substringAfter(" port: ").trim().toInt()
        gradleServerEventsListener.start(hostName, port, resultHandlerWrapper)
      }
    }
  }

  companion object {
    private val log = logger<GradleServerRunner>()
  }
}
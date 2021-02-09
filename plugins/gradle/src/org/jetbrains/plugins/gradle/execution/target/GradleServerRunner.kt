// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.target

import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.target.TargetEnvironmentAwareRunProfileState.TargetProgressIndicator
import com.intellij.execution.target.TargetedCommandLine
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.util.io.BaseOutputReader
import org.gradle.initialization.BuildEventConsumer
import org.gradle.internal.remote.internal.RemoteConnection
import org.gradle.internal.remote.internal.inet.SocketInetAddress
import org.gradle.internal.remote.internal.inet.TcpOutgoingConnector
import org.gradle.internal.serialize.Serializers
import org.gradle.launcher.cli.action.BuildActionSerializer
import org.gradle.launcher.daemon.protocol.*
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.ResultHandler
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters
import org.jetbrains.plugins.gradle.tooling.proxy.TargetBuildParameters
import java.net.InetAddress
import java.util.concurrent.Future

internal class GradleServerRunner(private val connection: TargetProjectConnection,
                                  private val consumerOperationParameters: ConsumerOperationParameters) {

  fun run(targetBuildParametersBuilder: TargetBuildParameters.Builder, resultHandler: ResultHandler<Any?>) {
    val project: Project = connection.taskId?.findProject() ?: return
    val progressIndicator = MyTargetProgressIndicator(connection.taskId, connection.taskListener)
    val serverEnvironmentSetup = GradleServerEnvironmentSetupImpl(project)
    val commandLine = serverEnvironmentSetup.prepareEnvironment(connection.environmentConfiguration, connection.targetPathMapper,
                                                                targetBuildParametersBuilder, consumerOperationParameters,
                                                                progressIndicator)
    runTargetProcess(commandLine, serverEnvironmentSetup, progressIndicator, resultHandler)
  }

  private fun runTargetProcess(targetedCommandLine: TargetedCommandLine,
                               serverEnvironmentSetup: GradleServerEnvironmentSetupImpl,
                               targetProgressIndicator: TargetProgressIndicator,
                               resultHandler: ResultHandler<Any?>) {
    val remoteEnvironment = serverEnvironmentSetup.targetEnvironment
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
                                           private val buildEventConsumer: BuildEventConsumer) {
    private lateinit var listenerTask: Future<*>
    fun start(hostName: String, port: Int, resultHandler: ResultHandler<Any?>) {
      check(!::listenerTask.isInitialized) { "Gradle server events listener has already been started" }
      listenerTask = ApplicationManager.getApplication().executeOnPooledThread {
        try {
          doRun(targetBuildParameters, hostName, port, resultHandler, buildEventConsumer)
        }
        catch (t: Throwable) {
          resultHandler.onFailure(GradleConnectionException(t.message, t))
        }
      }
    }

    private fun doRun(targetBuildParameters: TargetBuildParameters,
                      hostName: String,
                      port: Int,
                      resultHandler: ResultHandler<Any?>,
                      buildEventConsumer: BuildEventConsumer) {
      val inetAddress = InetAddress.getByName(hostName)
      val connectCompletion = TcpOutgoingConnector().connect(SocketInetAddress(inetAddress, port))
      val serializer = DaemonMessageSerializer.create(BuildActionSerializer.create())
      val connection = connectCompletion.create(Serializers.stateful(serializer))
      connection.dispatch(BuildEvent(targetBuildParameters))
      connection.flush()

      try {
        loop@ while (true) {
          val message = connection.receive() ?: break
          when (message) {
            is Success -> {
              resultHandler.onComplete(message.value)
              break@loop
            }
            is Failure -> {
              resultHandler.onFailure(message.value as? GradleConnectionException ?: GradleConnectionException(message.value.message))
              break@loop
            }
            !is BuildEvent -> {
              break@loop
            }
            else -> {
              buildEventConsumer.dispatch(message.payload)
            }
          }
        }
      }
      finally {
        connection.sendResultAck()
      }
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
      while (!handler.invoke() || ::listenerTask.isInitialized && listenerTask.isDone && (System.currentTimeMillis() - startTime) < 10000) {
        val lock = Object()
        synchronized(lock) {
          try {
            lock.wait(100)
          }
          catch (ignore: InterruptedException) {
          }
        }
      }
    }
  }

  private class GradleServerProcessListener(private val targetProgressIndicator: TargetProgressIndicator,
                                            private val resultHandler: ResultHandler<Any?>,
                                            private val gradleServerEventsListener: GradleServerEventsListener) : ProcessListener {
    @Volatile
    private var connectionAddressReceived = false

    @Volatile
    var resultReceived = false

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
        gradleServerEventsListener.waitForResult { resultReceived }
      }
      if (!resultReceived) {
        resultHandler.onFailure(GradleConnectionException("Operation result has not been received."))
      }
      gradleServerEventsListener.stop()
    }

    override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
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

  class MyTargetProgressIndicator(private val taskId: ExternalSystemTaskId,
                                  private val taskListener: ExternalSystemTaskNotificationListener?) : TargetProgressIndicator {
    @Volatile
    var stopped = false
    override fun addText(text: String, outputType: Key<*>) {
      taskListener?.onTaskOutput(taskId, text, outputType === ProcessOutputTypes.STDOUT)
    }

    override fun isCanceled(): Boolean = false
    override fun stop() {
      stopped = true
    }

    override fun isStopped(): Boolean = stopped
  }

  companion object {
    private val log = logger<GradleServerRunner>()
  }
}
// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.target

import com.intellij.execution.Platform
import com.intellij.execution.process.*
import com.intellij.execution.target.HostPort
import com.intellij.execution.target.TargetEnvironmentAwareRunProfileState.TargetProgressIndicator
import com.intellij.execution.target.TargetEnvironmentType
import com.intellij.execution.target.TargetPlatform
import com.intellij.execution.target.TargetedCommandLine
import com.intellij.execution.target.value.getTargetUploadPath
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.io.BaseOutputReader
import com.intellij.util.text.nullize
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
import org.jetbrains.plugins.gradle.service.execution.GradleServerConfigurationProvider
import org.jetbrains.plugins.gradle.tooling.proxy.TargetBuildParameters
import org.jetbrains.plugins.gradle.util.GradleBundle
import java.net.InetAddress
import java.util.concurrent.Future

internal class GradleServerRunner(private val connection: TargetProjectConnection,
                                  private val consumerOperationParameters: ConsumerOperationParameters) {

  fun run(targetBuildParametersBuilder: TargetBuildParameters.Builder, resultHandler: ResultHandler<Any?>) {
    val project: Project = connection.taskId?.findProject() ?: return
    val progressIndicator = MyTargetProgressIndicator(connection.taskId, connection.taskListener)
    val environmentConfigurationProvider = connection.environmentConfigurationProvider
    val serverEnvironmentSetup = GradleServerEnvironmentSetupImpl(project, environmentConfigurationProvider)
    val commandLine = serverEnvironmentSetup.prepareEnvironment(targetBuildParametersBuilder, consumerOperationParameters,
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
    val projectUploadRoot = serverEnvironmentSetup.projectUploadRoot
    val targetProjectBasePath = projectUploadRoot.getTargetUploadPath().apply(remoteEnvironment)
    val localProjectBasePath = projectUploadRoot.localRootPath.toString()
    val targetPlatform = remoteEnvironment.targetPlatform

    val serverConfigurationProvider = connection.environmentConfigurationProvider as? GradleServerConfigurationProvider
    val connectionAddressResolver: (HostPort) -> HostPort = {
      serverConfigurationProvider?.getClientCommunicationAddress(serverEnvironmentSetup.environmentConfiguration, it) ?: it
    }
    val gradleServerEventsListener = GradleServerEventsListener(targetBuildParameters, connectionAddressResolver) {
      when (it) {
        is String -> {
          consumerOperationParameters.progressListener.run {
            onOperationStart(it)
            onOperationEnd()
          }
        }
        is org.jetbrains.plugins.gradle.tooling.proxy.StandardError -> {
          targetProgressIndicator.addText(
            resolveFistPath(it.text.useLocalLineSeparators(targetPlatform), targetProjectBasePath, localProjectBasePath, targetPlatform),
            ProcessOutputType.STDERR)
        }
        is org.jetbrains.plugins.gradle.tooling.proxy.StandardOutput -> {
          targetProgressIndicator.addText(
            resolveFistPath(it.text.useLocalLineSeparators(targetPlatform), targetProjectBasePath, localProjectBasePath, targetPlatform),
            ProcessOutputType.STDOUT)
        }
        else -> {
          consumerOperationParameters.buildProgressListener.onEvent(it)
        }
      }
    }

    val appStartedMessage = if (connection.getUserData(targetPreparationKey) == true) null
    else {
      connection.putUserData(targetPreparationKey, true)
      val targetTypeId = serverEnvironmentSetup.environmentConfiguration.typeId
      val targetDisplayName = TargetEnvironmentType.EXTENSION_NAME.findFirstSafe { it.id == targetTypeId }?.displayName
      targetDisplayName?.run { GradleBundle.message("gradle.target.execution.running", this) + "\n" }
    }
    processHandler.addProcessListener(
      GradleServerProcessListener(appStartedMessage, targetProgressIndicator, resultHandler, gradleServerEventsListener)
    )
    processHandler.runProcessWithProgressIndicator(EmptyProgressIndicator(), -1, true)
  }

  private fun String.useLocalLineSeparators(targetPlatform: TargetPlatform) =
    if (targetPlatform.platform == Platform.current()) this
    else replace(targetPlatform.platform.lineSeparator, Platform.current().lineSeparator)

  private fun String.useLocalFileSeparators(targetPlatform: TargetPlatform) =
    if (targetPlatform.platform == Platform.current()) this
    else replace(targetPlatform.platform.fileSeparator, Platform.current().fileSeparator)

  @NlsSafe
  private fun resolveFistPath(@NlsSafe text: String,
                              targetProjectBasePath: String,
                              localProjectBasePath: String,
                              targetPlatform: TargetPlatform): String {
    val pathIndexStart = text.indexOf(targetProjectBasePath)
    if (pathIndexStart == -1) return text

    val delimiter = if (pathIndexStart == 0) ' '
    else {
      val char = text[pathIndexStart - 1]
      if (char != '\'' && char != '\"') ' ' else char
    }
    var pathIndexEnd = text.indexOf(delimiter, pathIndexStart)
    if (pathIndexEnd == -1) {
      pathIndexEnd = text.indexOf('\n', pathIndexStart)
    }

    if (pathIndexEnd == -1) pathIndexEnd = text.length
    val path = text.substring(pathIndexStart + targetProjectBasePath.length, pathIndexEnd).useLocalFileSeparators(targetPlatform)

    val buf = StringBuilder()
    buf.append(text.subSequence(0, pathIndexStart))
    buf.append(localProjectBasePath)
    buf.append(path)
    buf.append(text.substring(pathIndexEnd))
    return buf.toString()
  }

  private class GradleServerEventsListener(private val targetBuildParameters: TargetBuildParameters,
                                           private val connectionAddressResolver: (HostPort) -> HostPort,
                                           private val buildEventConsumer: BuildEventConsumer) {
    private lateinit var listenerTask: Future<*>
    fun start(hostName: String, port: Int, resultHandler: ResultHandler<Any?>) {
      check(!::listenerTask.isInitialized) { "Gradle server events listener has already been started" }
      listenerTask = ApplicationManager.getApplication().executeOnPooledThread {
        try {
          val hostPort = connectionAddressResolver.invoke(HostPort(hostName, port))
          doRun(targetBuildParameters, hostPort, resultHandler, buildEventConsumer)
        }
        catch (t: Throwable) {
          resultHandler.onFailure(GradleConnectionException(t.message, t))
        }
      }
    }

    private fun doRun(targetBuildParameters: TargetBuildParameters,
                      hostName: HostPort,
                      resultHandler: ResultHandler<Any?>,
                      buildEventConsumer: BuildEventConsumer) {
      val inetAddress = InetAddress.getByName(hostName.host)
      val connectCompletion = TcpOutgoingConnector().connect(SocketInetAddress(inetAddress, hostName.port))
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
            is org.jetbrains.plugins.gradle.tooling.proxy.Output -> {
              buildEventConsumer.dispatch(message)
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
      while (!handler.invoke() &&
             (::listenerTask.isInitialized.not() || !listenerTask.isDone) &&
             System.currentTimeMillis() - startTime < 10000) {
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

  private class GradleServerProcessListener(private val appStartedMessage: String?,
                                            private val targetProgressIndicator: TargetProgressIndicator,
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

    override fun startNotified(event: ProcessEvent) {
      appStartedMessage?.let { targetProgressIndicator.addText(it, ProcessOutputType.STDOUT) }
    }

    override fun processTerminated(event: ProcessEvent) {
      if (!resultReceived) {
        gradleServerEventsListener.waitForResult { resultReceived || targetProgressIndicator.isCanceled }
      }
      if (!resultReceived) {
        val outputType = if (event.exitCode == 0) ProcessOutputType.STDOUT else ProcessOutputType.STDERR
        event.text?.also { targetProgressIndicator.addText(it, outputType) }
        resultHandler.onFailure(GradleConnectionException("Operation result has not been received."))
      }
      gradleServerEventsListener.stop()
    }

    override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
      log.traceIfNotEmpty(event.text)
      if (connectionAddressReceived) return
      if (outputType === ProcessOutputTypes.STDERR) {
        targetProgressIndicator.addText(event.text, outputType)
      }
      if (event.text.startsWith(connectionConfLinePrefix)) {
        connectionAddressReceived = true
        val hostName = event.text.substringAfter(connectionConfLinePrefix).substringBefore(" port: ")
        val port = event.text.substringAfter(" port: ").trim().toInt()
        gradleServerEventsListener.start(hostName, port, resultHandlerWrapper)
      }
    }

    companion object {
      private const val connectionConfLinePrefix = "Gradle target server hostName: "
    }
  }

  class MyTargetProgressIndicator(private val taskId: ExternalSystemTaskId,
                                  private val taskListener: ExternalSystemTaskNotificationListener?) : TargetProgressIndicator {
    @Volatile
    var stopped = false
    override fun addText(text: String, outputType: Key<*>) {
      taskListener?.onTaskOutput(taskId, text, outputType != ProcessOutputTypes.STDERR)
    }

    override fun isCanceled(): Boolean = false
    override fun stop() {
      stopped = true
    }

    override fun isStopped(): Boolean = stopped
  }

  companion object {
    private val log = logger<GradleServerRunner>()
    private val targetPreparationKey = Key.create<Boolean>("target preparation key")
  }
}

private fun Logger.traceIfNotEmpty(text: @NlsSafe String?) {
  text.nullize(true)?.also { trace { it } }
}

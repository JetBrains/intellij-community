// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.target

import com.intellij.execution.Platform
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutputType
import com.intellij.execution.target.TargetPlatform
import com.intellij.execution.target.TargetedCommandLine
import com.intellij.execution.target.value.getTargetUploadPath
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.provider.osFamily
import com.intellij.util.io.BaseOutputReader
import org.gradle.tooling.ResultHandler
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters
import org.jetbrains.plugins.gradle.service.execution.GradleServerConfigurationProvider
import org.jetbrains.plugins.gradle.tooling.proxy.TargetBuildParameters
import java.nio.file.InvalidPathException
import java.nio.file.Path

internal class GradleServerRunner(private val connection: TargetProjectConnection,
                                  private val consumerOperationParameters: ConsumerOperationParameters,
                                  private val prepareTaskState: Boolean) {

  fun run(
    classloaderHolder: GradleToolingProxyClassloaderHolder,
    targetBuildParametersBuilder: TargetBuildParameters.Builder<*>,
    resultHandler: ResultHandler<Any?>,
  ) {
    val project: Project = connection.taskId?.findProject() ?: return
    val taskProgressIndicator = ProgressManager.getInstance()
      .progressIndicator ?: throw IllegalStateException("There are no progress indicator assigned. " +
                                                        "GradleServerRunner should be runned with a correct ProgressIndicator " +
                                                        "available in the thread context!")
    consumerOperationParameters.cancellationToken.addCallback(taskProgressIndicator::cancel)
    val serverEnvironmentSetup = GradleServerEnvironmentSetupImpl(project, connection, prepareTaskState)

    val progressIndicator = GradleServerProgressIndicator(connection.taskId, connection.taskListener, taskProgressIndicator)
    val commandLine = serverEnvironmentSetup.prepareEnvironment(targetBuildParametersBuilder, consumerOperationParameters, progressIndicator)
    runTargetProcess(commandLine, serverEnvironmentSetup, progressIndicator, resultHandler, classloaderHolder)
  }

  private fun runTargetProcess(
    targetedCommandLine: TargetedCommandLine,
    serverEnvironmentSetup: GradleServerEnvironmentSetup,
    targetProgressIndicator: GradleServerProgressIndicator,
    resultHandler: ResultHandler<Any?>,
    classloaderHolder: GradleToolingProxyClassloaderHolder,
  ) {
    targetProgressIndicator.checkCanceled()
    val remoteEnvironment = serverEnvironmentSetup.getTargetEnvironment()
    val process = remoteEnvironment.createProcess(targetedCommandLine, EmptyProgressIndicator())
    val processHandler: CapturingProcessHandler = object :
      CapturingProcessHandler(process, targetedCommandLine.charset, targetedCommandLine.getCommandPresentation(remoteEnvironment)) {
      override fun readerOptions(): BaseOutputReader.Options {
        return BaseOutputReader.Options.forMostlySilentProcess()
      }
    }
    val projectUploadRoot = serverEnvironmentSetup.getProjectUploadRoot()
    val targetProjectBasePath = projectUploadRoot.getTargetUploadPath().apply(remoteEnvironment)
    val localProjectBasePath = projectUploadRoot.localRootPath.toString()
    val targetPlatform = remoteEnvironment.targetPlatform
    val samePlatform = try {
      targetProjectBasePath.getPathPlatform() == localProjectBasePath.getPathPlatform()
    }
    catch (_: InvalidPathException) {
      false
    }

    val connectorFactory = ToolingProxyConnector.ToolingProxyConnectorFactory(
      classloaderHolder,
      serverEnvironmentSetup,
      connection.environmentConfigurationProvider as? GradleServerConfigurationProvider,
      connection.taskId
    )
    val serverProcessListener = GradleServerProcessListener(
      targetProgressIndicator,
      serverEnvironmentSetup,
      resultHandler,
      connectorFactory
    ) {
      when (it) {
        is String -> {
          consumerOperationParameters.progressListener.run {
            onOperationStart(it)
            onOperationEnd()
          }
        }
        is org.jetbrains.plugins.gradle.tooling.proxy.StandardError -> {
          val text = replaceTargetPathsWithLocal(
            it.text.useLocalLineSeparators(targetPlatform),
            targetProjectBasePath,
            localProjectBasePath,
            samePlatform,
            targetPlatform
          )
          targetProgressIndicator.addText(text, ProcessOutputType.STDERR)
        }
        is org.jetbrains.plugins.gradle.tooling.proxy.StandardOutput -> {
          val text = replaceTargetPathsWithLocal(
            it.text.useLocalLineSeparators(targetPlatform),
            targetProjectBasePath,
            localProjectBasePath,
            samePlatform,
            targetPlatform
          )
          targetProgressIndicator.addText(text, ProcessOutputType.STDOUT)
        }
        else -> {
          consumerOperationParameters.buildProgressListener.onEvent(it)
        }
      }
    }

    processHandler.addProcessListener(serverProcessListener)
    processHandler.runProcessWithProgressIndicator(targetProgressIndicator.progressIndicator, -1, true)

    serverProcessListener.waitForServerShutdown()
  }

  private fun String.useLocalLineSeparators(targetPlatform: TargetPlatform) =
    if (targetPlatform.platform == Platform.current()) this
    else replace(targetPlatform.platform.lineSeparator, Platform.current().lineSeparator)

  private fun String.useLocalFileSeparators(platform: Platform, uriMode: Boolean): String {
    val separator = if (uriMode)
      '/'
    else
      Platform.current().fileSeparator

    return if (platform.fileSeparator == separator) this
    else replace(platform.fileSeparator, separator)
  }

  @NlsSafe
  private fun replaceTargetPathsWithLocal(
    @NlsSafe text: String,
    targetProjectBasePath: String,
    localProjectBasePath: String,
    samePlatform: Boolean,
    targetPlatform: TargetPlatform,
  ): String {
    val pathIndexStart = text.indexOf(targetProjectBasePath)
    if (pathIndexStart == -1) {
      return text
    }
    // we could just replace the entire path
    if (samePlatform) {
      return text.replace(targetProjectBasePath, localProjectBasePath)
    }

    if (pathIndexStart != 0) {
      val char = text[pathIndexStart - 1]
      // the path is escaped with ' or " -> we could define the end of the path string
      if (char == '\'' || char == '\"') {
        var pathIndexEnd = text.indexOf(char, pathIndexStart)
        if (pathIndexEnd == -1) {
          pathIndexEnd = text.indexOf('\n', pathIndexStart)
        }

        if (pathIndexEnd == -1) pathIndexEnd = text.length
        val subPathStart = pathIndexStart + targetProjectBasePath.length
        // we've faced with a broken string, there is nothing we could do to separate the path from the text content
        if (subPathStart >= pathIndexEnd) {
          return text
        }

        val isUri = text.substring(maxOf(0, pathIndexStart - 7), maxOf(0, pathIndexStart - 1)).endsWith("file:/")
        val path = text.substring(subPathStart, pathIndexEnd).useLocalFileSeparators(targetPlatform.platform, isUri)

        val buf = StringBuilder()
        buf.append(text.subSequence(0, pathIndexStart))
        buf.append(localProjectBasePath.useLocalFileSeparators(Platform.current(), isUri))
        buf.append(path)
        buf.append(text.substring(pathIndexEnd))
        return buf.toString()
      }
    }
    return text
  }

  private fun String.getPathPlatform(): EelOsFamily = Path.of(this).osFamily

}
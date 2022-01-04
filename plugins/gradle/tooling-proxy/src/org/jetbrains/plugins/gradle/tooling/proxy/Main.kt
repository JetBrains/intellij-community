// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.proxy

import org.gradle.internal.remote.internal.inet.InetEndpoint
import org.gradle.launcher.daemon.protocol.BuildEvent
import org.gradle.launcher.daemon.protocol.DaemonMessageSerializer
import org.gradle.launcher.daemon.protocol.Failure
import org.gradle.launcher.daemon.protocol.Success
import org.gradle.tooling.*
import org.gradle.tooling.internal.consumer.BlockingResultHandler
import org.gradle.tooling.internal.provider.action.BuildActionSerializer
import org.gradle.tooling.model.build.BuildEnvironment
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.InternalBuildIdentifier
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.InternalJavaEnvironment
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.build.InternalBuildEnvironment
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.ObjectOutputStream
import java.util.logging.ConsoleHandler
import java.util.logging.Level
import java.util.logging.Logger

object Main {
  const val LOCAL_BUILD_PROPERTY = "idea.gradle.target.local"
  private lateinit var LOG: org.slf4j.Logger
  private lateinit var serverConnector: TargetTcpServerConnector
  private lateinit var incomingConnectionHandler: TargetIncomingConnectionHandler

  @JvmStatic
  fun main(args: Array<String>) {
    initLogging(args)
    try {
      doMain()
    }
    finally {
      if (::serverConnector.isInitialized) {
        serverConnector.stop()
      }
    }
  }

  private fun doMain() {
    serverConnector = TargetTcpServerConnector(DaemonMessageSerializer.create(BuildActionSerializer.create()))
    incomingConnectionHandler = TargetIncomingConnectionHandler()
    val address = serverConnector.start(incomingConnectionHandler) { LOG.error("connection error") } as InetEndpoint
    println("Gradle target server hostAddress: ${address.candidates.first().hostAddress} port: ${address.port}")
    waitForIncomingConnection()
    waitForBuildParameters()

    val targetBuildParameters = incomingConnectionHandler.targetBuildParameters()
    LOG.debug("targetBuildParameters: $targetBuildParameters")

    val connector = GradleConnector.newConnector()
    val workingDirectory = File(".").canonicalFile
    LOG.debug("Working directory: ${workingDirectory.absolutePath}")
    connector.forProjectDirectory(workingDirectory.absoluteFile)
    val gradleHome = targetBuildParameters.gradleHome
    if (gradleHome != null) {
      connector.useInstallation(File(gradleHome))
    }
    val gradleUserHome = targetBuildParameters.gradleUserHome
    if (gradleUserHome != null) {
      connector.useGradleUserHomeDir(File(gradleUserHome))
    }

    try {
      val result = connector.connect().use { runBuildAndGetResult(targetBuildParameters, it, workingDirectory) }
      LOG.debug("operation result: $result")
      val adapted = maybeConvert(result)
      val bos = ByteArrayOutputStream()
      var bytes = ByteArray(1);
      ObjectOutputStream(bos).use {
        it.writeObject(adapted)
        it.flush()
        bytes = bos.toByteArray()
      }

      incomingConnectionHandler.dispatch(Success(bytes))
    }
    catch (t: Throwable) {
      LOG.debug("GradleConnectionException: $t")
      incomingConnectionHandler.dispatch(Failure(t))
    }
    finally {
      incomingConnectionHandler.receiveResultAck()
    }
  }

  private fun runBuildAndGetResult(targetBuildParameters: TargetBuildParameters,
                                   connection: ProjectConnection,
                                   workingDirectory: File): Any? {
    val resultHandler = BlockingResultHandler(Any::class.java)
    val operation = when (targetBuildParameters) {
      is BuildLauncherParameters -> connection.newBuild().apply {
        targetBuildParameters.tasks.nullize()?.run { forTasks(*toTypedArray()) }
      }
      is TestLauncherParameters -> connection.newTestLauncher()
      is ModelBuilderParameters<*> -> connection.model(targetBuildParameters.modelType).apply {
        targetBuildParameters.tasks.nullize()?.run { forTasks(*toTypedArray()) }
      }
      is BuildActionParameters<*> -> connection.action(targetBuildParameters.buildAction).apply {
        targetBuildParameters.tasks.nullize()?.run { forTasks(*toTypedArray()) }
      }
      is PhasedBuildActionParameters<*> -> connection.action()
        .projectsLoaded(targetBuildParameters.projectsLoadedAction, IntermediateResultHandler {
          //resultHandler.onComplete(it)
        })
        .buildFinished(targetBuildParameters.buildFinishedAction, IntermediateResultHandler {
          resultHandler.onComplete(it)
        })
        .build().apply {
          targetBuildParameters.tasks.nullize()?.run { forTasks(*toTypedArray()) }
        }
    }
    val progressEventConverter = ProgressEventConverter()
    operation.apply {
      setStandardError(OutputWrapper { incomingConnectionHandler.dispatch(StandardError(it)) })
      setStandardOutput(OutputWrapper { incomingConnectionHandler.dispatch(StandardOutput(it)) })
      addProgressListener(
        { incomingConnectionHandler.dispatch(BuildEvent(progressEventConverter.convert(it))) },
        targetBuildParameters.progressListenerOperationTypes
      )
      addProgressListener(ProgressListener {
        val description = it.description
        if (description.isNotEmpty()) {
          incomingConnectionHandler.dispatch(BuildEvent(description))
        }
      })
      val arguments = mutableListOf<String>()
      val initScriptsFiles = createOrFindInitScriptFiles(workingDirectory, targetBuildParameters.initScripts)
      for (initScript in initScriptsFiles) {
        arguments.add("--init-script")
        arguments.add(initScript.absolutePath)
      }
      arguments.addAll(targetBuildParameters.arguments)
      withArguments(arguments)
      setJvmArguments(targetBuildParameters.jvmArguments)
      if (targetBuildParameters.environmentVariables.isNotEmpty()) {
        setEnvironmentVariables(targetBuildParameters.environmentVariables)
      }

      when (this) {
        is BuildLauncher -> run(resultHandler)
        is TestLauncher -> run(resultHandler)
        is ModelBuilder<*> -> get(resultHandler)
        is BuildActionExecuter<*> -> run(resultHandler)
      }
    }
    return resultHandler.result
  }

  private fun createOrFindInitScriptFiles(workingDirectory: File,
                                          initScripts: Map<String, String>): List<File> {
    if (initScripts.isEmpty()) return emptyList()
    val projectDotGradleDir = File(workingDirectory, ".gradle")
    val ideaInitScriptsDir = File(projectDotGradleDir, "ideaInitScripts")
    ideaInitScriptsDir.mkdirs()
    val arguments = mutableListOf<File>()
    for ((filePrefix, scriptContent) in initScripts) {
      val content = scriptContent.toByteArray()
      val contentSize = content.size
      val initScriptFile = ideaInitScriptsDir.findSequentChild(filePrefix, "gradle") {
        if (!it.exists()) {
          it.writeText(scriptContent)
          return@findSequentChild true
        }
        if (contentSize.toLong() != it.length()) false
        else it.isFile && content.contentEquals(it.readBytes())
      }
      arguments.add(initScriptFile)
    }
    return arguments
  }

  private fun maybeConvert(result: Any?): Any? {
    if (result is BuildEnvironment) {
      return InternalBuildEnvironment({ InternalBuildIdentifier(result.buildIdentifier.rootDir) },
                                      { result.java.run { InternalJavaEnvironment(javaHome, jvmArguments) } },
                                      { result.gradle.gradleUserHome },
                                      result.gradle.gradleVersion)
    }
    return result
  }

  private fun waitForIncomingConnection() {
    waitFor({ incomingConnectionHandler.isConnected() },
            "Waiting for incoming connection....",
            "Incoming connection timeout")
  }

  private fun waitForBuildParameters() {
    waitFor({ incomingConnectionHandler.isBuildParametersReceived() },
            "Waiting for target build parameters....",
            "Target build parameters were not received")
  }

  private fun waitFor(handler: () -> Boolean, waitingMessage: String, timeOutMessage: String) {
    val startTime = System.currentTimeMillis()
    while (!handler.invoke() && (System.currentTimeMillis() - startTime) < 5000) {
      LOG.debug(waitingMessage)
      val lock = Object()
      synchronized(lock) {
        try {
          lock.wait(100)
        }
        catch (ignore: InterruptedException) {
        }
      }
    }
    check(handler.invoke()) { timeOutMessage }
  }

  private fun initLogging(args: Array<String>) {
    val loggingArguments = mapOf(
      "--debug" to Level.FINE,
      "--info" to Level.INFO,
      "--warn" to Level.WARNING,
      "--error" to Level.SEVERE,
      "--trace" to Level.FINER
    )
    val loggingLevel = args.find { it in loggingArguments } ?: "--error"
    Logger.getLogger("").apply {
      if (handlers.isEmpty()) {
        addHandler(ConsoleHandler())
      }
      level = loggingArguments[loggingLevel]
    }

    LOG = LoggerFactory.getLogger(Main::class.java)
  }
}
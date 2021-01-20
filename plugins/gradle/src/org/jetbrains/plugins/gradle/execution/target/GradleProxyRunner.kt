// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.target

import com.intellij.execution.configurations.SimpleJavaParameters
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.target.*
import com.intellij.execution.target.TargetEnvironmentAwareRunProfileState.TargetProgressIndicator
import com.intellij.execution.target.java.JavaLanguageRuntimeConfiguration
import com.intellij.execution.target.local.LocalTargetEnvironmentFactory
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.intellij.execution.target.value.DeferredTargetValue
import com.intellij.execution.target.value.TargetValue
import com.intellij.lang.LangBundle
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JdkUtil
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.BaseOutputReader
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.gradle.api.invocation.Gradle
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
import org.gradle.wrapper.WrapperExecutor
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.plugins.gradle.tooling.proxy.Main
import org.jetbrains.plugins.gradle.tooling.proxy.Main.LOCAL_BUILD_PROPERTY
import org.jetbrains.plugins.gradle.tooling.proxy.TargetBuildParameters
import org.jetbrains.plugins.gradle.tooling.proxy.TargetBuildParameters.TasksAwareBuilder
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.slf4j.LoggerFactory
import org.slf4j.impl.Log4jLoggerFactory
import java.io.File
import java.net.InetAddress
import java.nio.charset.StandardCharsets
import java.nio.file.Paths

class GradleProxyRunner(private val project: Project,
                        private val consumerOperationParameters: ConsumerOperationParameters) {
  private val environmentPromise = AsyncPromise<Pair<TargetEnvironment, TargetProgressIndicator>>()
  private val dependingOnEnvironmentPromise = mutableListOf<Promise<Unit>>()
  private val uploads = mutableListOf<Upload>()
  private lateinit var targetBuildParameters: TargetBuildParameters

  fun run(environmentConfiguration: TargetEnvironmentConfiguration,
          targetBuildParametersBuilder: TargetBuildParameters.Builder,
          progressIndicator: TargetProgressIndicator,
          resultHandler: ResultHandler<Any?>) {
    val (commandLine, remoteEnvironment) = prepareCommandLine(environmentConfiguration, targetBuildParametersBuilder, progressIndicator)
    runTargetProcess(commandLine, remoteEnvironment, progressIndicator, resultHandler)
  }

  private fun prepareCommandLine(environmentConfiguration: TargetEnvironmentConfiguration,
                                 targetBuildParametersBuilder: TargetBuildParameters.Builder,
                                 progressIndicator: TargetProgressIndicator): Pair<TargetedCommandLine, TargetEnvironment> {
    val factory = if (environmentConfiguration.typeId == "local") LocalTargetEnvironmentFactory()
    else environmentConfiguration.createEnvironmentFactory(project)
    val request = factory.createRequest()

    val javaParameters = SimpleJavaParameters()
    val targetArguments = request.requestUploadFileArguments(consumerOperationParameters, environmentConfiguration, javaParameters)
    val targetedCommandLineBuilder = createTargetedCommandLine(request, environmentConfiguration, javaParameters)
    val remoteEnvironment = factory.prepareRemoteEnvironment(request, progressIndicator)
    JdkUtil.COMMAND_LINE_SETUP_KEY.get(targetedCommandLineBuilder).provideEnvironment(remoteEnvironment, progressIndicator)
    environmentPromise.setResult(remoteEnvironment to progressIndicator)
    for (upload in uploads) {
      upload.volume.upload(upload.relativePath, progressIndicator)
    }
    for (promise in dependingOnEnvironmentPromise) {
      promise.blockingGet(0)  // Just rethrows errors.
    }

    targetBuildParameters = targetBuildParametersBuilder.build(consumerOperationParameters, targetArguments)
    return targetedCommandLineBuilder.build() to remoteEnvironment
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
    processHandler.addProcessListener(object : ProcessListener {
      private var connectionAddressReceived = false
      override fun startNotified(event: ProcessEvent) {}
      override fun processTerminated(event: ProcessEvent) {
        if (!resultReceived) {
          resultHandler.onFailure(GradleConnectionException("Operation result has not been received."))
        }
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
          monitorBuild(hostName, port, resultHandlerWrapper)
        }
      }

      private fun monitorBuild(hostName: String, port: Int, resultHandler: ResultHandler<Any?>) = GlobalScope.launch {
        val connectCompletion = TcpOutgoingConnector().connect(SocketInetAddress(InetAddress.getByName(hostName), port))
        val serializer = DaemonMessageSerializer.create(BuildActionSerializer.create())
        val connection = connectCompletion.create(Serializers.stateful(serializer))
        connection.dispatch(BuildEvent(targetBuildParameters))
        connection.flush()

        val buildEventConsumer = BuildEventConsumer {
          consumerOperationParameters.buildProgressListener.onEvent(it)
        }
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
    })
    processHandler.runProcessWithProgressIndicator(EmptyProgressIndicator(), 10000, true)
  }

  private fun createTargetedCommandLine(request: TargetEnvironmentRequest,
                                        targetEnvironmentConfiguration: TargetEnvironmentConfiguration,
                                        javaParameters: SimpleJavaParameters): TargetedCommandLineBuilder {
    val classPath = javaParameters.classPath
    // kotlin-stdlib-jdk8
    classPath.add(PathManager.getJarPathForClass(KotlinVersion::class.java))
    // gradle-api jar
    classPath.add(PathManager.getJarPathForClass(Gradle::class.java))
    // gradle-api-impldep jar
    classPath.add(PathManager.getJarPathForClass(org.gradle.internal.impldep.com.google.common.base.Function::class.java))
    // gradle-wrapper jar
    classPath.add(PathManager.getJarPathForClass(WrapperExecutor::class.java))
    // logging jars
    classPath.add(PathManager.getJarPathForClass(LoggerFactory::class.java))
    classPath.add(PathManager.getJarPathForClass(Log4jLoggerFactory::class.java))
    classPath.add(PathManager.getJarPathForClass(org.apache.log4j.Level::class.java))
    // gradle tooling proxy module
    classPath.add(PathManager.getJarPathForClass(Main::class.java))

    javaParameters.mainClass = Main::class.java.name
    javaParameters.setWorkingDirectory(consumerOperationParameters.projectDir)

    if (request is LocalTargetEnvironmentRequest) {
      javaParameters.vmParametersList.addProperty(LOCAL_BUILD_PROPERTY, "true")
      val javaHomePath = consumerOperationParameters.javaHome.path
      ProjectJdkTable.getInstance().allJdks.find { FileUtil.pathsEqual(it.homePath, javaHomePath) }?.let {
        javaParameters.jdk = it
      }
    }

    if (log.isDebugEnabled) {
      javaParameters.programParametersList.add("--debug")
    }
    return javaParameters.toCommandLine(request, targetEnvironmentConfiguration)
  }

  private fun TargetBuildParameters.Builder.build(operationParameters: ConsumerOperationParameters,
                                                  arguments: List<Pair<String, TargetValue<String>?>>): TargetBuildParameters {
    val resolvedBuildArguments = mutableListOf<String>()
    for ((arg, argValue) in arguments) {
      if (argValue == null) {
        resolvedBuildArguments.add(arg)
      }
      else {
        val targetPath = argValue.targetValue?.blockingGet(0) ?: argValue.localValue?.blockingGet(0) ?: continue
        resolvedBuildArguments.add(arg)
        resolvedBuildArguments.add(targetPath)
      }
    }

    withArguments(resolvedBuildArguments)
    withJvmArguments(operationParameters.jvmArguments ?: emptyList())
    (this as? TasksAwareBuilder)?.withTasks(operationParameters.tasks ?: emptyList())
    return build()
  }

  private fun TargetEnvironmentRequest.requestUploadFileArguments(parameters: ConsumerOperationParameters,
                                                                  environmentConfiguration: TargetEnvironmentConfiguration,
                                                                  javaParameters: SimpleJavaParameters):
    List<Pair<String, TargetValue<String>?>> {
    if (parameters.arguments.isNullOrEmpty()) return emptyList()

    val targetBuildArguments = mutableListOf<Pair<String, TargetValue<String>?>>()
    val iterator = parameters.arguments.iterator()
    while (iterator.hasNext()) {
      val arg = iterator.next()
      if (arg == GradleConstants.INIT_SCRIPT_CMD_OPTION && iterator.hasNext()) {
        val path = iterator.next()
        targetBuildArguments.add(arg to requestUploadIntoTarget(path, this, environmentConfiguration))
        val file = File(path)
        if (file.name.startsWith("ijinit") && file.extension == GradleConstants.EXTENSION) {
          val fileContent = FileUtil.loadFile(file, StandardCharsets.UTF_8)
          // based on the format of the `/org/jetbrains/plugins/gradle/tooling/internal/init/init.gradle` file
          val toolingExtensionsPaths = fileContent.substringAfter("initscript {\n" +
                                                                  "  dependencies {\n" +
                                                                  "    classpath files(").substringBefore(")\n" +
                                                                                                             "  }")
            .drop(2).dropLast(2).split("\",\"")
          for (toolingExtensionsPath in toolingExtensionsPaths) {
            javaParameters.classPath.add(toolingExtensionsPath)
          }
        }
      }
      else {
        targetBuildArguments.add(arg to null)
      }
    }
    return targetBuildArguments
  }

  private fun requestUploadIntoTarget(path: String,
                                      request: TargetEnvironmentRequest,
                                      environmentConfiguration: TargetEnvironmentConfiguration): TargetValue<String> {
    val uploadPath = Paths.get(FileUtil.toSystemDependentName(path))
    val localRootPath = uploadPath.parent

    val languageRuntime = environmentConfiguration.runtimes.findByType(JavaLanguageRuntimeConfiguration::class.java)
    val toolingFileOnTarget = LanguageRuntimeType.VolumeDescriptor(LanguageRuntimeType.VolumeType("gradleToolingFilesOnTarget"), "", "",
                                                                   "", request.projectPathOnTarget ?: "")
    val uploadRoot = languageRuntime?.createUploadRoot(toolingFileOnTarget, localRootPath) ?: TargetEnvironment.UploadRoot(localRootPath,
                                                                                                                           TargetEnvironment.TargetPath.Temporary())
    request.uploadVolumes += uploadRoot
    val result = DeferredTargetValue(path)

    dependingOnEnvironmentPromise += environmentPromise.then { (environment, targetProgressIndicator) ->
      if (targetProgressIndicator.isCanceled || targetProgressIndicator.isStopped) {
        result.stopProceeding()
        return@then
      }
      val volume = environment.uploadVolumes.getValue(uploadRoot)
      try {
        val relativePath = uploadPath.fileName.toString()
        val resolvedTargetPath = volume.resolveTargetPath(relativePath)
        uploads.add(Upload(volume, relativePath))
        result.resolve(resolvedTargetPath)
      }
      catch (t: Throwable) {
        targetProgressIndicator.stopWithErrorMessage(
          LangBundle.message("progress.message.failed.to.resolve.0.1", volume.localRoot, t.localizedMessage))
        result.resolveFailure(t)
      }
    }
    return result
  }

  private class Upload(val volume: TargetEnvironment.UploadableVolume, val relativePath: String)

  companion object {
    private val log = logger<GradleProxyRunner>()
  }
}
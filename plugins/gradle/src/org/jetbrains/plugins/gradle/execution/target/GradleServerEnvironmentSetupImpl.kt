// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.target

import com.intellij.execution.configurations.SimpleJavaParameters
import com.intellij.execution.target.*
import com.intellij.execution.target.java.JavaLanguageRuntimeConfiguration
import com.intellij.execution.target.local.LocalTargetEnvironmentFactory
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.intellij.execution.target.value.DeferredTargetValue
import com.intellij.execution.target.value.TargetValue
import com.intellij.execution.wsl.target.WslTargetEnvironmentConfiguration
import com.intellij.lang.LangBundle
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JdkUtil
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.PathMapper
import com.intellij.util.PathMappingSettings
import com.intellij.util.io.systemIndependentPath
import org.gradle.api.invocation.Gradle
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters
import org.gradle.wrapper.WrapperExecutor
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.plugins.gradle.tooling.proxy.Main
import org.jetbrains.plugins.gradle.tooling.proxy.TargetBuildParameters
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.slf4j.LoggerFactory
import org.slf4j.impl.Log4jLoggerFactory
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Paths
import java.util.*

internal class GradleServerEnvironmentSetupImpl(private val project: Project) : GradleServerEnvironmentSetup, UserDataHolderBase() {
  override val javaParameters = SimpleJavaParameters()
  override lateinit var environmentConfiguration: TargetEnvironmentConfiguration
  lateinit var targetEnvironment: TargetEnvironment
  lateinit var targetBuildParameters: TargetBuildParameters

  private val environmentPromise = AsyncPromise<Pair<TargetEnvironment, TargetEnvironmentAwareRunProfileState.TargetProgressIndicator>>()
  private val dependingOnEnvironmentPromise = mutableListOf<Promise<Unit>>()
  private val uploads = mutableListOf<Upload>()
  private val localPathsToMap = mutableListOf<String>()

  fun prepareEnvironment(environmentConfiguration: TargetEnvironmentConfiguration,
                         targetPathMapper: PathMapper?,
                         targetBuildParametersBuilder: TargetBuildParameters.Builder,
                         consumerOperationParameters: ConsumerOperationParameters,
                         progressIndicator: TargetEnvironmentAwareRunProfileState.TargetProgressIndicator): TargetedCommandLine {
    initJavaParameters(consumerOperationParameters)

    this.environmentConfiguration = environmentConfiguration

    val factory = if (environmentConfiguration.typeId == "local") LocalTargetEnvironmentFactory()
    else environmentConfiguration.createEnvironmentFactory(project)

    val (request, targetArguments) =
      prepareTargetEnvironmentRequest(factory, consumerOperationParameters, environmentConfiguration, progressIndicator)

    val targetedCommandLineBuilder = javaParameters.toCommandLine(request, environmentConfiguration)
    val remoteEnvironment = factory.prepareRemoteEnvironment(request, progressIndicator)
    targetEnvironment = remoteEnvironment
    JdkUtil.COMMAND_LINE_SETUP_KEY.get(targetedCommandLineBuilder).provideEnvironment(remoteEnvironment, progressIndicator)
    EP.forEachExtensionSafe {
      it.handleCreatedTargetEnvironment(remoteEnvironment, this, progressIndicator)
    }
    environmentPromise.setResult(remoteEnvironment to progressIndicator)
    for (upload in uploads) {
      upload.volume.upload(upload.relativePath, progressIndicator)
    }
    for (promise in dependingOnEnvironmentPromise) {
      promise.blockingGet(0)  // Just rethrows errors.
    }

    targetBuildParameters = targetBuildParametersBuilder.build(consumerOperationParameters, targetArguments)

    val pathMappingSettings = PathMappingSettings()
    val pathMapperRoots = StringBuilder()
    val entries = targetEnvironment.uploadVolumes.entries
    for ((_, uploadableVolume) in entries) {
      val localRoot = uploadableVolume.localRoot.systemIndependentPath
      val targetRoot = uploadableVolume.targetRoot
      pathMapperRoots.appendLine(localRoot).appendLine(targetRoot)
      pathMappingSettings.addMappingCheckUnique(localRoot, targetRoot)
    }
    for (localPath in localPathsToMap) {
      if (targetPathMapper != null && targetPathMapper.canReplaceLocal(localPath)) {
        pathMapperRoots.appendLine(localPath).appendLine(targetPathMapper.convertToRemote(localPath))
      }
      else if (pathMappingSettings.canReplaceLocal(localPath)) {
        pathMapperRoots.appendLine(localPath).appendLine(pathMappingSettings.convertToRemote(localPath))
      }
    }
    val variableValue = Base64.getEncoder().encodeToString(pathMapperRoots.toString().toByteArray(Charsets.UTF_8))
    targetedCommandLineBuilder.addEnvironmentVariable("target_path_mapping", variableValue)
    (environmentConfiguration as? WslTargetEnvironmentConfiguration)?.distribution?.wslIp?.also {
      targetedCommandLineBuilder.addEnvironmentVariable("targetHost", it)
    }
    return targetedCommandLineBuilder.build()
  }

  private fun prepareTargetEnvironmentRequest(factory: TargetEnvironmentFactory,
                                              consumerOperationParameters: ConsumerOperationParameters,
                                              environmentConfiguration: TargetEnvironmentConfiguration,
                                              progressIndicator: TargetEnvironmentAwareRunProfileState.TargetProgressIndicator): Pair<TargetEnvironmentRequest, List<Pair<String, TargetValue<String>?>>> {
    val request = factory.createRequest()
    if (request is LocalTargetEnvironmentRequest) {
      javaParameters.vmParametersList.addProperty(Main.LOCAL_BUILD_PROPERTY, "true")
      val javaHomePath = consumerOperationParameters.javaHome.path
      ProjectJdkTable.getInstance().allJdks.find { FileUtil.pathsEqual(it.homePath, javaHomePath) }?.let { javaParameters.jdk = it }
    }

    val targetArguments = request.requestFileArgumentsUpload(consumerOperationParameters, environmentConfiguration, javaParameters)

    EP.forEachExtensionSafe {
      it.prepareTargetEnvironmentRequest(request, this, progressIndicator)
    }
    return request to targetArguments
  }

  private fun TargetEnvironmentRequest.requestFileArgumentsUpload(parameters: ConsumerOperationParameters,
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
        if (file.extension != GradleConstants.EXTENSION) continue
        val fileContent = FileUtil.loadFile(file, StandardCharsets.UTF_8)
        if (file.name.startsWith("ijinit")) {
          // based on the format of the `/org/jetbrains/plugins/gradle/tooling/internal/init/init.gradle` file
          val toolingExtensionsPaths = fileContent.substringAfter("initscript {\n" +
                                                                  "  dependencies {\n" +
                                                                  "    classpath files(").substringBefore(")\n" +
                                                                                                          "  }")
            .drop(10).dropLast(3).split("\"),mapPath(\"")
          for (toolingExtensionsPath in toolingExtensionsPaths) {
            javaParameters.classPath.add(toolingExtensionsPath)
            localPathsToMap += toolingExtensionsPath
          }
        }
        else if (!file.name.startsWith("ijmapper")) {
          val regex = Regex("mapPath\\(['|\"](.{2,})['|\"][)]")
          val matches = regex.findAll(fileContent)
          val elements = matches.map { it.groupValues[1] }
          localPathsToMap.addAll(elements)
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
    val toolingFileOnTarget = LanguageRuntimeType.VolumeDescriptor(LanguageRuntimeType.VolumeType("gradleToolingFilesOnTarget"),
                                                                   "", "", "", request.projectPathOnTarget)
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
    (this as? TargetBuildParameters.TasksAwareBuilder)?.withTasks(operationParameters.tasks ?: emptyList())
    return build()
  }

  private fun initJavaParameters(consumerOperationParameters: ConsumerOperationParameters) {
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
    if (log.isDebugEnabled) {
      javaParameters.programParametersList.add("--debug")
    }
  }

  private class Upload(val volume: TargetEnvironment.UploadableVolume, val relativePath: String)

  companion object {
    private val log = logger<GradleServerEnvironmentSetupImpl>()
    private val EP = ExtensionPointName.create<GradleTargetEnvironmentAware>("org.jetbrains.plugins.gradle.targetEnvironmentAware")
  }
}
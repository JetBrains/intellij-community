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
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JdkUtil
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.io.FileUtil.*
import com.intellij.util.PathMapper
import com.intellij.util.PathMappingSettings
import org.gradle.api.invocation.Gradle
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters
import org.gradle.wrapper.WrapperExecutor
import org.jetbrains.annotations.NotNull
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionHelper
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionHelper.toGroovyString
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.tooling.proxy.Main
import org.jetbrains.plugins.gradle.tooling.proxy.TargetBuildParameters
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.GradleConstants.INIT_SCRIPT_CMD_OPTION
import org.slf4j.LoggerFactory
import org.slf4j.impl.Log4jLoggerFactory
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Paths

internal class GradleServerEnvironmentSetupImpl(private val project: Project) : GradleServerEnvironmentSetup, UserDataHolderBase() {
  override val javaParameters = SimpleJavaParameters()
  override lateinit var environmentConfiguration: TargetEnvironmentConfiguration
  lateinit var targetEnvironment: TargetEnvironment
  lateinit var targetBuildParameters: TargetBuildParameters
  lateinit var projectUploadRoot: TargetEnvironment.UploadRoot

  private val environmentPromise = AsyncPromise<Pair<TargetEnvironment, TargetEnvironmentAwareRunProfileState.TargetProgressIndicator>>()
  private val dependingOnEnvironmentPromise = mutableListOf<Promise<Unit>>()
  private val uploads = mutableListOf<Upload>()
  private val localPathsToMap = mutableListOf<String>()

  fun prepareEnvironment(environmentConfiguration: TargetEnvironmentConfiguration,
                         targetPathMapper: PathMapper?,
                         targetBuildParametersBuilder: TargetBuildParameters.Builder,
                         consumerOperationParameters: ConsumerOperationParameters,
                         progressIndicator: TargetEnvironmentAwareRunProfileState.TargetProgressIndicator): TargetedCommandLine {
    initJavaParameters()

    this.environmentConfiguration = environmentConfiguration

    val factory = if (environmentConfiguration.typeId == "local") LocalTargetEnvironmentFactory()
    else environmentConfiguration.createEnvironmentFactory(project)

    val (request, targetArguments) =
      prepareTargetEnvironmentRequest(factory, consumerOperationParameters, targetPathMapper, environmentConfiguration, progressIndicator)

    val initScriptTargetValue = requestPathMapperInitScriptUpload(targetPathMapper, request, environmentConfiguration)

    val targetedCommandLineBuilder = javaParameters.toCommandLine(request, environmentConfiguration)
    projectUploadRoot = setupTargetProjectDirectories(consumerOperationParameters, request, targetedCommandLineBuilder)
    val remoteEnvironment = factory.prepareRemoteEnvironment(request, progressIndicator)
    targetEnvironment = remoteEnvironment
    JdkUtil.COMMAND_LINE_SETUP_KEY.get(targetedCommandLineBuilder).provideEnvironment(remoteEnvironment, progressIndicator)
    EP.forEachExtensionSafe {
      it.handleCreatedTargetEnvironment(remoteEnvironment, this, progressIndicator)
    }
    environmentPromise.setResult(remoteEnvironment to progressIndicator)
    upload(progressIndicator)

    targetBuildParametersBuilder.withArguments(INIT_SCRIPT_CMD_OPTION, initScriptTargetValue.targetValue.blockingGet(0)!!)
    targetBuildParameters = targetBuildParametersBuilder.build(consumerOperationParameters, targetArguments)

    (environmentConfiguration as? WslTargetEnvironmentConfiguration)?.distribution?.wslIp?.also {
      targetedCommandLineBuilder.addEnvironmentVariable("targetHost", it)
    }
    return targetedCommandLineBuilder.build()
  }

  private fun setupTargetProjectDirectories(consumerOperationParameters: ConsumerOperationParameters,
                                            request: TargetEnvironmentRequest,
                                            targetedCommandLineBuilder: @NotNull TargetedCommandLineBuilder): TargetEnvironment.UploadRoot {
    val pathsToUpload: MutableSet<String> = HashSet()

    val workingDir = consumerOperationParameters.projectDir
    val gradleProjectDirectory = toSystemDependentName(workingDir.path)
    pathsToUpload.add(gradleProjectDirectory)

    val projectSettings = GradleSettings.getInstance(project).getLinkedProjectSettings(
      ExternalSystemApiUtil.toCanonicalPath(workingDir.path))
    projectSettings?.modules?.mapTo(pathsToUpload) { toSystemDependentName(it) }

    val commonAncestor = findCommonAncestor(pathsToUpload)
    val uploadPath = Paths.get(toSystemDependentName(commonAncestor!!))
    val uploadRoot = TargetEnvironment.UploadRoot(uploadPath, TargetEnvironment.TargetPath.Temporary())
    request.uploadVolumes += uploadRoot
    val targetFileSeparator = request.targetPlatform.platform.fileSeparator

    var targetWorkingDirectory: TargetValue<String>? = null
    for (path in pathsToUpload) {
      val relativePath = getRelativePath(commonAncestor, path, File.separatorChar)
      val targetValue = upload(uploadRoot, path, relativePath!!)
      if (targetWorkingDirectory == null && isAncestor(path, gradleProjectDirectory, false)) {
        val workingDirRelativePath = getRelativePath(path, gradleProjectDirectory, File.separatorChar)!!
        val targetWorkingDirRelativePath = if (workingDirRelativePath == ".") ""
        else toSystemDependentName(workingDirRelativePath, targetFileSeparator)
        targetWorkingDirectory = TargetValue.map(targetValue) { "$it$targetFileSeparator$targetWorkingDirRelativePath" }
      }
    }
    targetedCommandLineBuilder.setWorkingDirectory(targetWorkingDirectory!!)
    return uploadRoot
  }

  private fun findCommonAncestor(paths: Set<String>): String? {
    var commonRoot: File? = null
    for (path in paths) {
      commonRoot = if (commonRoot == null) File(path) else findAncestor(commonRoot, File(path))
      requireNotNull(commonRoot) { "no common root found" }
    }
    assert(commonRoot != null)
    return commonRoot!!.path
  }

  private fun requestPathMapperInitScriptUpload(targetPathMapper: PathMapper?,
                                                request: TargetEnvironmentRequest,
                                                environmentConfiguration: TargetEnvironmentConfiguration): TargetValue<String> {
    val pathMappingSettings = PathMappingSettings()
    val mapperInitScript = StringBuilder("ext.pathMapper = [:]\n")
    for (localPath in localPathsToMap) {
      if (targetPathMapper != null && targetPathMapper.canReplaceLocal(localPath)) {
        val targetPath = targetPathMapper.convertToRemote(localPath)
        mapperInitScript.append("ext.pathMapper.put(\"${toGroovyString(localPath)}\", \"${toGroovyString(targetPath)}\")\n")
      }
      else if (pathMappingSettings.canReplaceLocal(localPath)) {
        val targetPath = pathMappingSettings.convertToRemote(localPath)
        mapperInitScript.append("ext.pathMapper.put(\"${toGroovyString(localPath)}\", \"${toGroovyString(targetPath)}\")\n")
      }
    }
    mapperInitScript.append("ext.mapPath = { path -> pathMapper.get(path) ?: path }")

    val initScript = GradleExecutionHelper.writeToFileGradleInitScript(mapperInitScript.toString(), "ijtgtmapper")
    return requestUploadIntoTarget(initScript.path, request, environmentConfiguration)
  }

  private fun upload(uploadRoot: TargetEnvironment.UploadRoot,
                     uploadPathString: String,
                     uploadRelativePath: String): TargetValue<String> {
    val result = DeferredTargetValue(uploadPathString)
    dependingOnEnvironmentPromise += environmentPromise.then { (environment, progress) ->
      val volume = environment.uploadVolumes.getValue(uploadRoot)
      val resolvedTargetPath = volume.resolveTargetPath(uploadRelativePath)
      volume.upload(uploadRelativePath, progress)
      result.resolve(resolvedTargetPath)
    }
    return result
  }

  private fun upload(progressIndicator: TargetEnvironmentAwareRunProfileState.TargetProgressIndicator) {
    for (upload in uploads) {
      upload.volume.upload(upload.relativePath, progressIndicator)
    }
    uploads.clear()
    for (promise in dependingOnEnvironmentPromise) {
      promise.blockingGet(0)  // Just rethrows errors.
    }
    dependingOnEnvironmentPromise.clear()
  }

  private fun prepareTargetEnvironmentRequest(factory: TargetEnvironmentFactory,
                                              consumerOperationParameters: ConsumerOperationParameters,
                                              targetPathMapper: PathMapper?,
                                              environmentConfiguration: TargetEnvironmentConfiguration,
                                              progressIndicator: TargetEnvironmentAwareRunProfileState.TargetProgressIndicator): Pair<TargetEnvironmentRequest, List<Pair<String, TargetValue<String>?>>> {
    val request = factory.createRequest()
    if (request is LocalTargetEnvironmentRequest) {
      javaParameters.vmParametersList.addProperty(Main.LOCAL_BUILD_PROPERTY, "true")
      val javaHomePath = consumerOperationParameters.javaHome.path
      ProjectJdkTable.getInstance().allJdks.find { pathsEqual(it.homePath, javaHomePath) }?.let { javaParameters.jdk = it }
    }
    else {
      if (environmentConfiguration.runtimes.findByType(JavaLanguageRuntimeConfiguration::class.java) == null) {
        val localJavaHomePath = consumerOperationParameters.javaHome.path
        val targetJavaHomePath: String
        if (targetPathMapper?.canReplaceLocal(localJavaHomePath) == true) {
          targetJavaHomePath = targetPathMapper.convertToRemote(localJavaHomePath)
        }
        else {
          targetJavaHomePath = localJavaHomePath
        }
        val javaLanguageRuntimeConfiguration = JavaLanguageRuntimeConfiguration().apply { homePath = targetJavaHomePath }
        environmentConfiguration.addLanguageRuntime(javaLanguageRuntimeConfiguration)
      }
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
      if (arg == INIT_SCRIPT_CMD_OPTION && iterator.hasNext()) {
        val path = iterator.next()
        targetBuildArguments.add(arg to requestUploadIntoTarget(path, this, environmentConfiguration))
        val file = File(path)
        if (file.extension != GradleConstants.EXTENSION) continue
        val fileContent = loadFile(file, StandardCharsets.UTF_8)
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
    val uploadPath = Paths.get(toSystemDependentName(path))
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

  private fun initJavaParameters() {
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
    // intellij.gradle.toolingExtension - for use of model adapters classes
    classPath.add(PathManager.getJarPathForClass(
      org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.InternalBuildIdentifier::class.java))

    javaParameters.mainClass = Main::class.java.name
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
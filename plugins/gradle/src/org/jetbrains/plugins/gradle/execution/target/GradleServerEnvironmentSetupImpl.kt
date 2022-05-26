// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.target

import com.intellij.execution.Platform
import com.intellij.execution.configurations.SimpleJavaParameters
import com.intellij.execution.target.*
import com.intellij.execution.target.java.JavaLanguageRuntimeConfiguration
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.intellij.execution.target.value.DeferredLocalTargetValue
import com.intellij.execution.target.value.DeferredTargetValue
import com.intellij.execution.target.value.TargetValue
import com.intellij.lang.LangCoreBundle
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType
import com.intellij.openapi.externalSystem.service.execution.TargetEnvironmentConfigurationProvider
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.io.FileUtil.*
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.util.PathMapper
import com.intellij.util.PathMappingSettings
import com.intellij.util.io.isDirectory
import com.intellij.util.text.nullize
import org.gradle.api.invocation.Gradle
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters
import org.gradle.wrapper.WrapperExecutor
import org.jetbrains.annotations.NotNull
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.plugins.gradle.execution.target.GradleServerEnvironmentSetup.Companion.targetJavaExecutablePathMappingKey
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionHelper.toGroovyString
import org.jetbrains.plugins.gradle.service.execution.GradleServerConfigurationProvider
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.tooling.proxy.Main
import org.jetbrains.plugins.gradle.tooling.proxy.TargetBuildParameters
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.GradleConstants.INIT_SCRIPT_CMD_OPTION
import org.slf4j.LoggerFactory
import org.slf4j.impl.JDK14LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

internal class GradleServerEnvironmentSetupImpl(private val project: Project,
                                                private val classpathInferer: GradleServerClasspathInferer,
                                                private val environmentConfigurationProvider: TargetEnvironmentConfigurationProvider) : GradleServerEnvironmentSetup, UserDataHolderBase() {
  override val javaParameters = SimpleJavaParameters()
  override lateinit var environmentConfiguration: TargetEnvironmentConfiguration
  lateinit var targetEnvironment: TargetEnvironment
  lateinit var targetBuildParameters: TargetBuildParameters
  lateinit var projectUploadRoot: TargetEnvironment.UploadRoot

  private val targetEnvironmentProvider = TargetEnvironmentProvider()
  private val localPathsToMap = LinkedHashSet<String>()

  var serverBindingPort: TargetValue<Int>? = null

  fun prepareEnvironment(targetBuildParametersBuilder: TargetBuildParameters.Builder,
                         consumerOperationParameters: ConsumerOperationParameters,
                         progressIndicator: GradleServerRunner.GradleServerProgressIndicator): TargetedCommandLine {
    progressIndicator.checkCanceled()
    initJavaParameters()

    this.environmentConfiguration = environmentConfigurationProvider.environmentConfiguration
    val targetPathMapper = environmentConfigurationProvider.pathMapper

    val request = if (environmentConfiguration.typeId == "local") LocalTargetEnvironmentRequest()
    else environmentConfiguration.createEnvironmentRequest(project)

    environmentConfiguration.runtimes.findByType(GradleRuntimeTargetConfiguration::class.java)?.homePath?.nullize(true)?.also {
      targetBuildParametersBuilder.useInstallation(it)
    }

    progressIndicator.checkCanceled()
    val targetArguments =
      prepareTargetEnvironmentRequest(request, consumerOperationParameters, targetPathMapper, environmentConfiguration, progressIndicator)

    progressIndicator.checkCanceled()
    val targetedCommandLineBuilder = javaParameters.toCommandLine(request)

    (environmentConfigurationProvider as? GradleServerConfigurationProvider)?.getServerBindingAddress(environmentConfiguration)?.also {
      targetedCommandLineBuilder.addEnvironmentVariable("serverBindingHost", it.host.nullize())
      targetedCommandLineBuilder.addEnvironmentVariable("serverBindingPort", it.port.toString())
      if (it.port != 0) {
        serverBindingPort = targetEnvironmentProvider.requestPort(request, it.port)
      }
    }

    projectUploadRoot = setupTargetProjectDirectories(consumerOperationParameters, request, targetedCommandLineBuilder)
    val remoteEnvironment = request.prepareEnvironment(progressIndicator)
    targetEnvironment = remoteEnvironment
    EP.forEachExtensionSafe {
      it.handleCreatedTargetEnvironment(remoteEnvironment, this, progressIndicator)
    }

    progressIndicator.checkCanceled()
    targetEnvironmentProvider.supplyEnvironmentAndRunHandlers(remoteEnvironment, progressIndicator)
    val pathMapperInitScript = createTargetPathMapperInitScript(request, targetPathMapper, environmentConfiguration)
    targetBuildParametersBuilder.withInitScript("ijtgtmapper", pathMapperInitScript)
    targetBuildParameters = targetBuildParametersBuilder.build(consumerOperationParameters, targetArguments)
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
    projectSettings?.modules
      ?.filter { Files.exists(Path.of(it)) }
      ?.mapTo(pathsToUpload) { toSystemDependentName(it) }

    val commonAncestor = findCommonAncestor(pathsToUpload)
    val uploadPath = Paths.get(toSystemDependentName(commonAncestor!!))
    val uploadRoot = TargetEnvironment.UploadRoot(uploadPath, TargetEnvironment.TargetPath.Temporary())
    request.uploadVolumes += uploadRoot
    val targetFileSeparator = request.targetPlatform.platform.fileSeparator

    var targetWorkingDirectory: TargetValue<String>? = null
    for (path in pathsToUpload) {
      val relativePath = getRelativePath(commonAncestor, path, File.separatorChar)
      val targetValue = targetEnvironmentProvider.upload(uploadRoot, path, relativePath!!)
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

  private fun createTargetPathMapperInitScript(request: TargetEnvironmentRequest,
                                               targetPathMapper: PathMapper?,
                                               environmentConfiguration: TargetEnvironmentConfiguration): String {
    val pathMappingSettings = PathMappingSettings(targetEnvironmentProvider.pathMappingSettings.pathMappings)
    for ((uploadRoot, uploadableVolume) in targetEnvironment.uploadVolumes) {
      val localRootPath = uploadRoot.localRootPath
      val relativePath = if (localRootPath.isDirectory()) "." else localRootPath.fileName.toString()
      pathMappingSettings.addMapping(localRootPath.toString(), uploadableVolume.resolveTargetPath(relativePath))
    }
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

    // add target java executable mapping
    val platform = request.targetPlatform.platform
    val java = if (platform == Platform.WINDOWS) "java.exe" else "java"
    val javaRuntime = environmentConfiguration.runtimes.findByType(JavaLanguageRuntimeConfiguration::class.java)
    if (javaRuntime != null) {
      val targetJavaExecutablePath = arrayOf(javaRuntime.homePath, "bin", java).joinToString(platform.fileSeparator.toString())
      mapperInitScript.append(
        "ext.pathMapper.put(\"${targetJavaExecutablePathMappingKey}\", \"${toGroovyString(targetJavaExecutablePath)}\")\n")
    }
    else {
      mapperInitScript.append("ext.pathMapper.put(\"${targetJavaExecutablePathMappingKey}\", \"${java}\")\n")
    }
    mapperInitScript.append("ext.mapPath = { path -> pathMapper.get(path) ?: path }")
    return mapperInitScript.toString()
  }

  private fun prepareTargetEnvironmentRequest(request: TargetEnvironmentRequest,
                                              consumerOperationParameters: ConsumerOperationParameters,
                                              targetPathMapper: PathMapper?,
                                              environmentConfiguration: TargetEnvironmentConfiguration,
                                              progressIndicator: TargetProgressIndicator): List<Pair<String, TargetValue<String>?>> {
    if (request is LocalTargetEnvironmentRequest) {
      javaParameters.vmParametersList.addProperty(Main.LOCAL_BUILD_PROPERTY, "true")
      val javaHomePath = consumerOperationParameters.javaHome.path
      ProjectJdkTable.getInstance().allJdks.find { pathsEqual(it.homePath, javaHomePath) }?.let { javaParameters.jdk = it }
    }
    else {
      if (environmentConfiguration.runtimes.findByType(JavaLanguageRuntimeConfiguration::class.java) == null) {
        val localJavaHomePath = consumerOperationParameters.javaHome.path
        val targetJavaHomePath = targetPathMapper.maybeConvertToRemote(localJavaHomePath)
        val javaLanguageRuntimeConfiguration = JavaLanguageRuntimeConfiguration().apply { homePath = targetJavaHomePath }
        environmentConfiguration.addLanguageRuntime(javaLanguageRuntimeConfiguration)
      }
    }

    val targetArguments = request.requestFileArgumentsUpload(consumerOperationParameters, environmentConfiguration, javaParameters)

    EP.forEachExtensionSafe {
      it.prepareTargetEnvironmentRequest(request, this, progressIndicator)
    }
    return targetArguments
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
        targetBuildArguments.add(arg to targetEnvironmentProvider.requestUploadIntoTarget(path, this, environmentConfiguration))
        val file = File(path)
        if (file.extension != GradleConstants.EXTENSION) continue
        if (file.name.startsWith("ijinit")) {
          val fileContent = loadFile(file, CharsetToolkit.UTF8, true)
          // based on the format of the `/org/jetbrains/plugins/gradle/tooling/internal/init/init.gradle` file
          val toolingExtensionsPaths = fileContent
            .substringAfter(
              "initscript {\n" +
              "  dependencies {\n" +
              "    classpath files(", "")
            .substringBefore(
              ")\n" +
              "  }", "")
            .nullize()
            ?.drop(10)?.dropLast(3)?.split("\"),mapPath(\"")
          if (toolingExtensionsPaths != null) {
            for (toolingExtensionsPath in toolingExtensionsPaths) {
              javaParameters.classPath.add(toolingExtensionsPath)
              localPathsToMap += toolingExtensionsPath
            }
          }
        }
        else if (!file.name.startsWith("ijmapper")) {
          val fileContent = loadFile(file, CharsetToolkit.UTF8, true)
          val regex = Regex("mapPath\\(['|\"](.{2,}?)['|\"][)]")
          val matches = regex.findAll(fileContent)
          matches.mapTo(localPathsToMap) { it.groupValues[1] }
        }
      }
      else {
        targetBuildArguments.add(arg to null)
      }
    }
    return targetBuildArguments
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
    withEnvironmentVariables(operationParameters.environmentVariables ?: emptyMap())
    (this as? TargetBuildParameters.TasksAwareBuilder)?.withTasks(operationParameters.tasks ?: emptyList())
    return build()
  }

  private fun initJavaParameters() {
    // kotlin-stdlib-jdk8
    classpathInferer.add(KotlinVersion::class.java)
    // gradle-api jar
    classpathInferer.add(Gradle::class.java)
    // gradle-api-impldep jar
    classpathInferer.add(org.gradle.internal.impldep.com.google.common.base.Function::class.java)
    // gradle-wrapper jar
    classpathInferer.add(WrapperExecutor::class.java)
    // logging jars
    classpathInferer.add(LoggerFactory::class.java)
    classpathInferer.add(JDK14LoggerFactory::class.java)
    // gradle tooling proxy module
    classpathInferer.add(Main::class.java)
    // intellij.gradle.toolingExtension - for use of model adapters classes
    classpathInferer.add(
      org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.InternalBuildIdentifier::class.java)
    // intellij.platform.externalSystem.rt
    classpathInferer.add(ExternalSystemSourceType::class.java)

    javaParameters.classPath.addAll(classpathInferer.getClasspath())
    javaParameters.mainClass = Main::class.java.name
    if (log.isDebugEnabled) {
      javaParameters.programParametersList.add("--debug")
    }
  }

  private class TargetEnvironmentProvider {
    private val environmentPromise = AsyncPromise<Pair<TargetEnvironment, TargetProgressIndicator>>()
    private val dependingOnEnvironmentPromise = mutableListOf<Promise<Unit>>()
    private val uploads = mutableListOf<Upload>()
    val pathMappingSettings = PathMappingSettings()

    fun supplyEnvironmentAndRunHandlers(targetEnvironment: TargetEnvironment,
                                        progressIndicator: GradleServerRunner.GradleServerProgressIndicator) {
      environmentPromise.setResult(targetEnvironment to progressIndicator)
      for (promise in dependingOnEnvironmentPromise) {
        progressIndicator.checkCanceled()
        promise.blockingGet(0)  // Just rethrows errors.
      }
      dependingOnEnvironmentPromise.clear()
      for (upload in uploads) {
        progressIndicator.checkCanceled()
        upload.volume.upload(upload.relativePath, progressIndicator)
      }
      uploads.clear()
    }

    fun upload(uploadRoot: TargetEnvironment.UploadRoot,
               uploadPathString: String,
               uploadRelativePath: String): TargetValue<String> {
      val result = DeferredTargetValue(uploadPathString)
      doWhenEnvironmentPrepared { environment, progress ->
        val volume = environment.uploadVolumes.getValue(uploadRoot)
        val resolvedTargetPath = volume.resolveTargetPath(uploadRelativePath)
        volume.upload(uploadRelativePath, progress)
        result.resolve(resolvedTargetPath)
        pathMappingSettings.addMapping(uploadPathString, resolvedTargetPath)
      }
      return result
    }

    fun requestUploadIntoTarget(path: String,
                                request: TargetEnvironmentRequest,
                                environmentConfiguration: TargetEnvironmentConfiguration): TargetValue<String> {
      val uploadPath = Paths.get(toSystemDependentName(path))
      val localRootPath = uploadPath.parent

      val languageRuntime = environmentConfiguration.runtimes.findByType(JavaLanguageRuntimeConfiguration::class.java)
      val toolingFileOnTarget = LanguageRuntimeType.VolumeDescriptor(LanguageRuntimeType.VolumeType("gradleToolingFilesOnTarget"),
                                                                     "", "", "", "")
      val uploadRoot = languageRuntime?.createUploadRoot(toolingFileOnTarget, localRootPath) ?: TargetEnvironment.UploadRoot(localRootPath,
                                                                                                                             TargetEnvironment.TargetPath.Temporary())
      request.uploadVolumes += uploadRoot
      val result = DeferredTargetValue(path)
      doWhenEnvironmentPrepared(result::stopProceeding) { environment, targetProgressIndicator ->
        val volume = environment.uploadVolumes.getValue(uploadRoot)
        try {
          val relativePath = uploadPath.fileName.toString()
          val resolvedTargetPath = volume.resolveTargetPath(relativePath)
          uploads.add(Upload(volume, relativePath))
          result.resolve(resolvedTargetPath)
        }
        catch (t: Throwable) {
          targetProgressIndicator.stopWithErrorMessage(
            LangCoreBundle.message("progress.message.failed.to.resolve.0.1", volume.localRoot, t.localizedMessage))
          result.resolveFailure(t)
        }
      }
      return result
    }

    fun requestPort(request: TargetEnvironmentRequest, targetPort: Int): TargetValue<Int> {
      val binding = TargetEnvironment.TargetPortBinding(null, targetPort)
      request.targetPortBindings.add(binding)
      val result = DeferredLocalTargetValue(targetPort)
      doWhenEnvironmentPrepared { environment, _ ->
        val localPort = environment.targetPortBindings[binding]
        result.resolve(localPort)
      }
      return result
    }

    private fun doWhenEnvironmentPrepared(onCancel: () -> Unit = {}, block: (TargetEnvironment, TargetProgressIndicator) -> Unit) {
      dependingOnEnvironmentPromise += environmentPromise.then { (environment, progress) ->
        if (progress.isCanceled || progress.isStopped) {
          onCancel.invoke()
        }
        else {
          block(environment, progress)
        }
      }
    }
  }

  private class Upload(val volume: TargetEnvironment.UploadableVolume, val relativePath: String)

  companion object {
    private val log = logger<GradleServerEnvironmentSetupImpl>()
    private val EP = ExtensionPointName.create<GradleTargetEnvironmentAware>("org.jetbrains.plugins.gradle.targetEnvironmentAware")
  }
}
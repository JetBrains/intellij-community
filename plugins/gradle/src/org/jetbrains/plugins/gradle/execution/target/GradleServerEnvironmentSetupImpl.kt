// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.target
// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import com.intellij.execution.Platform
import com.intellij.execution.configurations.SimpleJavaParameters
import com.intellij.execution.target.*
import com.intellij.execution.target.java.JavaLanguageRuntimeConfiguration
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.intellij.execution.target.value.TargetValue
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.util.PathMapper
import com.intellij.util.PathMappingSettings
import com.intellij.util.text.nullize
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.gradle.execution.target.GradleServerEnvironmentSetup.Companion.targetJavaExecutablePathMappingKey
import org.jetbrains.plugins.gradle.execution.target.GradleServerEnvironmentSetupImpl.Helper.collectInitScripts
import org.jetbrains.plugins.gradle.execution.target.GradleServerEnvironmentSetupImpl.Helper.extractPathsFromInitScript
import org.jetbrains.plugins.gradle.execution.target.GradleServerEnvironmentSetupImpl.Helper.extractPathsToMapFromInitScripts
import org.jetbrains.plugins.gradle.execution.target.GradleServerEnvironmentSetupImpl.Helper.getToolingProxyDefaultJavaParameters
import org.jetbrains.plugins.gradle.service.execution.*
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.tooling.proxy.Main
import org.jetbrains.plugins.gradle.tooling.proxy.TargetBuildParameters
import org.jetbrains.plugins.gradle.tooling.proxy.TargetIntermediateResultHandler
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.GradleConstants.INIT_SCRIPT_CMD_OPTION
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.isDirectory

internal class GradleServerEnvironmentSetupImpl(
  private val project: Project,
  private val connection: TargetProjectConnection,
  private val prepareTaskState: Boolean,
) : GradleServerEnvironmentSetup {

  private val javaParameters = getToolingProxyDefaultJavaParameters()
  private lateinit var environmentConfiguration: TargetEnvironmentConfiguration
  private lateinit var targetEnvironment: TargetEnvironment
  private lateinit var targetIntermediateResultHandler: TargetIntermediateResultHandler
  private lateinit var targetBuildParameters: TargetBuildParameters
  private lateinit var projectUploadRoot: TargetEnvironment.UploadRoot
  private val targetEnvironmentProvider = TargetEnvironmentProvider()
  private var serverBindingPort: TargetValue<Int>? = null

  override fun getJavaParameters(): SimpleJavaParameters {
    return javaParameters
  }

  override fun getEnvironmentConfiguration(): TargetEnvironmentConfiguration {
    return environmentConfiguration
  }

  override fun getTargetEnvironment(): TargetEnvironment {
    return targetEnvironment
  }

  override fun getTargetIntermediateResultHandler(): TargetIntermediateResultHandler {
    return targetIntermediateResultHandler
  }

  override fun getTargetBuildParameters(): TargetBuildParameters {
    return targetBuildParameters
  }

  override fun getProjectUploadRoot(): TargetEnvironment.UploadRoot {
    return projectUploadRoot
  }

  override fun getServerBindingPort(): TargetValue<Int>? {
    return serverBindingPort
  }

  override fun prepareEnvironment(
    targetBuildParametersBuilder: TargetBuildParameters.Builder<*>,
    consumerOperationParameters: ConsumerOperationParameters,
    progressIndicator: GradleServerProgressIndicator,
  ): TargetedCommandLine {
    progressIndicator.checkCanceled()
    val initScripts = collectInitScripts(consumerOperationParameters)

    val mainInitScript = initScripts.find { File(it).name.startsWith(MAIN_INIT_SCRIPT_NAME) }
    if (mainInitScript != null) {
      /**
       * All necessary jars must be explicitly provided in the main init script as a result of the
       * GradleProjectResolverExtension.getToolingExtensionsClasses().
       * By extracting these paths from the init script, we can build the classpath needed for execution.
       * These jars are needed on the tooling proxy side, because the tooling proxy converts the built models before sending
       * them to the IDEA side.
       */
      val projectResolverExtensionProvidedClassPath = extractPathsFromInitScript(mainInitScript)
      for (path in projectResolverExtensionProvidedClassPath) {
        javaParameters.classPath.add(path)
      }
    }

    val environmentConfigurationProvider = connection.environmentConfigurationProvider
    this.environmentConfiguration = environmentConfigurationProvider.environmentConfiguration

    val request = if (environmentConfiguration.typeId == "local") LocalTargetEnvironmentRequest()
    else environmentConfiguration.createEnvironmentRequest(project)

    environmentConfiguration.runtimes.findByType(GradleRuntimeTargetConfiguration::class.java)?.homePath?.nullize(true)?.also {
      targetBuildParametersBuilder.useInstallation(it)
    }

    configureJdk(javaParameters, request, consumerOperationParameters, environmentConfiguration)
    progressIndicator.checkCanceled()
    val targetArguments = prepareTargetEnvironmentRequest(
      request,
      consumerOperationParameters,
      environmentConfiguration,
      progressIndicator
    )

    progressIndicator.checkCanceled()
    val targetedCommandLineBuilder = javaParameters.toCommandLine(request)

    (environmentConfigurationProvider as? GradleServerConfigurationProvider)?.getServerBindingAddress(environmentConfiguration)?.also {
      targetedCommandLineBuilder.addEnvironmentVariable("serverBindingHost", it.host.nullize())
      targetedCommandLineBuilder.addEnvironmentVariable("serverBindingPort", it.port.toString())
      if (it.port != 0) {
        serverBindingPort = targetEnvironmentProvider.requestPort(request, it.port)
      }
    }

    projectUploadRoot = setupTargetProjectDirectories(consumerOperationParameters.projectDir, request, targetedCommandLineBuilder)
    val remoteEnvironment = request.prepareEnvironment(progressIndicator)
    targetEnvironment = remoteEnvironment
    EP.forEachExtensionSafe {
      it.handleCreatedTargetEnvironment(remoteEnvironment, this)
      progressIndicator.checkCanceled()
    }
    if (prepareTaskState) {
      val taskStateInitScript = connection.parameters.taskState?.handleCreatedTargetEnvironment(remoteEnvironment, progressIndicator)
      if (taskStateInitScript != null) {
        targetBuildParametersBuilder.withInitScript("ijtgttaskstate", taskStateInitScript)
      }
      connection.taskListener?.onEnvironmentPrepared(connection.taskId!!)
    }

    progressIndicator.checkCanceled()
    targetEnvironmentProvider.supplyEnvironmentAndRunHandlers(remoteEnvironment, progressIndicator)
    targetEnvironmentProvider.uploadVolumes(progressIndicator)

    val pathsToMap = extractPathsToMapFromInitScripts(initScripts)
    val targetPathMapper = environmentConfigurationProvider.pathMapper
    val pathMapperInitScript = createTargetPathMapperInitScript(
      request,
      targetPathMapper,
      environmentConfiguration,
      pathsToMap
    )
    targetBuildParametersBuilder.withInitScript("ijtgtmapper", pathMapperInitScript)

    targetBuildParametersBuilder.withBuildArguments(targetArguments)
    targetBuildParametersBuilder.withJvmArguments(consumerOperationParameters.jvmArguments ?: emptyList())
    targetBuildParametersBuilder.withEnvironmentVariables(consumerOperationParameters.environmentVariables ?: emptyMap())
    (targetBuildParametersBuilder as? TargetBuildParameters.TaskAwareBuilder<*>)
      ?.withTasks(consumerOperationParameters.tasks ?: emptyList())

    targetIntermediateResultHandler = targetBuildParametersBuilder.buildIntermediateResultHandler()
    targetBuildParameters = targetBuildParametersBuilder.build()

    return targetedCommandLineBuilder.build()
  }

  private fun setupTargetProjectDirectories(
    workingDir: File,
    request: TargetEnvironmentRequest,
    targetedCommandLineBuilder: @NotNull TargetedCommandLineBuilder,
  ): TargetEnvironment.UploadRoot {
    val pathsToUpload: MutableSet<String> = HashSet()

    val gradleProjectDirectory = FileUtilRt.toSystemDependentName(workingDir.path)
    pathsToUpload.add(gradleProjectDirectory)

    val projectModules = getProjectModules(workingDir)
    pathsToUpload.addAll(projectModules)

    val commonAncestor = findCommonAncestor(pathsToUpload)
    val uploadPath = Paths.get(FileUtilRt.toSystemDependentName(commonAncestor!!))
    val uploadRoot = TargetEnvironment.UploadRoot(uploadPath, TargetEnvironment.TargetPath.Temporary())
    request.uploadVolumes += uploadRoot
    val targetFileSeparator = request.targetPlatform.platform.fileSeparator

    var targetWorkingDirectory: TargetValue<String>? = null
    for (path in pathsToUpload) {
      val relativePath = FileUtilRt.getRelativePath(commonAncestor, path, File.separatorChar)
      val targetValue = targetEnvironmentProvider.upload(uploadRoot, path, relativePath!!)
      if (targetWorkingDirectory == null && FileUtil.isAncestor(path, gradleProjectDirectory, false)) {
        val workingDirRelativePath = FileUtilRt.getRelativePath(path, gradleProjectDirectory, File.separatorChar)!!
        val targetWorkingDirRelativePath = if (workingDirRelativePath == ".") {
          ""
        }
        else {
          FileUtilRt.toSystemDependentName(workingDirRelativePath, targetFileSeparator)
        }
        targetWorkingDirectory = TargetValue.map(targetValue) { "$it$targetFileSeparator$targetWorkingDirRelativePath" }
      }
    }
    targetedCommandLineBuilder.setWorkingDirectory(targetWorkingDirectory!!)
    return uploadRoot
  }

  private fun getProjectModules(workingDir: File): Set<String> {
    val externalProjectPath = ExternalSystemApiUtil.toCanonicalPath(workingDir.path)
    val projectSettings = GradleSettings.getInstance(project).getLinkedProjectSettings(externalProjectPath)
    return projectSettings?.modules
             ?.filter { Files.exists(Path.of(it)) }
             ?.map { FileUtilRt.toSystemDependentName(it) }
             ?.toSet()
           ?: emptySet()
  }

  private fun findCommonAncestor(paths: Set<String>): String? {
    var commonRoot: File? = null
    for (path in paths) {
      commonRoot = if (commonRoot == null) File(path) else FileUtil.findAncestor(commonRoot, File(path))
      requireNotNull(commonRoot) { "no common root found" }
    }
    assert(commonRoot != null)
    return commonRoot!!.path
  }

  private fun createTargetPathMapperInitScript(
    request: TargetEnvironmentRequest,
    targetPathMapper: PathMapper?,
    environmentConfiguration: TargetEnvironmentConfiguration,
    pathsToMap: List<String>,
  ): String {
    val pathMappingSettings = PathMappingSettings(targetEnvironmentProvider.pathMappingSettings.pathMappings)
    for ((uploadRoot, uploadableVolume) in targetEnvironment.uploadVolumes) {
      val localRootPath = uploadRoot.localRootPath
      val relativePath = if (localRootPath.isDirectory()) "." else localRootPath.fileName.toString()
      pathMappingSettings.addMapping(localRootPath.toString(), uploadableVolume.resolveTargetPath(relativePath))
    }
    val mapperInitScript = StringBuilder("ext.pathMapper = [:]\n")
    for (localPath in pathsToMap) {
      if (targetPathMapper != null && targetPathMapper.canReplaceLocal(localPath)) {
        val targetPath = targetPathMapper.convertToRemote(localPath)
        mapperInitScript.append("ext.pathMapper.put(${localPath.toGroovyStringLiteral()}, ${targetPath.toGroovyStringLiteral()})\n")
      }
      else if (pathMappingSettings.canReplaceLocal(localPath)) {
        val targetPath = pathMappingSettings.convertToRemote(localPath)
        mapperInitScript.append("ext.pathMapper.put(${localPath.toGroovyStringLiteral()}, ${targetPath.toGroovyStringLiteral()})\n")
      }
    }

    // add target java executable mapping
    val platform = request.targetPlatform.platform
    val java = if (platform == Platform.WINDOWS) "java.exe" else "java"
    val javaRuntime = environmentConfiguration.runtimes.findByType(JavaLanguageRuntimeConfiguration::class.java)
    if (javaRuntime != null) {
      val targetJavaExecutablePath = arrayOf(javaRuntime.homePath, "bin", java).joinToString(platform.fileSeparator.toString())
      mapperInitScript.append(
        "ext.pathMapper.put(\"${targetJavaExecutablePathMappingKey}\", ${targetJavaExecutablePath.toGroovyStringLiteral()})\n")
    }
    else {
      mapperInitScript.append("ext.pathMapper.put(\"${targetJavaExecutablePathMappingKey}\", \"${java}\")\n")
    }
    mapperInitScript.append("ext.mapPath = { path -> pathMapper.get(path) ?: path }")
    return mapperInitScript.toString()
  }

  private fun configureJdk(
    javaParameters: SimpleJavaParameters,
    request: TargetEnvironmentRequest,
    consumerOperationParameters: ConsumerOperationParameters,
    environmentConfiguration: TargetEnvironmentConfiguration,
  ) {
    if (request is LocalTargetEnvironmentRequest) {
      javaParameters.vmParametersList.addProperty(Main.LOCAL_BUILD_PROPERTY, "true")
      val javaHomePath = consumerOperationParameters.javaHome.path
      ProjectJdkTable.getInstance().allJdks.find { FileUtilRt.pathsEqual(it.homePath, javaHomePath) }?.let { javaParameters.jdk = it }
    }
    else {
      if (environmentConfiguration.runtimes.findByType(JavaLanguageRuntimeConfiguration::class.java) == null) {
        val targetJavaHomePath = FileUtil.toCanonicalPath(consumerOperationParameters.javaHome.path)
        val javaLanguageRuntimeConfiguration = JavaLanguageRuntimeConfiguration().apply { homePath = targetJavaHomePath }
        environmentConfiguration.addLanguageRuntime(javaLanguageRuntimeConfiguration)
      }
    }
  }

  private fun prepareTargetEnvironmentRequest(request: TargetEnvironmentRequest,
                                              consumerOperationParameters: ConsumerOperationParameters,
                                              environmentConfiguration: TargetEnvironmentConfiguration,
                                              progressIndicator: GradleServerProgressIndicator): List<Pair<String, TargetValue<String>?>> {
    val targetArguments = requestFileArgumentsUpload(request, consumerOperationParameters, environmentConfiguration)
    EP.forEachExtensionSafe {
      it.prepareTargetEnvironmentRequest(request, this)
      progressIndicator.checkCanceled()
    }
    if (prepareTaskState) {
      connection.parameters.taskState?.prepareTargetEnvironmentRequest(request, progressIndicator)
    }
    return targetArguments
  }

  private fun requestFileArgumentsUpload(
    request: TargetEnvironmentRequest,
    parameters: ConsumerOperationParameters,
    configuration: TargetEnvironmentConfiguration
  ): List<Pair<String, TargetValue<String>?>> {
    val targetBuildArguments = ArrayList<Pair<String, TargetValue<String>?>>()
    val iterator = parameters.arguments?.iterator() ?: return targetBuildArguments
    while (iterator.hasNext()) {
      val arg = iterator.next()
      if (arg == INIT_SCRIPT_CMD_OPTION && iterator.hasNext()) {
        val path = iterator.next()
        val targetInitScriptPath = targetEnvironmentProvider.requestUploadIntoTarget(path, request, configuration)
        targetBuildArguments.add(arg to targetInitScriptPath)
      }
      else {
        targetBuildArguments.add(arg to null)
      }
    }
    return targetBuildArguments
  }

  private fun TargetBuildParameters.Builder<*>.withBuildArguments(arguments: List<Pair<String, TargetValue<String>?>>) {
    val resolvedBuildArguments = ArrayList<String>()
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
  }

  companion object {
    private val log = logger<GradleServerEnvironmentSetupImpl>()
    private val EP = ExtensionPointName.create<GradleTargetEnvironmentAware>("org.jetbrains.plugins.gradle.targetEnvironmentAware")
  }

  private object Helper {
    fun getToolingProxyDefaultJavaParameters(): SimpleJavaParameters {
      val javaParameters = SimpleJavaParameters()
      val toolingExtensionJarPaths = getToolingExtensionsJarPaths(GRADLE_TOOLING_EXTENSION_PROXY_CLASSES)
      for (path in toolingExtensionJarPaths) {
        javaParameters.classPath.add(path)
      }
      javaParameters.vmParametersList.add("-Djava.net.preferIPv4Stack=true")
      javaParameters.mainClass = Main::class.java.name
      if (log.isDebugEnabled) {
        javaParameters.programParametersList.add("--debug")
      }
      return javaParameters
    }

    fun extractPathsToMapFromInitScripts(initScriptPaths: List<String>): List<String> {
      val paths = mutableListOf<String>()
      for (initScriptPath in initScriptPaths) {
        val pathsToMap = extractPathsFromInitScript(initScriptPath)
        paths.addAll(pathsToMap)
      }
      return paths
    }

    fun collectInitScripts(parameters: ConsumerOperationParameters): List<String> {
      val initScriptPaths = ArrayList<String>()
      val iterator = parameters.arguments?.iterator() ?: return emptyList()
      while (iterator.hasNext()) {
        val arg = iterator.next()
        if (arg == INIT_SCRIPT_CMD_OPTION && iterator.hasNext()) {
          val path = iterator.next()
          initScriptPaths.add(path)
        }
      }
      return initScriptPaths
    }

    fun extractPathsFromInitScript(initScriptPath: String): List<String> {
      val initScriptFile = File(initScriptPath)
      if (initScriptFile.extension == GradleConstants.EXTENSION) {
        val fileContent = initScriptFile.readText()
        val regex = "mapPath\\(['|\"](.{2,}?)['|\"][)]".toRegex()
        val matches = regex.findAll(fileContent)
        return matches.map { it.groupValues[1] }.toList()
      }
      return emptyList()
    }
  }
}

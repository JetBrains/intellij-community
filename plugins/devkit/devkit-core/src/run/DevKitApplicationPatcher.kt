// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.run

import com.intellij.execution.JavaRunConfigurationBase
import com.intellij.execution.RunConfigurationExtension
import com.intellij.execution.application.ApplicationConfiguration
import com.intellij.execution.configurations.DebuggingRunnerData
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.ParametersList
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.scratch.JavaScratchConfiguration
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.impl.ModuleManagerEx
import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.CompilerModuleExtension
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.ijent.community.buildConstants.IJENT_BOOT_CLASSPATH_MODULE
import com.intellij.platform.ijent.community.buildConstants.IJENT_REQUIRED_DEFAULT_NIO_FS_PROVIDER_CLASS
import com.intellij.platform.ijent.community.buildConstants.IJENT_WSL_FILE_SYSTEM_REGISTRY_KEY
import com.intellij.platform.ijent.community.buildConstants.MULTI_ROUTING_FILE_SYSTEM_VMOPTIONS
import com.intellij.util.PlatformUtils
import com.intellij.util.lang.UrlClassLoader
import org.jetbrains.idea.devkit.requestHandlers.passDataAboutBuiltInServer
import java.lang.ClassLoader.getSystemClassLoader
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.invariantSeparatorsPathString

private val LOG = logger<DevKitApplicationPatcher>()

internal class DevKitApplicationPatcher : RunConfigurationExtension() {
  override fun <T : RunConfigurationBase<*>> updateJavaParameters(
    configuration: T,
    javaParameters: JavaParameters,
    runnerSettings: RunnerSettings?,
  ) {
    val project = configuration.project
    if (!IntelliJProjectUtil.isIntelliJPlatformProject(project)) {
      return
    }
    if (configuration !is JavaRunConfigurationBase) {
      return
    }
    if (configuration is JavaScratchConfiguration) {
      return
    }

    val mainClass = configuration.runClass ?: return

    passDataAboutBuiltInServer(javaParameters, project)
    val vmParameters = javaParameters.vmParametersList
    val module = configuration.configurationModule.module ?: return
    val jdk = JavaParameters.getJdkToRunModule(module, true) ?: return
    if (!vmParameters.getPropertyValue("intellij.devkit.skip.automatic.add.opens").toBoolean()) {
      JUnitDevKitPatcher.appendAddOpensWhenNeeded(project, jdk, vmParameters)
    }

    val isDevBuild = mainClass == "org.jetbrains.intellij.build.devServer.DevMainKt"
    val vmParametersAsList = vmParameters.list
    if (vmParametersAsList.contains("--add-modules") || !isDevBuild && mainClass != "com.intellij.idea.Main") {
      return
    }

    if (!vmParameters.hasProperty(JUnitDevKitPatcher.SYSTEM_CL_PROPERTY)) {
      val qualifiedName = "com.intellij.util.lang.PathClassLoader"
      if (JUnitDevKitPatcher.loaderValid(project, module, qualifiedName)) {
        vmParameters.addProperty(JUnitDevKitPatcher.SYSTEM_CL_PROPERTY, qualifiedName)
        vmParameters.addProperty(UrlClassLoader.CLASSPATH_INDEX_PROPERTY_NAME, "true")
      }
    }

    if (!vmParametersAsList.any { it.contains("CICompilerCount") || it.contains("TieredCompilation") }) {
      vmParameters.addAll("-XX:CICompilerCount=2")
      vmParameters.addAll("-XX:+UnlockDiagnosticVMOptions")
      vmParameters.addAll("-XX:TieredOldPercentage=100000")
    }

    vmParameters.addAll(
      "-XX:MaxJavaStackTraceDepth=10000",
      "-ea",
    )

    if (runnerSettings is DebuggingRunnerData) {
      vmParameters.defineProperty("kotlinx.coroutines.debug.enable.creation.stack.trace", "true")
    }

    if (vmParametersAsList.none { it.startsWith("-Xmx") }) {
      vmParameters.add("-Xmx2g")
    }
    if (vmParametersAsList.none { it.startsWith("-XX:JbrShrinkingGcMaxHeapFreeRatio=") }) {
      vmParameters.add("-XX:JbrShrinkingGcMaxHeapFreeRatio=40")
    }
    vmParameters.add("-XX:SoftRefLRUPolicyMSPerMB=50")
    if (vmParametersAsList.none { it.startsWith("-XX:ReservedCodeCacheSize") }) {
      vmParameters.add("-XX:ReservedCodeCacheSize=512m")
    }
    if (vmParametersAsList.none { it.startsWith("-Djava.util.zip.use.nio.for.zip.file.access") }) {
      vmParameters.add("-Djava.util.zip.use.nio.for.zip.file.access=true") // IJPL-149160
    }
    if (vmParametersAsList.none { it.startsWith("-Djdk.nio.maxCachedBufferSize") }) {
      vmParameters.add("-Djdk.nio.maxCachedBufferSize=2097152") // IJPL-164109
    }

    enableIjentDefaultFsProvider(project, vmParameters)

    if (isDevBuild) {
      updateParametersForDevBuild(javaParameters, configuration)
    }
  }

  override fun isApplicableFor(configuration: RunConfigurationBase<*>): Boolean {
    return configuration is ApplicationConfiguration ||
           configuration.factory?.id == "JetRunConfigurationType" // to avoid a dependency on the Kotlin plugin
  }
}

private fun updateParametersForDevBuild(javaParameters: JavaParameters, configuration: JavaRunConfigurationBase) {
  val vmParameters = javaParameters.vmParametersList

  var productClassifier = vmParameters.getPropertyValue("idea.platform.prefix")
  productClassifier = when (productClassifier) {
    null -> "idea"
    PlatformUtils.IDEA_CE_PREFIX -> "idea-community"
    else -> productClassifier
  }

  val workingDirectory = Path.of(configuration.workingDirectory!!)
  if (!vmParameters.hasProperty("idea.config.path")) {
    val configDirPath = workingDirectory.asEelPath().toString()
    val dir = FileUtilRt.toSystemIndependentName("$configDirPath/out/dev-data/${productClassifier.lowercase()}")
    vmParameters.addProperty("idea.config.path", "$dir/config")
    vmParameters.addProperty("idea.system.path", "$dir/system")
  }

  val runDir = workingDirectory.resolve("out/dev-run/$productClassifier")
  if (vmParameters.getPropertyValue("idea.dev.skip.build").toBoolean()) {
    // todo broken for now, if this mode will be needed, proper binary maybe implemented
    vmParameters.addProperty(PathManager.PROPERTY_HOME_PATH, runDir.invariantSeparatorsPathString)
    val files = try {
      Files.readAllLines(runDir.resolve("core-classpath.txt"))
    }
    catch (_: NoSuchFileException) {
      null
    }

    if (files != null) {
      javaParameters.classPath.clear()
      javaParameters.classPath.addAll(files)
      javaParameters.mainClass = "com.intellij.idea.Main"
    }
  }

  vmParameters.addProperty("idea.vendor.name", "JetBrains")
  vmParameters.addProperty("idea.use.dev.build.server", "true")
  setPropertyIfAbsent(vmParameters, "idea.dev.build.unpacked")

  vmParameters.addProperty("sun.io.useCanonCaches", "false")
  vmParameters.addProperty("sun.awt.disablegrab", "true")
  vmParameters.addProperty("sun.java2d.metal", "true")
  vmParameters.addProperty("idea.debug.mode", "true")
  vmParameters.addProperty("idea.is.internal", "true")
  vmParameters.addProperty("fus.internal.test.mode", "true")
  vmParameters.addProperty("jdk.attach.allowAttachSelf")
}

private fun setPropertyIfAbsent(vmParameters: ParametersList, @Suppress("SameParameterValue") name: String) {
  if (!vmParameters.hasProperty(name)) {
    vmParameters.addProperty(name, "true")
  }
}

/**
 * A direct call of [com.intellij.platform.ijent.community.buildConstants.isMultiRoutingFileSystemEnabledForProduct] invokes
 * the function which is bundled with the DevKit plugin.
 * In contrast, the result of this function corresponds to what is written in the source code at the current revision.
 */
@Suppress("FunctionName")
private fun isIjentWslFsEnabledByDefaultForProduct_Reflective(project: Project, platformPrefix: String?): Boolean {
  try {
    val constantsClass = getIjentBuildScriptsConstantsClass_Reflective(project) ?: return false
    val method = try {
      constantsClass.getDeclaredMethod("isMultiRoutingFileSystemEnabledForProduct", String::class.java)
    }
    catch (_: NoSuchMethodException) {
      @Suppress("SpellCheckingInspection")
      constantsClass.getDeclaredMethod("isIjentWslFsEnabledByDefaultForProduct", String::class.java)
    }
    return method.invoke(null, platformPrefix) as Boolean
  }
  catch (e: Throwable) {
    when (e) {
      is ClassNotFoundException, is NoSuchMethodException, is IllegalAccessException, is java.lang.reflect.InvocationTargetException -> {
        logger<DevKitApplicationPatcher>().warn(
          "Failed to reflectively load IjentWslFsEnabledByDefaultForProduct from built classes." +
          " Maybe the file didn't exist in this revision, so the ijent WSL FS was disabled.",
          e,
        )
        return false
      }
      else -> throw e
    }
  }
}

/**
 * A direct call of [com.intellij.platform.ijent.community.buildConstants.MULTI_ROUTING_FILE_SYSTEM_VMOPTIONS] gets
 * values which is bundled with the DevKit plugin.
 * In contrast, the result of this function corresponds to what is written in the source code at the current revision.
 */
@Suppress("FunctionName")
private fun getMultiRoutingFileSystemVmOptions_Reflective(project: Project): List<String> {
  try {
    val constantsClass = getIjentBuildScriptsConstantsClass_Reflective(project) ?: return MULTI_ROUTING_FILE_SYSTEM_VMOPTIONS
    val field = constantsClass.getDeclaredField("MULTI_ROUTING_FILE_SYSTEM_VMOPTIONS")
    field.trySetAccessible()
    @Suppress("UNCHECKED_CAST")
    return field.get(constantsClass) as List<String>
  }
  catch (err: Throwable) {
    when (err) {
      is ClassNotFoundException, is NoSuchMethodException, is IllegalAccessException, is java.lang.reflect.InvocationTargetException -> {
        logger<DevKitApplicationPatcher>().warn(
          "Failed to reflectively load MULTI_ROUTING_FILE_SYSTEM_VMOPTIONS from built classes." +
          " Options from DevKit plugin loaded class will be used.",
          err,
        )
        return MULTI_ROUTING_FILE_SYSTEM_VMOPTIONS
      }
      else -> throw err
    }
  }
}

@Suppress("FunctionName")
private fun getIjentBuildScriptsConstantsClass_Reflective(project: Project): Class<*>? {

  val buildConstantsClassPath = getOutputByModule(
    project = project,
    moduleName = "intellij.platform.ijent.community.buildConstants",
    requestMoniker = "ijent build scripts constants class loading",
  ) ?: return null

  val kotlinStdlibClassPath = run {
    val systemClassLoader = getSystemClassLoader()
    val kotlinCollectionsClassUri = systemClassLoader.getResource("kotlin/collections/CollectionsKt.class")!!.toURI()

    if (kotlinCollectionsClassUri.scheme != "jar") {
      logger<DevKitApplicationPatcher>().warn("Kotlin stdlib is not in a JAR: $kotlinCollectionsClassUri")
      return null
    }
    val osPath = kotlinCollectionsClassUri.schemeSpecificPart
      .substringBefore(".jar!")
      .plus(".jar")
      .removePrefix(if (SystemInfo.isWindows) "file:/" else "file:")

    Path.of(osPath).toUri().toURL()
  }

  val tmpClassLoader = URLClassLoader(arrayOf(buildConstantsClassPath.toUri().toURL(), kotlinStdlibClassPath), null)
  return tmpClassLoader.loadClass("com.intellij.platform.ijent.community.buildConstants.IjentBuildScriptsConstantsKt")
}

internal fun enableIjentDefaultFsProvider(
  project: Project,
  vmParameters: ParametersList,
) {
  // Enable the ijent file system only when the new default FS provider class is available.
  // It is required to let actual DevKit plugins work with branches without the FS provider class, like 241.
  if (JUnitDevKitPatcher.loaderValid(project, null, IJENT_REQUIRED_DEFAULT_NIO_FS_PROVIDER_CLASS)) {
    val isIjentWslFsEnabled = isIjentWslFsEnabledByDefaultForProduct_Reflective(
      project = project,
      platformPrefix = vmParameters.getPropertyValue("idea.platform.prefix"),
    )
    vmParameters.add("-D${IJENT_WSL_FILE_SYSTEM_REGISTRY_KEY}=$isIjentWslFsEnabled")
    vmParameters.addAll(getMultiRoutingFileSystemVmOptions_Reflective(project))

    val outputRoot = getOutputByModule(project, IJENT_BOOT_CLASSPATH_MODULE, "ijent boot classpath addition")
    if (outputRoot != null) {
      vmParameters.add("-Xbootclasspath/a:$outputRoot")
    }
  }
}

private fun getOutputByModule(project: Project, moduleName: String, requestMoniker: String): Path? {
  val module = ModuleManagerEx.getInstanceEx(project).findModuleByName(moduleName)
  if (module == null) {
    LOG.warn("Module $moduleName not found in project ${project.basePath}, skipping $requestMoniker")
    return null
  }

  val compilerModuleExtension = CompilerModuleExtension.getInstance(module)
  if (compilerModuleExtension == null) {
    LOG.warn("CompilerModuleExtension not found for module ${module.name}, skipping $requestMoniker")
    return null
  }

  // For JPS compilation returns a directory
  // For Bazel delegation returns a jar
  val roots = compilerModuleExtension.getOutputRoots(false)
  if (roots.isEmpty()) {
    LOG.warn("No output roots found for module ${module.name}, skipping $requestMoniker")
    return null
  }

  if (roots.size > 1) {
    LOG.warn("Multiple output roots found for module ${module.name}, skipping $requestMoniker: ${roots.map { it.path }.sorted()}")
    return null
  }

  val outputRoot = roots.single().toNioPath()
  if (!outputRoot.exists()) {
    LOG.warn("Output root for module ${module.name} does not exist: $outputRoot, skipping $requestMoniker")
    return null
  }

  LOG.info("Using output $outputRoot for $requestMoniker")

  return outputRoot
}
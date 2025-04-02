// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.run

import com.intellij.compiler.options.MakeProjectStepBeforeRun
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
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.ijent.community.buildConstants.IJENT_BOOT_CLASSPATH_MODULE
import com.intellij.platform.ijent.community.buildConstants.IJENT_REQUIRED_DEFAULT_NIO_FS_PROVIDER_CLASS
import com.intellij.platform.ijent.community.buildConstants.IJENT_WSL_FILE_SYSTEM_REGISTRY_KEY
import com.intellij.platform.ijent.community.buildConstants.MULTI_ROUTING_FILE_SYSTEM_VMOPTIONS
import com.intellij.util.PlatformUtils
import com.intellij.util.lang.UrlClassLoader
import com.intellij.util.system.CpuArch
import org.jetbrains.ide.BuiltInServerManager
import org.jetbrains.idea.devkit.requestHandlers.CompileHttpRequestHandlerToken
import org.jetbrains.idea.devkit.requestHandlers.passDataAboutBuiltInServer
import java.lang.ClassLoader.getSystemClassLoader
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

@Suppress("SpellCheckingInspection")
private class DevKitApplicationPatcher : RunConfigurationExtension() {
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
    if (vmParametersAsList.contains("--add-modules") || (!isDevBuild && mainClass != "com.intellij.idea.Main")) {
      return
    }

    if (!vmParameters.hasProperty(JUnitDevKitPatcher.SYSTEM_CL_PROPERTY)) {
      val qualifiedName = "com.intellij.util.lang.PathClassLoader"
      if (JUnitDevKitPatcher.loaderValid(project, module, qualifiedName)) {
        vmParameters.addProperty(JUnitDevKitPatcher.SYSTEM_CL_PROPERTY, qualifiedName)
        vmParameters.addProperty(UrlClassLoader.CLASSPATH_INDEX_PROPERTY_NAME, "true")
      }
    }

    val is17 = javaParameters.jdk?.versionString?.contains("17") == true
    if (!vmParametersAsList.any { it.contains("CICompilerCount") || it.contains("TieredCompilation") }) {
      vmParameters.addAll("-XX:CICompilerCount=2")
      if (!is17) {
        //vmParameters.addAll("-XX:-TieredCompilation")
        //vmParameters.addAll("-XX:+SegmentedCodeCache")
        vmParameters.addAll("-XX:+UnlockDiagnosticVMOptions")
        vmParameters.addAll("-XX:TieredOldPercentage=100000")
      }
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
    if (is17 && vmParametersAsList.none { it.startsWith("-XX:SoftRefLRUPolicyMSPerMB") }) {
      vmParameters.add("-XX:SoftRefLRUPolicyMSPerMB=50")
    }
    if (vmParametersAsList.none { it.startsWith("-XX:ReservedCodeCacheSize") }) {
      vmParameters.add("-XX:ReservedCodeCacheSize=512m")
    }
    if (vmParametersAsList.none { it.startsWith("-Djava.util.zip.use.nio.for.zip.file.access") }) {
      vmParameters.add("-Djava.util.zip.use.nio.for.zip.file.access=true") // IJPL-149160
    }
    if (vmParametersAsList.none { it.startsWith("-Djdk.nio.maxCachedBufferSize") }) {
      vmParameters.add("-Djdk.nio.maxCachedBufferSize=2097152") // IJPL-164109
    }

    enableIjentDefaultFsProvider(project, configuration.workingDirectory, vmParameters)

    if (isDevBuild) {
      updateParametersForDevBuild(javaParameters, configuration, project)
    }
  }

  private fun updateParametersForDevBuild(javaParameters: JavaParameters, configuration: JavaRunConfigurationBase, project: Project) {
    val vmParameters = javaParameters.vmParametersList
    if (configuration.beforeRunTasks.none { it.providerId === MakeProjectStepBeforeRun.ID }) {
      vmParameters.addProperty("compile.server.port", BuiltInServerManager.getInstance().port.toString())
      vmParameters.addProperty("compile.server.project", project.locationHash)
      vmParameters.addProperty("compile.server.token", service<CompileHttpRequestHandlerToken>().acquireToken())
    }

    var productClassifier = vmParameters.getPropertyValue("idea.platform.prefix")
    productClassifier = when (productClassifier) {
      null -> "idea"
      PlatformUtils.IDEA_CE_PREFIX -> "idea-community"
      else -> productClassifier
    }

    if (!vmParameters.hasProperty("idea.config.path")) {
      val path = Path.of(configuration.workingDirectory!!)
      val configDirPath = path.asEelPath().toString()
      val dir = FileUtilRt.toSystemIndependentName("$configDirPath/out/dev-data/${productClassifier.lowercase()}")
      vmParameters.addProperty("idea.config.path", "$dir/config")
      vmParameters.addProperty("idea.system.path", "$dir/system")
    }

    val runDir = Path.of("${configuration.workingDirectory}/out/dev-run/${productClassifier}/${productClassifier}")
    for ((name, value) in getIdeSystemProperties(runDir)) {
      vmParameters.addProperty(name, value)
    }

    if (vmParameters.getPropertyValue("idea.dev.skip.build").toBoolean()) {
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
    if (!vmParameters.hasParameter("-Didea.initially.ask.config=never")) {
      vmParameters.addProperty("idea.initially.ask.config", "true")
    }
  }

  override fun isApplicableFor(configuration: RunConfigurationBase<*>): Boolean {
    return configuration is ApplicationConfiguration
           //use this instead of 'is KotlinRunConfiguration' to avoid having dependency on Kotlin plugin here
           || configuration.factory?.id == "JetRunConfigurationType"
  }
}

private fun setPropertyIfAbsent(vmParameters: ParametersList, @Suppress("SameParameterValue") name: String) {
  if (!vmParameters.hasProperty(name)) {
    vmParameters.addProperty(name, "true")
  }
}

@Suppress("SpellCheckingInspection")
private fun getIdeSystemProperties(runDir: Path): Map<String, String> {
  // see BuildContextImpl.getAdditionalJvmArguments - we should somehow deduplicate code
  val libDir = runDir.resolve("lib")
  return mapOf(
    "jna.boot.library.path" to "$libDir/jna/${if (CpuArch.isArm64()) "aarch64" else "amd64"}",
    "pty4j.preferred.native.folder" to "$libDir/pty4j",
    // require bundled JNA dispatcher lib
    "jna.nosys" to "true",
    "jna.noclasspath" to "true",
    "compose.swing.render.on.graphics" to "true",
  )
}

/**
 * A direct call of [com.intellij.platform.ijent.community.buildConstants.isMultiRoutingFileSystemEnabledForProduct] invokes
 * the function which is bundled with the DevKit plugin.
 * In contrast, the result of this function corresponds to what is written in the source code at current revision.
 */
@Suppress("FunctionName")
private fun isIjentWslFsEnabledByDefaultForProduct_Reflective(workingDirectory: String?, platformPrefix: String?): Boolean {
  if (workingDirectory == null) return false
  try {
    val constantsClass = getIjentBuildScriptsConstantsClass_Reflective(workingDirectory) ?: return false
    val method =
      try {
        constantsClass.getDeclaredMethod("isMultiRoutingFileSystemEnabledForProduct", String::class.java)
      } catch (_: NoSuchMethodException) {
        constantsClass.getDeclaredMethod("isIjentWslFsEnabledByDefaultForProduct", String::class.java)
      }
    return method.invoke(null, platformPrefix) as Boolean
  }
  catch (err: Throwable) {
    when (err) {
      is ClassNotFoundException, is NoSuchMethodException, is IllegalAccessException, is java.lang.reflect.InvocationTargetException -> {
        logger<DevKitApplicationPatcher>().warn(
          "Failed to reflectively load IJentWslFsEnabledByDefaultForProduct from built classes." +
          " Maybe the file didn't exist in this revision, so the IJent WSL FS was disabled.",
          err,
        )
        return false
      }
      else -> throw err
    }
  }
}

/**
 * A direct call of [com.intellij.platform.ijent.community.buildConstants.MULTI_ROUTING_FILE_SYSTEM_VMOPTIONS] gets
 * values which is bundled with the DevKit plugin.
 * In contrast, the result of this function corresponds to what is written in the source code at current revision.
 */
@Suppress("FunctionName")
private fun getMultiRoutingFileSystemVmOptions_Reflective(workingDirectory: String?): List<String> {
  if (workingDirectory == null) return MULTI_ROUTING_FILE_SYSTEM_VMOPTIONS
  try {
    val constantsClass = getIjentBuildScriptsConstantsClass_Reflective(workingDirectory) ?: return MULTI_ROUTING_FILE_SYSTEM_VMOPTIONS
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
private fun getIjentBuildScriptsConstantsClass_Reflective(workingDirectory: String): Class<*>? {
  val buildConstantsClassPath = Path.of(
    workingDirectory,
    "out/classes/production/intellij.platform.ijent.community.buildConstants",
  ).toUri().toURL()

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

  val tmpClassLoader = URLClassLoader(arrayOf(buildConstantsClassPath, kotlinStdlibClassPath), null)
  return tmpClassLoader.loadClass("com.intellij.platform.ijent.community.buildConstants.IjentBuildScriptsConstantsKt")
}

internal fun enableIjentDefaultFsProvider(
  project: Project,
  workingDirectory: String?,
  vmParameters: ParametersList,
) {
  // Enable the IJent file system only when the new default FS provider class is available.
  // It is required to let actual DevKit plugins work with branches without the FS provider class, like 241.
  if (JUnitDevKitPatcher.loaderValid(project, null, IJENT_REQUIRED_DEFAULT_NIO_FS_PROVIDER_CLASS)) {
    val isIjentWslFsEnabled = isIjentWslFsEnabledByDefaultForProduct_Reflective(
      workingDirectory,
      vmParameters.getPropertyValue("idea.platform.prefix"),
    )
    vmParameters.add("-D${IJENT_WSL_FILE_SYSTEM_REGISTRY_KEY}=$isIjentWslFsEnabled")
    vmParameters.addAll(getMultiRoutingFileSystemVmOptions_Reflective(workingDirectory))
    vmParameters.add("-Xbootclasspath/a:${workingDirectory}/out/classes/production/$IJENT_BOOT_CLASSPATH_MODULE")
  }
}

internal fun Module.hasIjentDefaultFsProviderInClassPath(): Boolean {
  val queue = ArrayDeque(listOf(*ModuleRootManager.getInstance(this).getModuleDependencies()))
  val seen = hashSetOf(this)
  while (true) {
    val module =
      queue.removeFirstOrNull()
      ?: return false
    if (module.name == IJENT_BOOT_CLASSPATH_MODULE) {
      return true
    }
    if (seen.add(module)) {
      queue.addAll(ModuleRootManager.getInstance(module).getModuleDependencies())
    }
  }
}
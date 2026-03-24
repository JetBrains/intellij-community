// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.util.PlatformUtils
import com.intellij.util.lang.UrlClassLoader
import org.jetbrains.idea.devkit.requestHandlers.passDataAboutBuiltInServer
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

internal class DevKitApplicationPatcher : RunConfigurationExtension() {
  override fun <T : RunConfigurationBase<*>> updateJavaParameters(
    configuration: T,
    javaParameters: JavaParameters,
    runnerSettings: RunnerSettings?,
  ) {
    val project = configuration.project
    if (
      configuration !is JavaRunConfigurationBase ||
      configuration is JavaScratchConfiguration ||
      !IntelliJProjectUtil.isIntelliJPlatformProject(project)
    ) return

    val mainClass = configuration.runClass ?: return

    passDataAboutBuiltInServer(javaParameters, project)
    val vmParameters = javaParameters.vmParametersList
    val module = configuration.configurationModule.module ?: return
    val jdk = JavaParameters.getJdkToRunModule(module, true) ?: return
    if (!vmParameters.getPropertyValue("intellij.devkit.skip.automatic.add.opens").toBoolean()) {
      DevKitPatcherHelper.appendAddOpensWhenNeeded(project, jdk, vmParameters)
    }

    val isDevBuild = mainClass == "org.jetbrains.intellij.build.devServer.DevMainKt"
    val vmParametersAsList = vmParameters.list
    if (vmParametersAsList.contains("--add-modules") || !isDevBuild && mainClass != "com.intellij.idea.Main") {
      return
    }

    if (!vmParameters.hasProperty(DevKitPatcherHelper.SYSTEM_CL_PROPERTY)) {
      val qualifiedName = "com.intellij.util.lang.PathClassLoader"
      if (DevKitPatcherHelper.loaderValid(project, module, qualifiedName)) {
        vmParameters.addProperty(DevKitPatcherHelper.SYSTEM_CL_PROPERTY, qualifiedName)
        vmParameters.addProperty(UrlClassLoader.CLASSPATH_INDEX_PROPERTY_NAME, "true")
      }
    }

    vmParameters.addAll(
      "-ea",
      "-XX:MaxJavaStackTraceDepth=10000",
      "-XX:+IgnoreUnrecognizedVMOptions",
      "-XX:+UnlockDiagnosticVMOptions",  // required for `-XX:TieredOldPercentage`
    )

    if (!vmParametersAsList.any { it.contains("CICompilerCount") || it.contains("TieredCompilation") }) {
      vmParameters.addAll("-XX:CICompilerCount=2", "-XX:TieredOldPercentage=100000")
    }

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

    DevKitPatcherHelper.enableIjentDefaultFsProvider(project, vmParameters)

    if (isDevBuild) {
      updateParametersForDevBuild(javaParameters, configuration)
    }
  }

  override fun isApplicableFor(configuration: RunConfigurationBase<*>): Boolean =
    configuration is ApplicationConfiguration ||
    configuration.factory?.id == "JetRunConfigurationType" // to avoid a dependency on the Kotlin plugin

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
}

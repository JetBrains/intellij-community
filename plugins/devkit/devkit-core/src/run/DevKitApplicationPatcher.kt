// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.run

import com.intellij.execution.RunConfigurationExtension
import com.intellij.execution.application.ApplicationConfiguration
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.ParametersList
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.util.PlatformUtils
import com.intellij.util.system.CpuArch
import org.jetbrains.idea.devkit.util.PsiUtil
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

internal class DevKitApplicationPatcher : RunConfigurationExtension() {
  @Suppress("SpellCheckingInspection")
  override fun <T : RunConfigurationBase<*>> updateJavaParameters(configuration: T,
                                                                  javaParameters: JavaParameters,
                                                                  runnerSettings: RunnerSettings?) {
    val applicationConfiguration = configuration as? ApplicationConfiguration ?: return
    val project = applicationConfiguration.project
    if (!PsiUtil.isIdeaProject(project)) {
      return
    }

    val vmParameters = javaParameters.vmParametersList
    val isDev = configuration.mainClassName == "org.jetbrains.intellij.build.devServer.DevMainKt"
    val vmParametersAsList = vmParameters.list
    if (vmParametersAsList.contains("--add-modules") || (!isDev && configuration.mainClassName != "com.intellij.idea.Main")) {
      return
    }

    val module = configuration.configurationModule.module
    val jdk = JavaParameters.getJdkToRunModule(module, true) ?: return
    if (!vmParameters.hasProperty(JUnitDevKitPatcher.SYSTEM_CL_PROPERTY)) {
      val qualifiedName = "com.intellij.util.lang.PathClassLoader"
      if (JUnitDevKitPatcher.loaderValid(project, module, qualifiedName)) {
        vmParameters.addProperty(JUnitDevKitPatcher.SYSTEM_CL_PROPERTY, qualifiedName)
      }
    }

    JUnitDevKitPatcher.appendAddOpensWhenNeeded(project, jdk, vmParameters)

    if (!isDev) {
      return
    }

    vmParameters.addProperty("kotlinx.coroutines.debug.enable.creation.stack.trace", "false")

    if (vmParametersAsList.none { it.startsWith("-Xmx") }) {
      vmParameters.add("-Xmx2g")
    }
    if (vmParametersAsList.none { it.startsWith("-XX:ReservedCodeCacheSize") }) {
      vmParameters.add("-XX:ReservedCodeCacheSize=512m")
    }
    vmParameters.addAll(
      "-XX:+UseG1GC",
      "-XX:SoftRefLRUPolicyMSPerMB=50",
      "-XX:MaxJavaStackTraceDepth=10000",
      "-ea",
      "-XX:CICompilerCount=2"
    )

    var productClassifier = vmParameters.getPropertyValue("idea.platform.prefix")
    productClassifier = when (productClassifier) {
      null -> "idea"
      PlatformUtils.IDEA_CE_PREFIX -> "idea-community"
      else -> productClassifier
    }

    if (!vmParameters.hasProperty("idea.config.path")) {
      val dir = FileUtilRt.toSystemIndependentName("${configuration.workingDirectory}/out/dev-data/${productClassifier.lowercase()}")
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
      catch (ignore: NoSuchFileException) {
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
    "skiko.library.path" to "$libDir/skiko-awt-runtime-all",
    "compose.swing.render.on.graphics" to "true",
  )
}

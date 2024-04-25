// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.run

import com.intellij.compiler.options.MakeProjectStepBeforeRun
import com.intellij.execution.RunConfigurationExtension
import com.intellij.execution.application.ApplicationConfiguration
import com.intellij.execution.configurations.*
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.service
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.util.PlatformUtils
import com.intellij.util.lang.UrlClassLoader
import com.intellij.util.system.CpuArch
import org.jetbrains.ide.BuiltInServerManager
import org.jetbrains.idea.devkit.requestHandlers.CompileHttpRequestHandlerToken
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
    val appConfiguration = configuration as? ApplicationConfiguration ?: return
    val project = appConfiguration.project
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
        vmParameters.addProperty(UrlClassLoader.CLASSPATH_INDEX_PROPERTY_NAME, "true")
      }
    }

    JUnitDevKitPatcher.appendAddOpensWhenNeeded(project, jdk, vmParameters)

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
    if (is17 && vmParametersAsList.none { it.startsWith("-XX:SoftRefLRUPolicyMSPerMB") }) {
      vmParameters.add("-XX:SoftRefLRUPolicyMSPerMB=50")
    }
    if (vmParametersAsList.none { it.startsWith("-XX:ReservedCodeCacheSize") }) {
      vmParameters.add("-XX:ReservedCodeCacheSize=512m")
    }

    if (!isDev) {
      return
    }

    if (appConfiguration.beforeRunTasks.none { it.providerId === MakeProjectStepBeforeRun.ID }) {
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
  )
}
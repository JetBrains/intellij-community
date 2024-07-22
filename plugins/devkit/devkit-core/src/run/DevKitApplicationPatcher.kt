// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.run

import com.intellij.compiler.options.MakeProjectStepBeforeRun
import com.intellij.execution.JavaRunConfigurationBase
import com.intellij.execution.RunConfigurationExtension
import com.intellij.execution.application.ApplicationConfiguration
import com.intellij.execution.configurations.*
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.platform.ijent.community.buildConstants.*
import com.intellij.util.PlatformUtils
import com.intellij.util.lang.UrlClassLoader
import com.intellij.util.system.CpuArch
import org.jetbrains.ide.BuiltInServerManager
import org.jetbrains.idea.devkit.requestHandlers.CompileHttpRequestHandlerToken
import org.jetbrains.idea.devkit.requestHandlers.passDataAboutBuiltInServer
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

internal class DevKitApplicationPatcher : RunConfigurationExtension() {
  @Suppress("SpellCheckingInspection")
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
    val mainClass = configuration.runClass ?: return

    passDataAboutBuiltInServer(javaParameters, project)
    val vmParameters = javaParameters.vmParametersList
    val module = configuration.configurationModule.module
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
    if (is17 && vmParametersAsList.none { it.startsWith("-XX:SoftRefLRUPolicyMSPerMB") }) {
      vmParameters.add("-XX:SoftRefLRUPolicyMSPerMB=50")
    }
    if (vmParametersAsList.none { it.startsWith("-XX:ReservedCodeCacheSize") }) {
      vmParameters.add("-XX:ReservedCodeCacheSize=512m")
    }
    if (vmParametersAsList.none { it.startsWith("-Djava.util.zip.use.nio.for.zip.file.access") }) {
      vmParameters.add("-Djava.util.zip.use.nio.for.zip.file.access=true") // IJPL-149160
    }

    enableIjentDefaultFsProvider(project, configuration, vmParameters)

    if (isDevBuild) {
      updateParametersForDevBuild(javaParameters, configuration, project)
    }
  }

  private fun enableIjentDefaultFsProvider(
    project: Project,
    configuration: JavaRunConfigurationBase,
    vmParameters: ParametersList,
  ) {
    if (!isIjentWslFsEnabledByDefaultForProduct(vmParameters.getPropertyValue("idea.platform.prefix"))) return

    // Enable the IJent file system only when the new default FS provider class is available.
    // It is required to let actual DevKit plugins work with branches without the FS provider class, like 241.
    if (JUnitDevKitPatcher.loaderValid(project, null, IJENT_REQUIRED_DEFAULT_NIO_FS_PROVIDER_CLASS)) {
      vmParameters.addAll(ENABLE_IJENT_WSL_FILE_SYSTEM_VMOPTIONS)
      vmParameters.add("-Xbootclasspath/a:${configuration.workingDirectory}/out/classes/production/$IJENT_BOOT_CLASSPATH_MODULE")
    }
    else {
      vmParameters.add("-D${IJENT_WSL_FILE_SYSTEM_REGISTRY_KEY}=false")
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
    "skiko.library.path" to "$libDir/skiko-awt-runtime-all",
    "compose.swing.render.on.graphics" to "true",
  )
}
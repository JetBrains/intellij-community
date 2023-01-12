// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.run

import com.intellij.execution.RunConfigurationExtension
import com.intellij.execution.application.ApplicationConfiguration
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.util.PlatformUtils
import org.jetbrains.idea.devkit.util.PsiUtil
import java.util.*

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

    if (!vmParametersAsList.contains("--add-opens")) {
      JUnitDevKitPatcher.appendAddOpensWhenNeeded(project, jdk, vmParameters)
    }

    if (!isDev) {
      return
    }

    if (vmParametersAsList.none { it.startsWith("-Xmx") }) {
      vmParameters.add("-Xmx2g")
    }
    if (vmParametersAsList.none { it.startsWith("-XX:ReservedCodeCacheSize") }) {
      vmParameters.add("-XX:ReservedCodeCacheSize=240m")
    }
    vmParameters.addAll(
      "-XX:+UseG1GC",
      "-XX:SoftRefLRUPolicyMSPerMB=50",
      "-XX:MaxJavaStackTraceDepth=10000",
      "-ea",
      "-XX:CICompilerCount=2",
      "-XX:PrintIdealGraphLevel=3"
    )

    if (!vmParameters.hasProperty("idea.config.path")) {
      var productClassifier = vmParameters.getPropertyValue("idea.platform.prefix")
      productClassifier = when (productClassifier) {
        null -> "idea"
        PlatformUtils.IDEA_CE_PREFIX -> "idea-community"
        else -> productClassifier.lowercase()
      }
      vmParameters.addProperty("idea.config.path",
                               FileUtilRt.toSystemIndependentName("${configuration.workingDirectory}/out/dev-data/$productClassifier/config"))
      vmParameters.addProperty("idea.system.path",
                               FileUtilRt.toSystemIndependentName("${configuration.workingDirectory}/out/dev-data/$productClassifier/system"))
    }

    vmParameters.addProperty("sun.io.useCanonCaches", "false")
    vmParameters.addProperty("sun.awt.disablegrab", "true")
    vmParameters.addProperty("sun.java2d.metal", "true")
    vmParameters.addProperty("idea.debug.mode", "true")
    vmParameters.addProperty("idea.is.internal", "true")
    vmParameters.addProperty("fus.internal.test.mode", "true")
    vmParameters.addProperty("jbScreenMenuBar.enabled", "true")
    vmParameters.addProperty("apple.laf.useScreenMenuBar", "true")
    vmParameters.addProperty("jdk.attach.allowAttachSelf")
    vmParameters.addProperty("idea.initially.ask.config", "true")
  }

  override fun isApplicableFor(configuration: RunConfigurationBase<*>): Boolean {
    return configuration is ApplicationConfiguration
  }
}

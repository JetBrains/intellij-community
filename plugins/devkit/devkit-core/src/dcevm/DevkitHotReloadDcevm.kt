// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.dcevm

import com.intellij.debugger.impl.GenericDebuggerRunner
import com.intellij.devkit.core.icons.DevkitCoreIcons
import com.intellij.execution.Executor
import com.intellij.execution.JavaRunConfigurationBase
import com.intellij.execution.RunConfigurationExtension
import com.intellij.execution.application.ApplicationConfiguration
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.IntelliJProjectUtil.isIntelliJPlatformProject
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsActions.ActionDescription
import com.intellij.openapi.util.NlsActions.ActionText
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.wm.ToolWindowId
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.idea.devkit.DevKitBundle
import javax.swing.Icon

private const val DEVKIT_DCEVM_RUNNER_ID = "DevkitDcevmRunner"
private const val DEVKIT_DCEVM_EXECUTOR_ID = "DevkitDcevmExecutor"

private fun isRelevantContext(project: Project): Boolean {
  return isIntelliJPlatformProject(project)
}

internal class DevkitDcevmExecutor : Executor() {
  override fun getId(): @NonNls String = DEVKIT_DCEVM_EXECUTOR_ID
  override fun getContextActionId(): @NonNls String = "DebugDcevm"
  override fun getToolWindowId(): String = ToolWindowId.DEBUG

  override fun getToolWindowIcon(): Icon = AllIcons.Toolwindows.ToolWindowDebugger
  override fun getIcon(): Icon = DevkitCoreIcons.HotReload
  override fun getDisabledIcon(): Icon? = null

  override fun getDescription(): @ActionDescription String = DevKitBundle.message("action.DevkitDcevmExecutor.description")
  override fun getActionName(): @ActionText String = DevKitBundle.message("action.DevkitDcevmExecutor.text")

  override fun getStartActionText(): @Nls(capitalization = Nls.Capitalization.Title) String {
    return DevKitBundle.message("action.DevkitDcevmExecutor.text")
  }

  override fun getStartActionText(configurationName: @NlsSafe String): @Nls(capitalization = Nls.Capitalization.Title) String {
    if (configurationName.isEmpty()) return startActionText
    val configName = shortenNameIfNeeded(configurationName)
    return DevKitBundle.message("action.DevkitDcevmExecutor.text.0", configName)
  }

  override fun getHelpId(): @NonNls String? = null

  override fun isApplicable(project: Project): Boolean {
    return isRelevantContext(project)
  }
}

internal class DevkitDcevmRunner : GenericDebuggerRunner() {
  override fun getRunnerId(): String = DEVKIT_DCEVM_RUNNER_ID

  override fun canRun(executorId: String, profile: RunProfile): Boolean {
    return executorId == DEVKIT_DCEVM_EXECUTOR_ID
           && profile is ApplicationConfiguration
           && isRelevantContext(profile.configurationModule.project)
  }
}

internal class DevkitDcevmCommandLinePatcher : RunConfigurationExtension() {

  override fun <T : RunConfigurationBase<*>?> updateJavaParameters(
    configuration: T & Any,
    params: JavaParameters,
    runnerSettings: RunnerSettings?,
  ) {
    // only update parameters for our executor, here we don't know it
  }

  override fun <T : RunConfigurationBase<*>?> updateJavaParameters(
    configuration: T & Any,
    params: JavaParameters,
    runnerSettings: RunnerSettings?,
    executor: Executor,
  ) {
    if (executor.id != DEVKIT_DCEVM_EXECUTOR_ID) return
    if (!isIntelliJPlatformProject(configuration.project)) return
    if (configuration !is JavaRunConfigurationBase) return

    if (params.mainClass != "org.jetbrains.intellij.build.devServer.DevMainKt"
        && params.mainClass != "com.intellij.idea.Main"
        && params.mainClass != "com.android.tools.idea.Main") {
      // only IDE build configurations supported here so far
      return
    }

    val module = configuration.configurationModule.module ?: return
    val jdk = JavaParameters.getJdkToRunModule(module, true) ?: return
    val versionString = jdk.versionString ?: ""
    if (!versionString.contains("JetBrains Runtime")) {
      // we may only expect -XX:+AllowEnhancedClassRedefinition on JBR
      return
    }

    val vmParametersList = params.vmParametersList
    vmParametersList.add("-XX:+AllowEnhancedClassRedefinition")
  }

  override fun isApplicableFor(configuration: RunConfigurationBase<*>): Boolean {
    return configuration is ApplicationConfiguration
  }
}
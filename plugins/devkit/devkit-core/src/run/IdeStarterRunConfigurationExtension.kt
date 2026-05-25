// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.run

import com.intellij.execution.RunConfigurationExtension
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.ModuleBasedConfiguration
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.registry.Registry

private const val REMOTE_DEV_RUN_ENV = "REMOTE_DEV_RUN"
private const val JUNIT_RUNNER_USE_INSTALLER_ENV = "JUNIT_RUNNER_USE_INSTALLER"
internal const val IDE_STARTER_MODULE_PREFIX = "intellij.tools.ide.starter"
internal const val IDE_STARTER_RUN_MODES_ENABLED_KEY = "devkit.ide.starter.run.modes.enabled"

internal fun isIdeStarterRunModesEnabled(): Boolean {
  return Registry.`is`(IDE_STARTER_RUN_MODES_ENABLED_KEY, true)
}

internal fun isIdeStarterModule(module: Module): Boolean {
  if (!isIdeStarterRunModesEnabled()) return false
  return ModuleRootManager.getInstance(module).dependencies.any { dep ->
    dep.name.startsWith(IDE_STARTER_MODULE_PREFIX)
  }
}

internal class IdeStarterRunConfigurationExtension : RunConfigurationExtension() {
  override fun isApplicableFor(configuration: RunConfigurationBase<*>): Boolean {
    if (configuration !is ModuleBasedConfiguration<*, *>) return false
    val module = configuration.configurationModule?.module ?: return false
    return isIdeStarterModule(module)
  }

  override fun <T : RunConfigurationBase<*>> updateJavaParameters(
    configuration: T,
    params: JavaParameters,
    runnerSettings: RunnerSettings?,
  ) {
    val project = configuration.project
    val settings = IdeStarterRunSettings.getInstance(project)

    if (settings.useSplitMode) {
      params.env[REMOTE_DEV_RUN_ENV] = "true"
    }

    if (settings.useInstaller) {
      params.env[JUNIT_RUNNER_USE_INSTALLER_ENV] = "true"
    }
  }
}

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

internal const val IDE_STARTER_MODULE = "intellij.tools.ide.starter"
internal const val RDCT_TEST_FRAMEWORK_MODULE = "intellij.rdct.testFramework"
internal const val IDE_STARTER_RUN_MODES_ENABLED_KEY = "devkit.ide.starter.run.modes.enabled"

internal fun isIdeStarterRunModesEnabled(): Boolean {
  return Registry.`is`(IDE_STARTER_RUN_MODES_ENABLED_KEY, true)
}

internal fun isIdeStarterModule(module: Module): Boolean {
  if (!isIdeStarterRunModesEnabled()) return false
  val dependencyNames = collectDependencyModuleNames(module)
  if (RDCT_TEST_FRAMEWORK_MODULE in dependencyNames) return false
  return IDE_STARTER_MODULE in dependencyNames
}

/**
 * Collects the names of [module] and all of its module dependencies, transitively.
 */
private fun collectDependencyModuleNames(module: Module): Set<String> {
  val names = HashSet<String>()
  val visited = HashSet<Module>()
  val queue = ArrayDeque<Module>()
  queue.add(module)
  visited.add(module)
  while (queue.isNotEmpty()) {
    val current = queue.removeFirst()
    names.add(current.name)
    for (dependency in ModuleRootManager.getInstance(current).dependencies) {
      if (visited.add(dependency)) {
        queue.add(dependency)
      }
    }
  }
  return names
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

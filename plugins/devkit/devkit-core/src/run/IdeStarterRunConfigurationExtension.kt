// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.run

import com.intellij.execution.RunConfigurationExtension
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.ModuleBasedConfiguration
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.graph.Graph

private const val REMOTE_DEV_RUN_ENV = "REMOTE_DEV_RUN"
private const val JUNIT_RUNNER_USE_INSTALLER_ENV = "JUNIT_RUNNER_USE_INSTALLER"

internal const val IDE_STARTER_MODULE = "intellij.tools.ide.starter"
internal const val RDCT_TEST_FRAMEWORK_MODULE = "intellij.rdct.testFramework"
internal const val IDE_STARTER_RUN_MODES_ENABLED_KEY = "devkit.ide.starter.run.modes.enabled"

private val IDE_STARTER_MODULE_NAMES_KEY = Key.create<CachedValue<Set<String>>>("devkit.ide.starter.module.names")

internal fun isIdeStarterRunModesEnabled(): Boolean {
  return Registry.`is`(IDE_STARTER_RUN_MODES_ENABLED_KEY, true)
}

internal fun isIdeStarterModule(module: Module): Boolean {
  return isIdeStarterRunModesEnabled() && module.name in ideStarterModuleNames(module.project)
}

/**
 * Names of the modules for which the IDE Starter run modes apply.
 *
 * The set is computed once per project and reused until the project roots change: it is queried for every run configuration
 * every time the run configurations are serialized (see `RunConfigurationExtensionsManager.writeExternal`), so computing the
 * module dependencies on each call makes saving the settings quadratic in the size of the project.
 */
private fun ideStarterModuleNames(project: Project): Set<String> {
  return CachedValuesManager.getManager(project).getCachedValue(project, IDE_STARTER_MODULE_NAMES_KEY, {
    CachedValueProvider.Result.create(computeIdeStarterModuleNames(project), ProjectRootManager.getInstance(project))
  }, false)
}

private fun computeIdeStarterModuleNames(project: Project): Set<String> {
  val moduleManager = ModuleManager.getInstance(project)
  val ideStarterModule = moduleManager.findModuleByName(IDE_STARTER_MODULE) ?: return emptySet()
  val graph = moduleManager.moduleGraph()

  val modules = collectDependentModules(graph, ideStarterModule)
  val rdctTestFrameworkModule = moduleManager.findModuleByName(RDCT_TEST_FRAMEWORK_MODULE)
  if (rdctTestFrameworkModule != null) {
    modules.removeAll(collectDependentModules(graph, rdctTestFrameworkModule))
  }
  return modules.mapTo(HashSet(modules.size)) { it.name }
}

/**
 * Collects [root] and all the modules depending on it, transitively.
 */
private fun collectDependentModules(graph: Graph<Module>, root: Module): MutableSet<Module> {
  val visited = HashSet<Module>()
  val queue = ArrayDeque<Module>()
  visited.add(root)
  queue.add(root)
  while (queue.isNotEmpty()) {
    for (dependent in graph.getOut(queue.removeFirst())) {
      if (visited.add(dependent)) {
        queue.add(dependent)
      }
    }
  }
  return visited
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

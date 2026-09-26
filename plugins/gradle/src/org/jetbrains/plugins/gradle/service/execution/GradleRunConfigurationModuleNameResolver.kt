// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.execution

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.settings.RunConfigurationModuleNameResolver
import com.intellij.openapi.module.Module

/**
 * Resolves the IDE [Module] for a run configuration imported by the Gradle `idea-ext` plugin (and its
 * derivatives)
 */
internal class GradleRunConfigurationModuleNameResolver : RunConfigurationModuleNameResolver {
  override fun resolveModule(modelsProvider: IdeModifiableModelsProvider, moduleName: String): Module? {
    // IDEA-391836
    // Each '.' in the requested name may correspond to either a '.' or an escaped '_' in the actual module name.
    if (!moduleName.contains('.')) return null
    val pattern = Regex(moduleName.split('.').joinToString(separator = "[._]") { Regex.escape(it) })
    val matches = modelsProvider.modifiableModuleModel.modules.filter { pattern.matches(it.name) }

    return when (matches.size) {
      0 -> null
      1 -> matches.single()
      else -> {
        LOG.warn("Cannot resolve module for run configuration: the name '$moduleName' matches several modules " +
                 matches.joinToString(prefix = "[", postfix = "]") { it.name })
        null
      }
    }
  }

  companion object {
    private val LOG = logger<GradleRunConfigurationModuleNameResolver>()
  }
}

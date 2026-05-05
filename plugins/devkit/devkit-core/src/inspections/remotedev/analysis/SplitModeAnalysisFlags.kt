// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.remotedev.analysis

import com.intellij.openapi.util.registry.RegistryManager

internal object SplitModeAnalysisFlags {
  const val TRANSITIVE_DEPENDENCIES_ANALYSIS_KEY: String =
    "devkit.remote.dev.split.mode.analysis.transitive.dependencies"
  const val CONTAINING_PLUGINS_ANALYSIS_KEY: String =
    "devkit.remote.dev.split.mode.analysis.containing.plugins"
  const val SKIP_INSPECTIONS_FOR_PREDEFINED_MODULE_KINDS_KEY: String =
    "devkit.remote.dev.split.mode.inspections.skip.predefined"
  const val ADDITIONAL_PREDEFINED_MODULE_KINDS_FILE_KEY: String =
    "devkit.remote.dev.split.mode.analysis.predefined.module.kinds.additional.file"

  fun isTransitiveDependenciesAnalysisEnabled(): Boolean {
    return RegistryManager.getInstance().`is`(TRANSITIVE_DEPENDENCIES_ANALYSIS_KEY)
  }

  fun isContainingPluginsAnalysisEnabled(): Boolean {
    return RegistryManager.getInstance().`is`(CONTAINING_PLUGINS_ANALYSIS_KEY)
  }

  fun isSkippingInspectionsForPredefinedModuleKindsEnabled(): Boolean {
    return RegistryManager.getInstance().`is`(SKIP_INSPECTIONS_FOR_PREDEFINED_MODULE_KINDS_KEY)
  }

  fun getAdditionalPredefinedModuleKindsFilePath(): String? {
    val value = RegistryManager.getInstance().get(ADDITIONAL_PREDEFINED_MODULE_KINDS_FILE_KEY).asString()
    if (value.isBlank()) {
      return null
    }
    return value
  }
}

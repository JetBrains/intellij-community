// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.remotedev.analysis

import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryManager

internal object SplitModeAnalysisFlags {

  fun isTransitiveDependenciesAnalysisEnabled(): Boolean {
    return RegistryManager.getInstance().`is`("devkit.remote.dev.split.mode.analysis.transitive.dependencies")
  }

  fun isContainingPluginsAnalysisEnabled(): Boolean {
    return RegistryManager.getInstance().`is`("devkit.remote.dev.split.mode.analysis.containing.plugins")
  }

  fun isSkippingInspectionsForPredefinedModuleKindsEnabled(): Boolean {
    return RegistryManager.getInstance().`is`("devkit.remote.dev.split.mode.inspections.skip.predefined")
  }

  fun isXmlInspectionsForNonNativePluginEnabled(): Boolean {
    return RegistryManager.getInstance().`is`("devkit.remote.dev.split.mode.inspections.enable.xml.for.non.native.plugin")
  }

  fun isQodanaAnalysisScopeLimiterEnabled(): Boolean {
    return Registry.`is`("devkit.remote.dev.split.mode.qodana.analysis.scope.limiter.enabled", false)
  }

  fun getAdditionalPredefinedModuleKindsFilePath(): String? {
    val value = RegistryManager.getInstance().get("devkit.remote.dev.split.mode.analysis.predefined.module.kinds.additional.file").asString()
    if (value.isBlank()) {
      return null
    }
    return value
  }

  fun getAdditionalQodanaAnalysisScopeFilePath(): String? {
    val value = Registry.stringValue("devkit.remote.dev.split.mode.qodana.analysis.scope.additional.file", "")
    if (value.isBlank()) {
      return null
    }
    return value
  }
}

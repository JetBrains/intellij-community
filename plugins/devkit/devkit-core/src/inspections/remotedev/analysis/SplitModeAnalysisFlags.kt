// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.remotedev.analysis

import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryManager
import org.jetbrains.idea.devkit.inspections.remotedev.SplitModeInspectionResourceReadMode

internal object SplitModeAnalysisFlags {

  fun isTransitiveDependenciesAnalysisEnabled(): Boolean {
    return RegistryManager.getInstance().`is`("devkit.split.mode.analysis.transitive.dependencies")
  }

  fun isContainingPluginsAnalysisEnabled(): Boolean {
    return RegistryManager.getInstance().`is`("devkit.split.mode.analysis.containing.plugins")
  }

  fun isSkippingInspectionsForPredefinedModuleKindsEnabled(): Boolean {
    return RegistryManager.getInstance().`is`("devkit.split.mode.inspections.skip.predefined")
  }

  fun isShowAllErrorsInModulesWithImplicitKind(): Boolean {
    return RegistryManager.getInstance().`is`("devkit.split.mode.inspections.enable.in.implicit.module.kind")
  }

  fun isReportImplicitModuleKindEnabled(): Boolean {
    return RegistryManager.getInstance().`is`("devkit.split.mode.report.implicit.module.kind")
  }

  fun isRunJpsToBazelInQuickFixEnabled(): Boolean {
    return Registry.`is`("devkit.split.mode.run.jps.to.bazel.in.quick.fix", false)
  }

  fun isQodanaAnalysisScopeLimiterEnabled(): Boolean {
    return Registry.`is`("devkit.split.mode.qodana.analysis.scope.limiter.enabled", false)
  }

  fun getApiRestrictionsReadMode(): SplitModeInspectionResourceReadMode {
    return getResourceReadMode("devkit.split.mode.analysis.api.restrictions.source", SplitModeInspectionResourceReadMode.BUNDLED_ONLY)
  }

  fun getPredefinedModuleKindsReadMode(): SplitModeInspectionResourceReadMode {
    return getResourceReadMode("devkit.split.mode.analysis.predefined.module.kinds.source", SplitModeInspectionResourceReadMode.BUNDLED_ONLY)
  }

  fun getQodanaAnalysisScopeReadMode(): SplitModeInspectionResourceReadMode {
    return getResourceReadMode("devkit.split.mode.qodana.analysis.scope.source", SplitModeInspectionResourceReadMode.PROJECT_ONLY)
  }

  private fun getResourceReadMode(
    registryKey: String,
    defaultMode: SplitModeInspectionResourceReadMode,
  ): SplitModeInspectionResourceReadMode {
    val registryValue = RegistryManager.getInstance().get(registryKey)
    val value = registryValue.selectedOption ?: registryValue.asString()
    return SplitModeInspectionResourceReadMode.fromRegistryValue(registryKey, value, defaultMode)
  }
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.remotedev.analysis

import com.intellij.openapi.util.NlsSafe

internal data class ModuleAnalysis(
  val resolvedModuleKind: ResolvedModuleKind,
  val evidence: ModuleKindEvidence = ModuleKindEvidence(),
)

internal data class ModuleKindEvidence(
  val hasOwnFrontendEvidence: Boolean = false,
  val hasOwnBackendEvidence: Boolean = false,
  val hasOwnExplicitFrontendDependency: Boolean = false,
  val hasOwnExplicitBackendDependency: Boolean = false,
  val hasOwnExplicitMonolithDependency: Boolean = false,
) {
  val hasOwnExplicitPlatformDependency: Boolean
    get() = hasOwnExplicitFrontendDependency || hasOwnExplicitBackendDependency || hasOwnExplicitMonolithDependency
}

internal data class ResolvedModuleKind(
  val kind: SplitModeApiRestrictionsService.ModuleKind,
  val reasoning: @NlsSafe String,
)

internal data class DependencyTrace(
  val cacheKey: String,
  val description: @NlsSafe String,
)

internal data class DependencyInfo(
  val name: String,
  val trace: DependencyTrace,
) {
  val originDescription: @NlsSafe String
    get() = trace.description
}

internal data class DependencyFacts(
  val frontendEvidence: DependencyInfo? = null,
  val backendEvidence: DependencyInfo? = null,
  val monolithEvidence: DependencyInfo? = null,
) {
  fun evidence(kind: SplitModeApiRestrictionsService.ModuleKind): DependencyInfo? {
    return when (kind) {
      SplitModeApiRestrictionsService.ModuleKind.FRONTEND -> frontendEvidence
      SplitModeApiRestrictionsService.ModuleKind.BACKEND -> backendEvidence
      SplitModeApiRestrictionsService.ModuleKind.MONOLITH -> monolithEvidence
      SplitModeApiRestrictionsService.ModuleKind.MIXED,
      SplitModeApiRestrictionsService.ModuleKind.SHARED,
      is SplitModeApiRestrictionsService.ModuleKind.Composite,
        -> null
    }
  }

  fun representativeDependencies(): List<DependencyInfo> {
    return listOfNotNull(monolithEvidence, frontendEvidence, backendEvidence)
  }

  fun mapTraces(transform: (DependencyTrace) -> DependencyTrace): DependencyFacts {
    return DependencyFacts(
      frontendEvidence = frontendEvidence?.let { it.copy(trace = transform(it.trace)) },
      backendEvidence = backendEvidence?.let { it.copy(trace = transform(it.trace)) },
      monolithEvidence = monolithEvidence?.let { it.copy(trace = transform(it.trace)) },
    )
  }
}

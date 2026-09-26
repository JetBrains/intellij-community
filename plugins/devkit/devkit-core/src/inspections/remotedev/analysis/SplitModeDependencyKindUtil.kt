// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.remotedev.analysis

internal const val FRONTEND_PLATFORM_MODULE_BASE_NAME = "intellij.platform.frontend"
internal const val BACKEND_PLATFORM_MODULE_BASE_NAME = "intellij.platform.backend"
internal const val MONOLITH_PLATFORM_MODULE_BASE_NAME = "intellij.platform.monolith"

private val frontendPlatformModuleNames = moduleNameVariants(FRONTEND_PLATFORM_MODULE_BASE_NAME)
private val backendPlatformModuleNames = moduleNameVariants(BACKEND_PLATFORM_MODULE_BASE_NAME)

internal fun getExplicitPlatformDependencyName(moduleKind: SplitModeApiRestrictionsService.ModuleKind): String {
  return when (moduleKind) {
    SplitModeApiRestrictionsService.ModuleKind.FRONTEND -> FRONTEND_PLATFORM_MODULE_BASE_NAME
    SplitModeApiRestrictionsService.ModuleKind.BACKEND -> BACKEND_PLATFORM_MODULE_BASE_NAME
    SplitModeApiRestrictionsService.ModuleKind.MONOLITH -> MONOLITH_PLATFORM_MODULE_BASE_NAME
    SplitModeApiRestrictionsService.ModuleKind.MIXED,
    SplitModeApiRestrictionsService.ModuleKind.SHARED,
    is SplitModeApiRestrictionsService.ModuleKind.Composite,
      -> error("Explicit split-mode dependency is only supported for frontend/backend/monolith module kinds")
  }
}

internal fun recognizeExplicitDependencyKind(dependencyName: String): SplitModeApiRestrictionsService.ModuleKind? {
  if (isExplicitFrontendDependency(dependencyName)) {
    return SplitModeApiRestrictionsService.ModuleKind.FRONTEND
  }
  if (isExplicitBackendDependency(dependencyName)) {
    return SplitModeApiRestrictionsService.ModuleKind.BACKEND
  }
  if (isExplicitMonolithDependency(dependencyName)) {
    return SplitModeApiRestrictionsService.ModuleKind.MONOLITH
  }
  return null
}

internal fun isExplicitFrontendDependency(dependencyName: String): Boolean {
  return dependencyName in frontendPlatformModuleNames
}

internal fun isExplicitBackendDependency(dependencyName: String): Boolean {
  return dependencyName in backendPlatformModuleNames
}

internal fun isExplicitMonolithDependency(dependencyName: String): Boolean {
  return dependencyName == MONOLITH_PLATFORM_MODULE_BASE_NAME
}

internal fun isFrontendDependency(dependencyName: String): Boolean {
  return recognizeExplicitDependencyKind(dependencyName) == SplitModeApiRestrictionsService.ModuleKind.FRONTEND
}

internal fun isBackendDependency(dependencyName: String): Boolean {
  return recognizeExplicitDependencyKind(dependencyName) == SplitModeApiRestrictionsService.ModuleKind.BACKEND
}

private fun moduleNameVariants(baseName: String): Set<String> {
  return setOf(
    baseName,
    "$baseName.split"
  )
}
// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.core

import com.intellij.facet.FacetManager
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.ModuleSourceInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.PlatformModuleInfo

@Deprecated("Use 'org.jetbrains.kotlin.idea.base.util.isAndroidModule' instead")
fun Module.isAndroidModule(modelsProvider: IdeModifiableModelsProvider? = null): Boolean {
    val facetModel = modelsProvider?.getModifiableFacetModel(this) ?: FacetManager.getInstance(this)
    val facets = facetModel.allFacets
    return facets.any { it.javaClass.simpleName == "AndroidFacet" }
}

@Deprecated("Use 'org.jetbrains.kotlin.idea.base.projectStructure.unwrapModuleSourceInfo()' instead")
@Suppress("unused", "DEPRECATION", "DeprecatedCallableAddReplaceWith")
fun ModuleInfo.unwrapModuleSourceInfo(): org.jetbrains.kotlin.idea.caches.project.ModuleSourceInfo? {
    return when (this) {
        is ModuleSourceInfo -> this
        is PlatformModuleInfo -> this.platformModule
        else -> null
    }
}
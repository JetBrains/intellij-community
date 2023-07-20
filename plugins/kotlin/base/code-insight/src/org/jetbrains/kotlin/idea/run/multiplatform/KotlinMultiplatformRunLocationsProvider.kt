// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.run.multiplatform

import com.intellij.execution.Location
import com.intellij.execution.PsiLocation
import com.intellij.execution.actions.MultipleRunLocationsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.kotlin.idea.base.util.isAndroidModule
import org.jetbrains.kotlin.config.KotlinModuleKind
import org.jetbrains.kotlin.config.KotlinSourceRootType
import org.jetbrains.kotlin.idea.base.facet.implementingModules
import org.jetbrains.kotlin.idea.base.facet.isNewMultiPlatformModule
import org.jetbrains.kotlin.idea.base.facet.kotlinSourceRootType
import org.jetbrains.kotlin.idea.base.projectStructure.getKotlinSourceRootType
import org.jetbrains.kotlin.idea.base.projectStructure.toModuleGroup
import org.jetbrains.kotlin.idea.facet.KotlinFacet

class KotlinMultiplatformRunLocationsProvider : MultipleRunLocationsProvider() {
    override fun getLocationDisplayName(locationCreatedFrom: Location<*>, originalLocation: Location<*>): String? {
        val module = locationCreatedFrom.module ?: return null
        @NlsSafe val name = compactedGradleProjectId(module)
        return "[$name]"
    }

    override fun getAlternativeLocations(originalLocation: Location<*>): List<Location<*>> {
        val originalModule = originalLocation.module ?: return emptyList()
        if (originalModule.isNewMultiPlatformModule) {
            return emptyList()
        }

        val virtualFile = originalLocation.virtualFile ?: return emptyList()
        val projectFileIndex = ProjectFileIndex.getInstance(originalModule.project)
        val sourceType = projectFileIndex.getKotlinSourceRootType(virtualFile) ?: return emptyList()
        return modulesToRunFrom(originalModule, sourceType).map { PsiLocation(originalLocation.project, it, originalLocation.psiElement) }
    }
}

private fun compactedGradleProjectId(module: Module): String {
    return if (module.isNewMultiPlatformModule) {
        // TODO: more robust way to get compilation/sourceSet name
        module.name.substringAfterLast('_')
    } else {
        module.toModuleGroup().baseModule.name
    }
}

private fun modulesToRunFrom(
    originalModule: Module,
    originalSourceType: KotlinSourceRootType
): List<Module> {
    val modules = originalModule.implementingModules
    if (!originalModule.isNewMultiPlatformModule) return modules
    val compilations = modules.filter {
        KotlinFacet.get(it)?.configuration?.settings?.kind == KotlinModuleKind.COMPILATION_AND_SOURCE_SET_HOLDER
    }
    return compilations.filter { it.isAndroidModule() || it.kotlinSourceRootType == originalSourceType }
}
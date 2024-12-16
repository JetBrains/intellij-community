// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.analysisApiPlatform

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinGlobalSearchScopeMerger
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinResolutionScopeProvider
import org.jetbrains.kotlin.analysis.api.projectStructure.KaBuiltinsModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibrarySourceModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.projectStructure.allDirectDependencies
import org.jetbrains.kotlin.analysis.decompiler.psi.BuiltinsVirtualFileProvider

internal class IdeKotlinByModulesResolutionScopeProvider(
    private val project: Project
) : KotlinResolutionScopeProvider {

    override fun getResolutionScope(module: KaModule): GlobalSearchScope {
        val moduleWithDependentScopes = getResolutionScopes(module)
        return KotlinGlobalSearchScopeMerger.getInstance(project).union(moduleWithDependentScopes)
    }

    private fun getResolutionScopes(module: KaModule): List<GlobalSearchScope> {
        val modules = buildSet {
            add(module)
            addAll(module.allDirectDependencies())
            if (module is KaLibrarySourceModule) {
                add(module.binaryLibrary)
            }
        }

        return buildList {
            modules.mapTo(this) { it.contentScope }
            if (modules.none { it is KaBuiltinsModule }) {
                // Workaround for KT-72988
                // after fixed, it should probably only be added if `module.targetPlatform.hasCommonKotlinStdlib()`
                add(createBuiltinsScope())
            }
        }
    }

    private fun createBuiltinsScope(): GlobalSearchScope {
        return BuiltinsVirtualFileProvider.getInstance().createBuiltinsScope(project)
    }
}

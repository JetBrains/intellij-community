// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.analysisApiProviders

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analysis.project.structure.KtModule
import org.jetbrains.kotlin.analysis.project.structure.KtSourceModule
import org.jetbrains.kotlin.analysis.project.structure.ProjectStructureProvider
import org.jetbrains.kotlin.analysis.project.structure.allDirectDependencies
import org.jetbrains.kotlin.analysis.providers.KotlinResolutionScopeProvider
import org.jetbrains.kotlin.idea.base.projectStructure.ModuleDependencyCollector
import org.jetbrains.kotlin.idea.base.projectStructure.collectDependencies
import org.jetbrains.kotlin.idea.base.projectStructure.ideaModule
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.ModuleSourceInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.ModuleTestSourceInfo
import org.jetbrains.kotlin.idea.base.util.Frontend10ApiUsage
import org.jetbrains.kotlin.idea.base.util.minus

internal class IdeKotlinByModulesResolutionScopeProvider(private val project: Project) : KotlinResolutionScopeProvider() {
    override fun getResolutionScope(module: KtModule): GlobalSearchScope {
        return when (module) {
            is KtSourceModule -> {
                @OptIn(Frontend10ApiUsage::class)
                val moduleInfo = module.moduleInfo as ModuleSourceInfo
                val includeTests = moduleInfo is ModuleTestSourceInfo
                return excludeIgnoredModulesByKotlinProjectModel(moduleInfo, module, includeTests)
            }

            else -> {
                val allModules = buildList {
                    add(module)
                    addAll(module.allDirectDependencies())
                }
                GlobalSearchScope.union(allModules.map { it.contentScope })
            }
        }
    }

    /**
     * Some dependencies from order entities might be filtered by [org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.SourceModuleDependenciesFilter]
     * Those entries would still be present in the [GlobalSearchScope.moduleWithDependenciesAndLibrariesScope].
     * Analysis API should know nothing about such dependencies as it works only by KtModule (which itself works by ModuleInfo). So, we exclude such dependencies
     */
    private fun excludeIgnoredModulesByKotlinProjectModel(
        moduleInfo: ModuleSourceInfo,
        module: KtSourceModule,
        includeTests: Boolean
    ): GlobalSearchScope {
        val ignored = moduleInfo
            .collectDependencies(ModuleDependencyCollector.CollectionMode.COLLECT_IGNORED)
            .map { it.contentScope }

        val searchScope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module.ideaModule, includeTests)
        if (ignored.isEmpty()) return searchScope
        return (searchScope - GlobalSearchScope.union(ignored)) as GlobalSearchScope
    }
}
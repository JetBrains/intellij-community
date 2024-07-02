// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.analysisApiPlatform

import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinResolutionScopeProvider
import org.jetbrains.kotlin.analysis.api.projectStructure.*
import org.jetbrains.kotlin.idea.base.projectStructure.*
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.ModuleSourceInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.ModuleTestSourceInfo
import org.jetbrains.kotlin.idea.base.util.Frontend10ApiUsage
import org.jetbrains.kotlin.idea.base.util.fileScope
import org.jetbrains.kotlin.idea.base.util.minus

internal class IdeKotlinByModulesResolutionScopeProvider : KotlinResolutionScopeProvider() {
    override fun getResolutionScope(module: KaModule): GlobalSearchScope {
        val scope = when (module) {
            is KaSourceModule -> {
                @OptIn(Frontend10ApiUsage::class)
                val moduleInfo = module.moduleInfo as ModuleSourceInfo
                val includeTests = moduleInfo is ModuleTestSourceInfo
                val scope = excludeIgnoredModulesByKotlinProjectModel(moduleInfo, module, includeTests)
                if (module is KtSourceModuleByModuleInfoForOutsider) {
                    module.adjustContentScope(scope)
                } else {
                    scope
                }
            }

            is KaDanglingFileModule -> {
                val scopes = listOf(
                    module.file.fileScope(),
                    getResolutionScope(module.contextModule)
                )

                GlobalSearchScope.union(scopes)
            }

            else -> {
                val allModules = buildList {
                    if (module is KaLibrarySourceModule) {
                        add(module.binaryLibrary)
                    } else {
                        add(module)
                    }
                    addAll(module.allDirectDependencies())
                }
                GlobalSearchScope.union(allModules.map { it.contentScope })
            }
        }
        return if (module is KtSourceModuleByModuleInfo) {
            KotlinResolveScopeEnlarger.enlargeScope(scope, module.ideaModule, isTestScope = false)
        } else {
            scope
        }
    }

    /**
     * Some dependencies from order entities might be filtered by [org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.SourceModuleDependenciesFilter]
     * Those entries would still be present in the [GlobalSearchScope.moduleWithDependenciesAndLibrariesScope].
     * Analysis API should know nothing about such dependencies as it works only by KaModule (which itself works by ModuleInfo). So, we exclude such dependencies
     */
    private fun excludeIgnoredModulesByKotlinProjectModel(
        moduleInfo: ModuleSourceInfo,
        module: KaSourceModule,
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

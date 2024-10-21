// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.analysisApiPlatform

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinGlobalSearchScopeMerger
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinResolutionScopeProvider
import org.jetbrains.kotlin.analysis.api.projectStructure.*
import org.jetbrains.kotlin.analysis.decompiler.psi.BuiltinsVirtualFileProvider
import org.jetbrains.kotlin.idea.base.analysis.builtins.hasCommonKotlinStdlib
import org.jetbrains.kotlin.idea.base.projectStructure.*
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.ModuleSourceInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.ModuleTestSourceInfo
import org.jetbrains.kotlin.idea.base.util.K1ModeProjectStructureApi
import org.jetbrains.kotlin.idea.base.util.fileScope
import org.jetbrains.kotlin.idea.base.util.minus

internal class IdeKotlinByModulesResolutionScopeProvider(private val project: Project) : KotlinResolutionScopeProvider {
    override fun getResolutionScope(module: KaModule): GlobalSearchScope {
        val scope = when (module) {
            is KaSourceModule -> {
                @OptIn(K1ModeProjectStructureApi::class)
                val moduleInfo = module.moduleInfo as ModuleSourceInfo
                val includeTests = moduleInfo is ModuleTestSourceInfo
                val scope = excludeIgnoredModulesByKotlinProjectModel(moduleInfo, module, includeTests)

                adjustScope(scope, module)
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
                KotlinGlobalSearchScopeMerger.getInstance(project).union(allModules.map { it.contentScope })
            }
        }
        return if (module is KtSourceModuleByModuleInfo) {
            // remove dependency on `ModuleInfo` after KT-69980 is fixed
            KotlinResolveScopeEnlarger.enlargeScope(scope, module.ideaModule, isTestScope = module.ideaModuleInfo is ModuleTestSourceInfo)
        } else {
            scope
        }
    }

    private fun adjustScope(baseScope: GlobalSearchScope, module: KaSourceModule): GlobalSearchScope {
        var scope = baseScope

        if (module is KtSourceModuleByModuleInfoForOutsider) {
            scope = module.adjustContentScope(scope)
        }

        if (module.targetPlatform.hasCommonKotlinStdlib()) {
            // we do not have builtins in common stdlib
            scope = scope.withBuiltInsScope(module.project)
        }

        return scope
    }

    private fun GlobalSearchScope.withBuiltInsScope(project: Project): GlobalSearchScope {
        val builtinsScope = BuiltinsVirtualFileProvider.getInstance().createBuiltinsScope(project)
        return GlobalSearchScope.union(listOf(this, builtinsScope))
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

        val searchScope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module.openapiModule, includeTests)
        if (ignored.isEmpty()) return searchScope
        return (searchScope - KotlinGlobalSearchScopeMerger.getInstance(project).union(ignored)) as GlobalSearchScope
    }
}

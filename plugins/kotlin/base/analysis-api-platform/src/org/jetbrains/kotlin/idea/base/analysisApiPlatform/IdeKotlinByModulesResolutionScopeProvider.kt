// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.analysisApiPlatform

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinResolutionScopeProvider
import org.jetbrains.kotlin.analysis.api.projectStructure.KaDanglingFileModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibrarySourceModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaSourceModule
import org.jetbrains.kotlin.analysis.api.projectStructure.allDirectDependencies
import org.jetbrains.kotlin.analysis.decompiler.psi.BuiltinsVirtualFileProvider
import org.jetbrains.kotlin.idea.base.projectStructure.KtSourceModuleByModuleInfoForOutsider
import org.jetbrains.kotlin.idea.base.projectStructure.ModuleDependencyCollector
import org.jetbrains.kotlin.idea.base.projectStructure.collectDependencies
import org.jetbrains.kotlin.idea.base.projectStructure.ideaModule
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.ModuleSourceInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.ModuleTestSourceInfo
import org.jetbrains.kotlin.idea.base.util.Frontend10ApiUsage
import org.jetbrains.kotlin.idea.base.util.fileScope
import org.jetbrains.kotlin.idea.base.util.minus
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.isJs
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.platform.konan.isNative
import org.jetbrains.kotlin.platform.wasm.isWasm

internal class IdeKotlinByModulesResolutionScopeProvider : KotlinResolutionScopeProvider {
    override fun getResolutionScope(module: KaModule): GlobalSearchScope {
        return when (module) {
            is KaSourceModule -> {
                @OptIn(Frontend10ApiUsage::class)
                val moduleInfo = module.moduleInfo as ModuleSourceInfo
                val includeTests = moduleInfo is ModuleTestSourceInfo
                val scope = excludeIgnoredModulesByKotlinProjectModel(moduleInfo, module, includeTests)

                return adjustScope(scope, module)
            }

            is KaDanglingFileModule -> {
                val scopes = listOf(
                    module.file.fileScope(),
                    getResolutionScope(module.contextModule)
                )

                return GlobalSearchScope.union(scopes)
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

    /**
     * Checks if a source module with the [this] target will depend on a common stdlib artifact.
     *
     * This also means that the module is `common` in HMPP terms
     */
    private fun TargetPlatform.hasCommonKotlinStdlib(): Boolean {
        if (componentPlatforms.size <= 1) return false
        if (isJvm()) return false
        if (isJs()) return false
        if (isWasm()) return false
        if (isNative()) return false
        return true
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

        val searchScope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module.ideaModule, includeTests)
        if (ignored.isEmpty()) return searchScope
        return (searchScope - GlobalSearchScope.union(ignored)) as GlobalSearchScope
    }
}
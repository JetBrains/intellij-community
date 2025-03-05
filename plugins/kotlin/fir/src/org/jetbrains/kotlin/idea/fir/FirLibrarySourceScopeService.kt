// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.ProjectScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KaGlobalSearchScopeMerger
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibraryModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaScriptDependencyModule
import org.jetbrains.kotlin.analysis.api.projectStructure.directRegularDependenciesOfType
import org.jetbrains.kotlin.idea.base.projectStructure.LibraryDependenciesCache
import org.jetbrains.kotlin.idea.base.projectStructure.LibrarySourceScopeService
import org.jetbrains.kotlin.idea.base.projectStructure.getAssociatedKaModules
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.LibraryInfo
import org.jetbrains.kotlin.idea.base.projectStructure.toKaModule
import org.jetbrains.kotlin.idea.base.projectStructure.useNewK2ProjectStructureProvider
import org.jetbrains.kotlin.idea.base.util.K1ModeProjectStructureApi
import org.jetbrains.kotlin.platform.isCommon
import org.jetbrains.kotlin.utils.SmartList

@ApiStatus.Internal
class FirLibrarySourceScopeService(private val project: Project): LibrarySourceScopeService {
    override fun targetClassFilesToSourcesScopes(virtualFile: VirtualFile, project: Project): List<GlobalSearchScope> {
        val binaryModuleInfos = virtualFile.getAssociatedKaModules(project).filterIsInstance<KaLibraryModule>()

        val primaryScope = binaryModuleInfos.mapNotNull { it.librarySources?.contentScope }.union()
        val additionalScope = binaryModuleInfos.flatMap {
            it.associatedCommonLibraries() + it.sourcesOnlyDependencies()
        }.mapNotNull { it.librarySources?.contentScope }.union()

        return if (binaryModuleInfos.any { it is KaScriptDependencyModule }) {
            // NOTE: this is a workaround for https://github.com/gradle/gradle/issues/13783:
            // script configuration for *.gradle.kts files doesn't include sources for included plugins
            primaryScope + additionalScope + ProjectScope.getContentScope(project)
        } else {
            primaryScope + additionalScope
        }
    }

    private fun KaLibraryModule.associatedCommonLibraries(): List<KaLibraryModule> {
        if (targetPlatform.isCommon()) return emptyList()

        val result = SmartList<KaLibraryModule>()
        for (libraryDependency in directRegularDependenciesOfType<KaLibraryModule>()) {
            if (libraryDependency.targetPlatform.isCommon()) {
                result += libraryDependency
            }
        }
        return result
    }

    private fun KaLibraryModule.sourcesOnlyDependencies(): List<KaLibraryModule> {
        if (useNewK2ProjectStructureProvider) {
             // Library dependencies are not used in K2 mode
            return emptyList()
        } else {
            @OptIn(K1ModeProjectStructureApi::class)
            return LibraryDependenciesCache.getInstance(project).getLibraryDependencies(moduleInfo as LibraryInfo).sourcesOnlyDependencies
                .mapNotNull { it.toKaModule() as? KaLibraryModule }
        }
    }

    private fun Collection<GlobalSearchScope>.union(): List<GlobalSearchScope> =
        listOf(KaGlobalSearchScopeMerger.getInstance(project).union(this))
}
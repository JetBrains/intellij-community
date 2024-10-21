// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.ProjectScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.base.projectStructure.LibraryDependenciesCache
import org.jetbrains.kotlin.idea.base.projectStructure.ModuleInfoProvider
import org.jetbrains.kotlin.idea.base.projectStructure.collectLibraryBinariesModuleInfos
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.BinaryModuleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.LibraryInfo
import org.jetbrains.kotlin.idea.base.scripting.projectStructure.ScriptDependenciesInfo
import org.jetbrains.kotlin.idea.base.projectStructure.LibrarySourceScopeService
import org.jetbrains.kotlin.idea.base.util.K1ModeProjectStructureApi
import org.jetbrains.kotlin.platform.isCommon
import org.jetbrains.kotlin.utils.SmartList

@ApiStatus.Internal
@OptIn(K1ModeProjectStructureApi::class)
class FirLibrarySourceScopeService: LibrarySourceScopeService {
    override fun targetClassFilesToSourcesScopes(virtualFile: VirtualFile, project: Project): List<GlobalSearchScope> {
        val binaryModuleInfos = ModuleInfoProvider.getInstance(project)
            .collectLibraryBinariesModuleInfos(virtualFile)
            .toList()

        val primaryScope = binaryModuleInfos.mapNotNull { it.sourcesModuleInfo?.sourceScope() }.union()
        val additionalScope = binaryModuleInfos.flatMap {
            it.associatedCommonLibraries() + it.sourcesOnlyDependencies()
        }.mapNotNull { it.sourcesModuleInfo?.sourceScope() }.union()

        return if (binaryModuleInfos.any { it is ScriptDependenciesInfo }) {
            // NOTE: this is a workaround for https://github.com/gradle/gradle/issues/13783:
            // script configuration for *.gradle.kts files doesn't include sources for included plugins
            primaryScope + additionalScope + ProjectScope.getContentScope(project)
        } else {
            primaryScope + additionalScope
        }
    }

    private fun BinaryModuleInfo.associatedCommonLibraries(): List<BinaryModuleInfo> {
        if (platform.isCommon()) return emptyList()

        val result = SmartList<BinaryModuleInfo>()
        val dependencies = dependencies()
        for (ideaModuleInfo in dependencies) {
            if (ideaModuleInfo is BinaryModuleInfo && ideaModuleInfo.platform.isCommon()) {
                result += ideaModuleInfo
            }
        }
        return result
    }

    private fun BinaryModuleInfo.sourcesOnlyDependencies(): List<BinaryModuleInfo> {
        if (this !is LibraryInfo) return emptyList()

        return LibraryDependenciesCache.getInstance(project).getLibraryDependencies(this).sourcesOnlyDependencies
    }

    private fun Collection<GlobalSearchScope>.union(): List<GlobalSearchScope> =
        if (this.isNotEmpty()) listOf(GlobalSearchScope.union(this)) else emptyList()

}
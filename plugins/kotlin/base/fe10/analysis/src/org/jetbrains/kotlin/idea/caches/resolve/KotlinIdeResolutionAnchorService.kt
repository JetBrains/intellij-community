// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.caches.resolve

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analyzer.moduleInfo
import org.jetbrains.kotlin.caches.resolve.KotlinCacheService
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.base.fe10.analysis.ResolutionAnchorCacheService
import org.jetbrains.kotlin.idea.base.projectStructure.libraryToSourceAnalysis.useLibraryToSourceAnalysis
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.SourceForBinaryModuleInfo
import org.jetbrains.kotlin.resolve.ResolutionAnchorProvider

/**
 * This component provides capabilities for correct highlighting for projects with source-dependent libraries.
 * The issue with this kind of libraries is that their declarations are resolved by ResolverForProject
 * that have no access to project sources by itself. The necessary path back to project sources can be provided
 * manually for the libraries in project via resolution anchors. Anchor by itself is a source module which is mapped
 * to a library and used during resolution as a fallback.
 */
class KotlinIdeResolutionAnchorService(
    val project: Project
) : ResolutionAnchorProvider {
    override fun getResolutionAnchor(moduleDescriptor: ModuleDescriptor): ModuleDescriptor? {
        if (!project.useLibraryToSourceAnalysis) return null

        val moduleToAnchor = ResolutionAnchorCacheService.getInstance(project).resolutionAnchorsForLibraries
        val moduleInfo = moduleDescriptor.moduleInfo ?: return null
        val keyModuleInfo = if (moduleInfo is SourceForBinaryModuleInfo) moduleInfo.binariesModuleInfo else moduleInfo
        val mapped = moduleToAnchor[keyModuleInfo] ?: return null

        return KotlinCacheService.getInstance(project)
            .getResolutionFacadeByModuleInfo(mapped, mapped.platform)
            ?.moduleDescriptor
    }
}

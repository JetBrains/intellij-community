// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure.libraryToSourceAnalysis

import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.LibraryInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.ModuleSourceInfo

interface ResolutionAnchorCacheService {
    val resolutionAnchorsForLibraries: Map<LibraryInfo, ModuleSourceInfo>

    val librariesForResolutionAnchors: Map<ModuleSourceInfo, List<LibraryInfo>>

    /**
    See [org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinAnchorModuleProvider.getAllAnchorModulesIfComputed] for KDoc
     */
    val librariesForResolutionAnchorsIfComputed: Map<ModuleSourceInfo, List<LibraryInfo>>?

    /**
     * Anchor module is a source module that could replace library.
     *
     * Let's say:
     * Module1 (M1) has dependencies Library1 (L1) and Library2 (L2)
     * Module2 (M2) has dependencies Library2 (L2) and Library3 (L3)
     * and Module3 (M3) is an anchor module for `L3`.
     *
     * Current project model does NOT support library dependencies.
     * For those purposes some kind of approximation is used:
     * Library dependencies means other libraries those used in the same module.
     *
     * For any of `L1`, `L2` or `L3` method returns [`M3`] as those libraries have
     * direct or transitive `dependency` on `L3` that has anchor module `M3`.
     */
    fun getDependencyResolutionAnchors(libraryInfo: LibraryInfo): Set<ModuleSourceInfo>

    companion object {
        val Empty = object : ResolutionAnchorCacheService {
            override val resolutionAnchorsForLibraries: Map<LibraryInfo, ModuleSourceInfo> get() = emptyMap()
            override val librariesForResolutionAnchors: Map<ModuleSourceInfo, List<LibraryInfo>> get() = emptyMap()
            override val librariesForResolutionAnchorsIfComputed: Map<ModuleSourceInfo, List<LibraryInfo>> get() = emptyMap()
            override fun getDependencyResolutionAnchors(libraryInfo: LibraryInfo): Set<ModuleSourceInfo> = emptySet()
        }

        fun getInstance(project: Project): ResolutionAnchorCacheService {
            return project.serviceOrNull() ?: Empty
        }
    }
}
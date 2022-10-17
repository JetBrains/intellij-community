// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure.libraryToSourceAnalysis

import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.LibraryInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.ModuleSourceInfo

interface ResolutionAnchorCacheService {
    val resolutionAnchorsForLibraries: Map<LibraryInfo, ModuleSourceInfo>

    fun getDependencyResolutionAnchors(libraryInfo: LibraryInfo): Set<ModuleSourceInfo>

    companion object {
        val Empty = object : ResolutionAnchorCacheService {
            override val resolutionAnchorsForLibraries: Map<LibraryInfo, ModuleSourceInfo> get() = emptyMap()
            override fun getDependencyResolutionAnchors(libraryInfo: LibraryInfo): Set<ModuleSourceInfo> = emptySet()
        }

        fun getInstance(project: Project): ResolutionAnchorCacheService {
            return project.serviceOrNull() ?: Empty
        }
    }
}
// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fe10.analysisApiPlatform

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import org.jetbrains.kotlin.base.fe10.analysis.ResolutionAnchorCacheService
import org.jetbrains.kotlin.idea.base.projectStructure.KotlinModificationTrackerProvider
import org.jetbrains.kotlin.idea.base.projectStructure.ProjectStructureProviderService
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.LibraryInfo
import org.jetbrains.kotlin.idea.base.projectStructure.libraryToSourceAnalysis.useLibraryToSourceAnalysis

internal class K1IdeProjectStructureProviderService(private val project: Project) : ProjectStructureProviderService {
    override fun createLibraryModificationTracker(libraryInfo: LibraryInfo): ModificationTracker {
        return if (!project.useLibraryToSourceAnalysis) {
            ModificationTracker.NEVER_CHANGED
        } else {
            ResolutionAnchorAwareLibraryModificationTracker(libraryInfo)
        }
    }
}

private class ResolutionAnchorAwareLibraryModificationTracker(libraryInfo: LibraryInfo) : ModificationTracker {
    private val dependencyModules: List<Module> = if (!libraryInfo.isDisposed) {
        ResolutionAnchorCacheService.getInstance(libraryInfo.project)
            .getDependencyResolutionAnchors(libraryInfo)
            .map { it.module }
    } else {
        emptyList()
    }

    override fun getModificationCount(): Long {
        if (dependencyModules.isEmpty()) {
            return ModificationTracker.NEVER_CHANGED.modificationCount
        }

        val project = dependencyModules.first().project
        val modificationTrackerProvider = KotlinModificationTrackerProvider.getInstance(project)

        return dependencyModules
            .maxOfOrNull(modificationTrackerProvider::getModuleSelfModificationCount)
            ?: ModificationTracker.NEVER_CHANGED.modificationCount
    }
}
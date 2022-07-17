// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.analysisApiProviders

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.SimpleModificationTracker
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.analysis.project.structure.KtSourceModule
import org.jetbrains.kotlin.analysis.providers.KotlinModificationTrackerFactory
import org.jetbrains.kotlin.idea.base.projectStructure.ideaModule
import org.jetbrains.kotlin.idea.caches.project.LibraryModificationTracker

internal class FirIdeKotlinModificationTrackerFactory(private val project: Project) : KotlinModificationTrackerFactory() {
    override fun createProjectWideOutOfBlockModificationTracker(): ModificationTracker {
        return KotlinFirOutOfBlockModificationTracker(project)
    }

    override fun createModuleWithoutDependenciesOutOfBlockModificationTracker(module: KtSourceModule): ModificationTracker {
        return KotlinFirOutOfBlockModuleModificationTracker(module.ideaModule)
    }

    override fun createLibrariesModificationTracker(): ModificationTracker {
        return LibraryModificationTracker.getInstance(project)
    }

    @TestOnly
    override fun incrementModificationsCount() {
      (createLibrariesModificationTracker() as SimpleModificationTracker).incModificationCount()
        project.getService(FirIdeModificationTrackerService::class.java).increaseModificationCountForAllModules()
    }
}

private class KotlinFirOutOfBlockModificationTracker(project: Project) : ModificationTracker {
    private val trackerService = project.getService(FirIdeModificationTrackerService::class.java)

    override fun getModificationCount(): Long {
        return trackerService.projectGlobalOutOfBlockInKotlinFilesModificationCount
    }
}

private class KotlinFirOutOfBlockModuleModificationTracker(private val module: Module) : ModificationTracker {
    private val trackerService = module.project.getService(FirIdeModificationTrackerService::class.java)

    override fun getModificationCount(): Long {
        return trackerService.getOutOfBlockModificationCountForModules(module)
    }
}
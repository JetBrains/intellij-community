// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.analysis.providers.ide

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.SimpleModificationTracker
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.analysis.project.structure.KtSourceModule
import org.jetbrains.kotlin.analysis.providers.KotlinModificationTrackerFactory
import org.jetbrains.kotlin.analysis.providers.ide.trackers.KotlinFirModificationTrackerService
import org.jetbrains.kotlin.idea.caches.project.LibraryModificationTracker
import org.jetbrains.kotlin.idea.fir.analysis.project.structure.ideaModule

internal class KotlinFirIdeModificationTrackerFactory(private val project: Project) : KotlinModificationTrackerFactory() {
    override fun createProjectWideOutOfBlockModificationTracker(): ModificationTracker =
        KotlinFirOutOfBlockModificationTracker(project)

    override fun createModuleWithoutDependenciesOutOfBlockModificationTracker(module: KtSourceModule): ModificationTracker {
        return KotlinFirOutOfBlockModuleModificationTracker(module.ideaModule)
    }

    override fun createLibrariesModificationTracker(): ModificationTracker {
        return LibraryModificationTracker.getInstance(project)
    }

    @TestOnly
    override fun incrementModificationsCount() {
        (createLibrariesModificationTracker() as SimpleModificationTracker).incModificationCount()
        project.getService(KotlinFirModificationTrackerService::class.java).increaseModificationCountForAllModules()
    }
}

private class KotlinFirOutOfBlockModificationTracker(project: Project) : ModificationTracker {
    private val trackerService = project.getService(KotlinFirModificationTrackerService::class.java)

    override fun getModificationCount(): Long =
        trackerService.projectGlobalOutOfBlockInKotlinFilesModificationCount
}

private class KotlinFirOutOfBlockModuleModificationTracker(private val module: Module) : ModificationTracker {
    private val trackerService = module.project.getService(KotlinFirModificationTrackerService::class.java)

    override fun getModificationCount(): Long =
        trackerService.getOutOfBlockModificationCountForModules(module)
}
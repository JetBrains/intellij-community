// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.low.level.api.trackers

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.SimpleModificationTracker
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.analyzer.ModuleSourceInfoBase
import org.jetbrains.kotlin.idea.caches.project.LibraryModificationTracker
import org.jetbrains.kotlin.idea.caches.project.ModuleSourceInfo
import org.jetbrains.kotlin.idea.fir.low.level.api.api.KotlinOutOfBlockModificationTrackerFactory

class KotlinFirOutOfBlockModificationTrackerFactory(private val project: Project) : KotlinOutOfBlockModificationTrackerFactory() {
    override fun createProjectWideOutOfBlockModificationTracker(): ModificationTracker =
        KotlinFirOutOfBlockModificationTracker(project)

    override fun createModuleWithoutDependenciesOutOfBlockModificationTracker(moduleInfo: ModuleSourceInfoBase): ModificationTracker {
        require(moduleInfo is ModuleSourceInfo)
        return KotlinFirOutOfBlockModuleModificationTracker(moduleInfo.module)
    }

    override fun createLibraryOutOfBlockModificationTracker(): ModificationTracker {
        return LibraryModificationTracker.getInstance(project)
    }

    @TestOnly
    override fun incrementModificationsCount() {
        (createLibraryOutOfBlockModificationTracker() as SimpleModificationTracker).incModificationCount()
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
// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.fir.analysisApiProviders

import com.intellij.openapi.Disposable
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.SimpleModificationTracker
import org.jetbrains.kotlin.analysis.api.fir.utils.createCompositeModificationTracker
import java.util.concurrent.ConcurrentHashMap

internal class FirIdeModificationTrackerService(val project : Project) : Disposable {
    /**
     * A project-wide out-of-block modification tracker for Kotlin sources. The modification count of this tracker will be incremented on
     * global PSI tree changes or for test purposes.
     */
    val projectOutOfBlockModificationTracker: SimpleModificationTracker = SimpleModificationTracker()

    /**
     * A module modification tracker is used by dependents which want to depend on *specific* module changes. This modification tracker for
     * *all* modules gives us the option to force such an update for all source modules.
     *
     * That functionality is distinct from [projectOutOfBlockModificationTracker], because a dependent will explicitly use a project OOB
     * tracker if it wants to depend on *any* OOB modification in the project.
     */
    private val allModulesOutOfBlockModificationTracker: SimpleModificationTracker = SimpleModificationTracker()

    private val moduleOutOfBlockModificationTrackerWrappers = ConcurrentHashMap<Module, ModuleOutOfBlockModificationTrackerWrapper>()

    fun getModuleOutOfBlockModificationTracker(module: Module): ModificationTracker =
      getModuleOutOfBlockModificationTrackerWrapper(module).compositeModificationTracker

    private fun getModuleOutOfBlockModificationTrackerWrapper(module: Module): ModuleOutOfBlockModificationTrackerWrapper =
        moduleOutOfBlockModificationTrackerWrappers.computeIfAbsent(module) {
            ModuleOutOfBlockModificationTrackerWrapper(
                allModulesOutOfBlockModificationTracker,
                SimpleModificationTracker(),
            )
        }

    override fun dispose() {}

    fun increaseModificationCountForAllModules() {
        projectOutOfBlockModificationTracker.incModificationCount()
        allModulesOutOfBlockModificationTracker.incModificationCount()
    }

    fun increaseModificationCountForModule(module: Module) {
        getModuleOutOfBlockModificationTrackerWrapper(module).moduleModificationTracker.incModificationCount()
    }

    fun increaseModificationCountForModuleAndProject(module: Module?) {
        if (module != null) {
            increaseModificationCountForModule(module)
        }
        projectOutOfBlockModificationTracker.incModificationCount()
    }
}

/**
 * The module's out-of-block modification tracker is a _composite_ modification tracker because it is made up of both the specific module's
 * tracker and the tracker for _all_ modules. Should these trackers be flattened into another composite modification tracker, the
 * "all modules" modification tracker will only be contained once in the resulting modification tracker, because modification trackers are
 * flattened uniquely by identity and there must only be a single instance of the "all modules" modification tracker per project.
 */
internal class ModuleOutOfBlockModificationTrackerWrapper(
    allModulesModificationTracker: ModificationTracker,
    val moduleModificationTracker: SimpleModificationTracker,
) {
    val compositeModificationTracker = listOf(allModulesModificationTracker, moduleModificationTracker).createCompositeModificationTracker()
}

// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.fir.analysisApiProviders

import com.intellij.openapi.Disposable
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

internal class FirIdeModificationTrackerService(val project : Project) : Disposable {
    private val _projectGlobalOutOfBlockInKotlinFilesModificationCount = AtomicLong()
    val projectGlobalOutOfBlockInKotlinFilesModificationCount: Long
        get() = _projectGlobalOutOfBlockInKotlinFilesModificationCount.get()

    fun getOutOfBlockModificationCountForModules(module: Module): Long =
        moduleModificationsState.getModificationsCountForModule(module)

    private val moduleModificationsState = ModuleModificationsState()

    override fun dispose() {}

    fun increaseModificationCountForAllModules() {
        _projectGlobalOutOfBlockInKotlinFilesModificationCount.incrementAndGet()
        moduleModificationsState.increaseModificationCountForAllModules()
    }

    @TestOnly
    fun increaseModificationCountForModule(module: Module) {
        moduleModificationsState.increaseModificationCountForModule(module)
    }
    
    fun increaseModificationCountForModuleAndProject(module: Module?) {
        if (module != null) {
            moduleModificationsState.increaseModificationCountForModule(module)
        }
        _projectGlobalOutOfBlockInKotlinFilesModificationCount.incrementAndGet()
    }
}

private class ModuleModificationsState {
    private val modificationCountForModule = ConcurrentHashMap<Module, ModuleModifications>()
    private val state = AtomicLong()

    fun getModificationsCountForModule(module: Module) = modificationCountForModule.compute(module) { _, modifications ->
        val currentState = state.get()
        when {
            modifications == null -> ModuleModifications(0, currentState)
            modifications.state == currentState -> modifications
            else -> ModuleModifications(modificationsCount = modifications.modificationsCount + 1, state = currentState)
        }
    }!!.modificationsCount

    fun increaseModificationCountForAllModules() {
        state.incrementAndGet()
    }

    fun increaseModificationCountForModule(module: Module) {
        modificationCountForModule.compute(module) { _, modifications ->
            val currentState = state.get()
            when (modifications) {
                null -> ModuleModifications(0, currentState)
                else -> ModuleModifications(modifications.modificationsCount + 1, currentState)
            }
        }
    }

    private data class ModuleModifications(val modificationsCount: Long, val state: Long)
}
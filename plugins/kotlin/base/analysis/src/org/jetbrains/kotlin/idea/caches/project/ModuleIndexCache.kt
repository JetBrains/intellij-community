// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.caches.project

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.ModuleSourceInfo
import org.jetbrains.kotlin.idea.base.projectStructure.sourceModuleInfos
import org.jetbrains.kotlin.idea.base.projectStructure.testSourceInfo
import org.jetbrains.kotlin.idea.base.util.K1ModeProjectStructureApi
import org.jetbrains.kotlin.idea.caches.project.*
import org.jetbrains.kotlin.idea.caches.trackers.ModuleModificationTracker
import java.util.*

//NOTE: this is an approximation that may contain more module infos than the exact solution
@K1ModeProjectStructureApi
fun ModuleSourceInfo.getDependentModules(): Set<ModuleSourceInfo> {
    val dependents = getDependents(module)
    @Suppress("DEPRECATION")
    return when (sourceType) {
        SourceType.TEST -> dependents.mapNotNullTo(HashSet<ModuleSourceInfo>()) { it.testSourceInfo }
        SourceType.PRODUCTION -> dependents.flatMapTo(HashSet<ModuleSourceInfo>()) { it.sourceModuleInfos }
    }
}

//NOTE: getDependents adapted from com.intellij.openapi.module.impl.scopes.ModuleWithDependentsScope#buildDependents()
private fun getDependents(module: Module): Set<Module> {
    val result = HashSet<Module>()
    result.add(module)

    val processedExporting = HashSet<Module>()

    val index = getModuleIndex(module.project)

    val walkingQueue = ArrayDeque<Module>(10)
    walkingQueue.addLast(module)

    while (true) {
        val current = walkingQueue.pollFirst() ?: break
        processedExporting.add(current)
        result.addAll(index.plainUsages(current))
        for (dependent in index.exportingUsages(current)) {
            result.add(dependent)
            if (processedExporting.add(dependent)) {
                walkingQueue.addLast(dependent)
            }
        }
    }
    return result
}

private interface ModuleIndex {

    fun plainUsages(module: Module): Collection<Module>

    fun exportingUsages(module: Module): Collection<Module>
}

private class ModuleIndexImpl(
    private val plainUsages: MultiMap<Module, Module>,
    private val exportingUsages: MultiMap<Module, Module>,
) : ModuleIndex {
    override fun plainUsages(module: Module): Collection<Module> = plainUsages[module]

    override fun exportingUsages(module: Module): Collection<Module> = exportingUsages[module]
}

private fun getModuleIndex(project: Project): ModuleIndex = CachedValuesManager.getManager(project).getCachedValue(project) {
    val plainUsages: MultiMap<Module, Module> = MultiMap.create()
    val exportingUsages: MultiMap<Module, Module> = MultiMap.create()

    runReadAction {
        for (module in ModuleManager.getInstance(project).modules) {
            ProgressManager.checkCanceled()
            for (orderEntry in ModuleRootManager.getInstance(module).orderEntries) {
                if (orderEntry !is ModuleOrderEntry) continue
                val referenced = orderEntry.module ?: continue
                val map = if (orderEntry.isExported) exportingUsages else plainUsages
                map.putValue(referenced, module)
            }
        }
    }

    CachedValueProvider.Result(
        ModuleIndexImpl(plainUsages = plainUsages, exportingUsages = exportingUsages),
        ModuleModificationTracker.getInstance(project)
    )
}

// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.caches.project

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.containers.MultiMap
import java.util.*

//NOTE: this is an approximation that may contain more module infos than the exact solution
fun ModuleSourceInfo.getDependentModules(): Set<ModuleSourceInfo> {
    val dependents = getDependents(module)
    return when (sourceType) {
        SourceType.TEST -> dependents.mapNotNullTo(HashSet<ModuleSourceInfo>(), Module::testSourceInfo)
        SourceType.PRODUCTION -> dependents.flatMapTo(HashSet<ModuleSourceInfo>()) { it.correspondingModuleInfos() }
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
        result.addAll(index.plainUsages[current])
        for (dependent in index.exportingUsages[current]) {
            result.add(dependent)
            if (processedExporting.add(dependent)) {
                walkingQueue.addLast(dependent)
            }
        }
    }
    return result
}

private class ModuleIndex {
    val plainUsages = MultiMap.create<Module, Module>()
    val exportingUsages = MultiMap.create<Module, Module>()
}

private fun getModuleIndex(project: Project): ModuleIndex {
    return CachedValuesManager.getManager(project).getCachedValue(project) {
        val index = ModuleIndex()
        for (module in ModuleManager.getInstance(project).modules) {
            for (orderEntry in ModuleRootManager.getInstance(module).orderEntries) {
                if (orderEntry is ModuleOrderEntry) {
                    val referenced = orderEntry.module
                    if (referenced != null) {
                        val map = if (orderEntry.isExported) index.exportingUsages else index.plainUsages
                        map.putValue(referenced, module)
                    }
                }
            }
        }
        CachedValueProvider.Result(index, ProjectRootModificationTracker.getInstance(project))
    }!!
}

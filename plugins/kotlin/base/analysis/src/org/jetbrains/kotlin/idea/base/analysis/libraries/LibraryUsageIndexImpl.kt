// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.analysis.libraries

import com.intellij.java.library.JavaLibraryModificationTracker
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.libraries.Library
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.idea.base.facet.isHMPPEnabled
import org.jetbrains.kotlin.idea.base.projectStructure.LibraryInfoCache
import org.jetbrains.kotlin.idea.base.projectStructure.LibraryUsageIndex
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.LibraryInfo
import org.jetbrains.kotlin.idea.base.util.K1ModeProjectStructureApi
import org.jetbrains.kotlin.idea.caches.project.canDependOn
import org.jetbrains.kotlin.idea.caches.project.getIdeaModelInfosCache
import org.jetbrains.kotlin.idea.caches.trackers.ModuleModificationTracker
import org.jetbrains.kotlin.progress.ProgressIndicatorAndCompilationCanceledStatus.checkCanceled

@K1ModeProjectStructureApi
class LibraryUsageIndexImpl(private val project: Project) : LibraryUsageIndex {
    private val moduleDependentsByLibrary: CachedValue<MultiMap<Library, Module>> =
        CachedValuesManager.getManager(project).createCachedValue {
            CachedValueProvider.Result(
                computeLibraryModuleDependents(),
                ModuleModificationTracker.getInstance(project),
                JavaLibraryModificationTracker.getInstance(project),
            )
        }

    override fun getDependentModules(libraryInfo: LibraryInfo): Sequence<Module> = sequence<Module> {
        val ideaModelInfosCache = getIdeaModelInfosCache(project)
        for (module in moduleDependentsByLibrary.value[libraryInfo.library]) {
            val mappedModuleInfos = ideaModelInfosCache.getModuleInfosForModule(module)
            if (mappedModuleInfos.any { it.platform.canDependOn(libraryInfo, module.isHMPPEnabled) }) {
                yield(module)
            }
        }
    }

    override fun hasDependentModule(libraryInfo: LibraryInfo, module: Module): Boolean {
        // TODO: probably it is not fully true in the case of hmpp (as in getDependentModules)
        // but we accept it as a more performant
        return module in moduleDependentsByLibrary.value[libraryInfo.library]
    }

    private fun computeLibraryModuleDependents(): MultiMap<Library, Module> = runReadAction {
        val moduleDependentsByLibrary = MultiMap.createSet<Library, Module>()
        val libraryCache = LibraryInfoCache.getInstance(project)
        for (module in ModuleManager.getInstance(project).modules) {
            checkCanceled()
            for (entry in ModuleRootManager.getInstance(module).orderEntries) {
                if (entry !is LibraryOrderEntry) continue
                val library = entry.library ?: continue
                val keyLibrary = libraryCache.deduplicatedLibrary(library)
                moduleDependentsByLibrary.putValue(keyLibrary, module)
            }
        }

        moduleDependentsByLibrary
    }
}

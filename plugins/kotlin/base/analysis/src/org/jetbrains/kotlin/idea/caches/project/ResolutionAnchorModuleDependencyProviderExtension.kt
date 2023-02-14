// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.caches.project

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.util.CommonProcessors
import com.intellij.util.Processor
import org.jetbrains.kotlin.idea.base.projectStructure.LibraryInfoCache
import org.jetbrains.kotlin.idea.base.projectStructure.libraryToSourceAnalysis.ResolutionAnchorCacheService
import org.jetbrains.kotlin.idea.base.projectStructure.libraryToSourceAnalysis.useLibraryToSourceAnalysis
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.ModuleSourceInfo
import org.jetbrains.kotlin.idea.caches.trackers.ModuleDependencyProviderExtension
import org.jetbrains.kotlin.progress.ProgressIndicatorAndCompilationCanceledStatus.checkCanceled

class ResolutionAnchorModuleDependencyProviderExtension(private val project: Project) : ModuleDependencyProviderExtension {
    @Deprecated(
        "Use #processAdditionalDependencyModules",
        replaceWith = ReplaceWith("processAdditionalDependencyModules(module, processor)"),
    )
    override fun getAdditionalDependencyModules(module: Module): Collection<Module> {
        val modules = HashSet<Module>()
        processAdditionalDependencyModules(module, CommonProcessors.CollectProcessor(modules))
        return modules
    }

    /**
     * Consider modules M1, M2, M3, library L1 resolving via Resolution anchor M2, other libraries L2, L3 with the following dependencies:
     * M2 depends on M1
     * L1 depends on anchor M2
     * L2 depends on L1
     * L3 depends on L2
     * M3 depends on L3
     * Then modification of M1 should lead to complete invalidation of all modules and libraries in this example.
     *
     * Updates for libraries aren't managed here, corresponding ModificationTracker is responsible for that.
     * This extension provides missing dependencies from source-dependent library dependencies only to source modules.
     */
    override fun processAdditionalDependencyModules(module: Module, processor: Processor<Module>) {
        if (!project.useLibraryToSourceAnalysis) return

        val resolutionAnchorDependencies = HashSet<ModuleSourceInfo>()
        val libraryInfoCache = LibraryInfoCache.getInstance(project)
        val anchorCacheService = ResolutionAnchorCacheService.getInstance(project)
        ModuleRootManager.getInstance(module).orderEntries().recursively().forEachLibrary { library ->
            libraryInfoCache[library].flatMapTo(resolutionAnchorDependencies) { libraryInfo ->
                checkCanceled()
                anchorCacheService.getDependencyResolutionAnchors(libraryInfo)
            }

            true
        }

        for (anchorModule in resolutionAnchorDependencies) {
            ModuleRootManager.getInstance(anchorModule.module).orderEntries().recursively().forEachModule(processor)
        }
    }
}

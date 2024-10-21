// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.analysisApiPlatform

import com.intellij.java.library.JavaLibraryModificationTracker
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.platform.workspace.jps.entities.LibraryId
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.LibraryBridge
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinAnchorModuleProvider
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibraryModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaSourceModule
import org.jetbrains.kotlin.idea.base.analysisApiPlatform.IdeKotlinAnchorModuleProvider
import org.jetbrains.kotlin.idea.base.analysisApiPlatform.IdeKotlinModuleDependentsProvider
import org.jetbrains.kotlin.idea.base.projectStructure.KtLibraryModuleByModuleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.LibraryInfoCache
import org.jetbrains.kotlin.idea.base.projectStructure.symbolicId
import org.jetbrains.kotlin.idea.base.projectStructure.toKaSourceModuleForProductionOrTest
import org.jetbrains.kotlin.idea.base.util.K1ModeProjectStructureApi
import org.jetbrains.kotlin.idea.caches.trackers.ModuleModificationTracker
import org.jetbrains.kotlin.progress.ProgressIndicatorAndCompilationCanceledStatus.checkCanceled
import org.jetbrains.kotlin.utils.addIfNotNull

internal class K2IdeKotlinModuleDependentsProvider(project: Project) : IdeKotlinModuleDependentsProvider(project) {
    override fun addAnchorModuleDependents(
        module: KaSourceModule,
        to: MutableSet<KaModule>
    ) {
        val kotlinAnchorModuleProvider = KotlinAnchorModuleProvider.getInstance(project) as IdeKotlinAnchorModuleProvider
        val anchorLibraries = kotlinAnchorModuleProvider.getAnchorLibraries(module).ifEmpty { return }
        for (library in anchorLibraries) {
            to.add(library)
            to.addIfNotNull(library.librarySources)
        }
    }

    @OptIn(K1ModeProjectStructureApi::class)
    override fun getDirectDependentsForLibraryNonSdkModule(module: KaLibraryModule): Set<KaModule> {
        require(module is KtLibraryModuleByModuleInfo)

        return K2LibraryUsageIndex.getInstance(project)
            .getDependentModules(module.symbolicId)
            .mapNotNullTo(mutableSetOf()) { it.toKaSourceModuleForProductionOrTest(project) }
    }
}

@Service(Service.Level.PROJECT)
private class K2LibraryUsageIndex(private val project: Project) {
    private val moduleDependentsByLibrary: CachedValue<Map<LibraryId, List<ModuleId>>> =
        CachedValuesManager.getManager(project).createCachedValue {
            CachedValueProvider.Result(
                computeLibraryModuleDependents(),
                ModuleModificationTracker.getInstance(project),
                JavaLibraryModificationTracker.getInstance(project),
            )
        }

    fun getDependentModules(libraryId: LibraryId): List<ModuleId> {
        return moduleDependentsByLibrary.value[libraryId] ?: emptyList()
    }

    private fun computeLibraryModuleDependents(): Map<LibraryId, List<ModuleId>> {
        @OptIn(K1ModeProjectStructureApi::class)
        val libraryInfoCache = LibraryInfoCache.getInstance(project)

        val result = mutableMapOf<LibraryId, MutableList<ModuleId>>()
        for (module in ModuleManager.getInstance(project).modules) {
            check(module is ModuleBridge)
            checkCanceled()
            for (entry in ModuleRootManager.getInstance(module).orderEntries) {
                if (entry !is LibraryOrderEntry) continue

                // We still use LibraryInfoCache here for deduplication.
                // The next steps will be to implement KaModule without IdeaModuleInfo and try to get rid of the deduplication logic
                @OptIn(K1ModeProjectStructureApi::class)
                val library = entry.library?.let { libraryInfoCache.deduplicatedLibrary(it)  } ?: continue
                check(library is LibraryBridge)
                result.getOrPut(library.libraryId) { mutableListOf() } += module.moduleEntityId
            }
        }

        return result
    }

    companion object {
        fun getInstance(project: Project): K2LibraryUsageIndex =
            project.service()
    }
}


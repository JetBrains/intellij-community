// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.analysisApiPlatform

import com.intellij.java.library.JavaLibraryModificationTracker
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.libraries.Library
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibraryModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaSourceModule
import org.jetbrains.kotlin.idea.base.analysisApiPlatform.IdeKotlinAnchorModuleProvider
import org.jetbrains.kotlin.idea.base.projectStructure.getLibraryModules
import org.jetbrains.kotlin.idea.base.projectStructure.getMainKtSourceModule
import org.jetbrains.kotlin.idea.caches.resolve.util.ResolutionAnchorCacheState
import org.jetbrains.kotlin.idea.caches.trackers.ModuleModificationTracker

@ApiStatus.Internal
class K2IdeKotlinAnchorModuleProvider(val project: Project) : IdeKotlinAnchorModuleProvider {
    override fun getAnchorModule(libraryModule: KaLibraryModule): KaSourceModule? {
        return anchorMapping.anchorByLibrary[libraryModule]
    }

    override fun getAllAnchorModules(): Collection<KaSourceModule> {
        return anchorMapping.librariesByAnchor.keys
    }

    override fun getAllAnchorModulesIfComputed(): Collection<KaSourceModule>? {
        val mapping = anchorMappingCachedValue.upToDateOrNull?.get() ?: return null
        return mapping.librariesByAnchor.keys
    }

    override fun getAnchorLibraries(libraryModule: KaSourceModule): List<KaLibraryModule> {
        return anchorMapping.librariesByAnchor[libraryModule] ?: emptyList()
    }

    @TestOnly
    fun setAnchors(mapping: Map<String, String>) {
        state.setAnchors(mapping)
    }

    private val state get() = ResolutionAnchorCacheState.getInstance(project)

    private val moduleNameToAnchorName get() = state.myState.moduleNameToAnchorName

    private class AnchorMapping(
        val anchorByLibrary: Map<KaLibraryModule, KaSourceModule>,
        val librariesByAnchor: Map<KaSourceModule, List<KaLibraryModule>>,
    )

    private val anchorMappingCachedValue = CachedValuesManager.getManager(project).createCachedValue(
        project,
        {
            CachedValueProvider.Result.create(
                createResolutionAnchorMapping(),
                ModuleModificationTracker.getInstance(project),
                JavaLibraryModificationTracker.getInstance(project),
            )
        },
        /* trackValue = */ false
    )

    private val anchorMapping: AnchorMapping
        get() = anchorMappingCachedValue.value


    private fun createResolutionAnchorMapping(): AnchorMapping {
        val moduleNameToAnchorName = moduleNameToAnchorName
        // Avoid loading all modules if the project defines no anchor mappings.
        if (moduleNameToAnchorName.isEmpty()) {
            return AnchorMapping(emptyMap(), emptyMap())
        }

        val moduleManager = ModuleManager.getInstance(project)

        val librariesByName = getLibrariesByName()


        val anchorByLibrary = mutableMapOf<KaLibraryModule, KaSourceModule>()
        val librariesByAnchor = mutableMapOf<KaSourceModule, MutableList<KaLibraryModule>>()

        moduleNameToAnchorName.entries.forEach { (libraryName, anchorName) ->
            val library: KaLibraryModule = librariesByName[libraryName]?.getLibraryModules(project)?.firstOrNull() ?: run {
                logger.warn("Resolution anchor mapping key doesn't point to a known library: $libraryName. Skipping this anchor")
                return@forEach
            }

            val anchor: KaSourceModule = moduleManager.findModuleByName(anchorName)?.getMainKtSourceModule() ?: run {
                logger.warn("Resolution anchor mapping value doesn't point to a source module: $anchorName. Skipping this anchor")
                return@forEach
            }

            anchorByLibrary.put(library, anchor)
            librariesByAnchor.getOrPut(anchor) { mutableListOf() }.add(library)
        }

        return AnchorMapping(anchorByLibrary, librariesByAnchor)
    }

    private fun getLibrariesByName(): Map<String, Library> = buildMap {
        for (module in ModuleManager.getInstance(project).modules) {
            ProgressManager.checkCanceled()
            for (entry in ModuleRootManager.getInstance(module).orderEntries) {
                if (entry !is LibraryOrderEntry) continue
                val library = entry.library ?: continue
                val name = library.name ?: continue
                put(name, library)
            }
        }

    }

    companion object {
        private val logger = logger<K2IdeKotlinAnchorModuleProvider>()
    }
}
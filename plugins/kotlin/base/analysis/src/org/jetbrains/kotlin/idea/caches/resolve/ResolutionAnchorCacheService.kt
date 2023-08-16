// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.caches.resolve

import com.intellij.java.library.JavaLibraryModificationTracker
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.xmlb.XmlSerializerUtil
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.LibraryDependenciesCache
import org.jetbrains.kotlin.idea.base.projectStructure.libraryToSourceAnalysis.ResolutionAnchorCacheService
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.LibraryInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.ModuleSourceInfo
import org.jetbrains.kotlin.idea.caches.project.getModuleInfosFromIdeaModel
import org.jetbrains.kotlin.idea.caches.trackers.ModuleModificationTracker
import org.jetbrains.kotlin.progress.ProgressIndicatorAndCompilationCanceledStatus.checkCanceled
import org.jetbrains.kotlin.types.typeUtil.closure
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import org.jetbrains.kotlin.idea.base.analysis.LibraryDependenciesCacheImpl.Companion.isSpecialKotlinCoreLibrary

@State(name = "KotlinIdeAnchorService", storages = [Storage("anchors.xml")])
class ResolutionAnchorCacheServiceImpl(
    val project: Project
) : ResolutionAnchorCacheService, PersistentStateComponent<ResolutionAnchorCacheServiceImpl.State> {
    data class State(
        var moduleNameToAnchorName: Map<String, String> = emptyMap()
    )

    @JvmField
    @Volatile
    var myState: State = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, myState)
    }

    @TestOnly
    fun setAnchors(mapping: Map<String, String>) {
        myState = State(mapping)
    }

    override val resolutionAnchorsForLibraries: Map<LibraryInfo, ModuleSourceInfo>
        get() =
            CachedValuesManager.getManager(project).getCachedValue(project) {
                CachedValueProvider.Result.create(
                  mapResolutionAnchorForLibraries(),
                  ModuleModificationTracker.getInstance(project),
                  JavaLibraryModificationTracker.getInstance(project)
                )
            }

    private val resolutionAnchorDependenciesCache: MutableMap<LibraryInfo, Set<ModuleSourceInfo>>
        get() =
            CachedValuesManager.getManager(project).getCachedValue(project) {
                CachedValueProvider.Result.create(
                  ContainerUtil.createConcurrentWeakMap(),
                  ModuleModificationTracker.getInstance(project),
                  JavaLibraryModificationTracker.getInstance(project)
                )
            }

    override fun getDependencyResolutionAnchors(libraryInfo: LibraryInfo): Set<ModuleSourceInfo> {
        resolutionAnchorDependenciesCache[libraryInfo]?.let {
            return it
        }

        val allTransitiveLibraryDependencies = with(LibraryDependenciesCache.getInstance(project)) {
            val directDependenciesOnLibraries = getLibraryDependencies(libraryInfo).libraries
            directDependenciesOnLibraries.closure { libraryDependency ->
                checkCanceled()
                getLibraryDependencies(libraryDependency).libraries
            }
        }

        val dependencyResolutionAnchors = allTransitiveLibraryDependencies.mapNotNullTo(mutableSetOf()) { resolutionAnchorsForLibraries[it] }
        resolutionAnchorDependenciesCache.putIfAbsent(libraryInfo, dependencyResolutionAnchors)?.let {
            // if value is already provided by the cache - no reasons for this thread to fill other values
            return it
        }

        val platform = libraryInfo.platform
        for (transitiveLibraryDependency in allTransitiveLibraryDependencies) {
            // it's safe to use same dependencyResolutionAnchors for the same platform libraries
            if (transitiveLibraryDependency.platform == platform && !transitiveLibraryDependency.isSpecialKotlinCoreLibrary(project)) {
                resolutionAnchorDependenciesCache.putIfAbsent(transitiveLibraryDependency, dependencyResolutionAnchors)
            }
        }

        return dependencyResolutionAnchors
    }

    private fun associateModulesByNames(): Map<String, ModuleInfo> {
        return getModuleInfosFromIdeaModel(project).associateBy { moduleInfo ->
            checkCanceled()
            when (moduleInfo) {
                is LibraryInfo -> moduleInfo.library.name ?: "" // TODO: when does library have no name?
                is ModuleSourceInfo -> moduleInfo.module.name
                else -> moduleInfo.name.asString()
            }
        }
    }

    private fun mapResolutionAnchorForLibraries(): Map<LibraryInfo, ModuleSourceInfo> {
        val modulesByNames: Map<String, ModuleInfo> = associateModulesByNames()

        return myState.moduleNameToAnchorName.entries.mapNotNull { (libraryName, anchorName) ->
            val library: LibraryInfo = modulesByNames[libraryName]?.safeAs<LibraryInfo>() ?: run {
                logger.warn("Resolution anchor mapping key doesn't point to a known library: $libraryName. Skipping this anchor")
                return@mapNotNull null
            }

            val anchor: ModuleSourceInfo = modulesByNames[anchorName]?.safeAs<ModuleSourceInfo>() ?: run {
                logger.warn("Resolution anchor mapping value doesn't point to a source module: $anchorName. Skipping this anchor")
                return@mapNotNull null
            }

            library to anchor
        }.toMap()
    }

    companion object {
        private val logger = logger<ResolutionAnchorCacheServiceImpl>()
    }
}

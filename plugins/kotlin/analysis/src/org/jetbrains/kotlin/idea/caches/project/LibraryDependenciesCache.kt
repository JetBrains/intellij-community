// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.caches.project

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.*
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.util.Condition
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.idea.core.util.CachedValue
import org.jetbrains.kotlin.idea.core.util.getValue
import org.jetbrains.kotlin.idea.klib.AbstractKlibLibraryInfo
import org.jetbrains.kotlin.platform.SimplePlatform
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.isCommon
import org.jetbrains.kotlin.platform.konan.NativePlatformUnspecifiedTarget
import org.jetbrains.kotlin.platform.konan.NativePlatformWithTarget
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

internal typealias LibrariesAndSdks = Pair<List<LibraryInfo>, List<SdkInfo>>

interface LibraryDependenciesCache {
    companion object {
        fun getInstance(project: Project):LibraryDependenciesCache = project.getServiceSafe()
    }

    fun getLibrariesAndSdksUsedWith(libraryInfo: LibraryInfo): LibrariesAndSdks
}

class LibraryDependenciesCacheImpl(private val project: Project) : LibraryDependenciesCache {
    private val cache by CachedValue(project) {
        CachedValueProvider.Result(
            ContainerUtil.createConcurrentWeakMap<LibraryInfo, LibrariesAndSdks>(),
            ProjectRootManager.getInstance(project)
        )
    }

    private val moduleDependenciesCache by CachedValue(project) {
        CachedValueProvider.Result(
            ContainerUtil.createConcurrentWeakMap<Module, Pair<Set<DependencyCandidate>, Set<SdkInfo>>>(),
            ProjectRootManager.getInstance(project)
        )
    }

    override fun getLibrariesAndSdksUsedWith(libraryInfo: LibraryInfo): LibrariesAndSdks =
        cache.getOrPut(libraryInfo) { computeLibrariesAndSdksUsedWith(libraryInfo) }

    private fun computeLibrariesAndSdksUsedWith(libraryInfo: LibraryInfo): LibrariesAndSdks {
        val (dependencyCandidates, sdks) = computeLibrariesAndSdksUsedWithNoFilter(libraryInfo)
        val chosenCompatibleCandidates = chooseCompatibleDependencies(libraryInfo.platform, dependencyCandidates)
        val libraries = chosenCompatibleCandidates.map { it.libraries }.flatten()
        return Pair(libraries, sdks.toList())
    }

    //NOTE: used LibraryRuntimeClasspathScope as reference
    private fun computeLibrariesAndSdksUsedWithNoFilter(libraryInfo: LibraryInfo): Pair<Set<DependencyCandidate>, Set<SdkInfo>> {
        val libraries = LinkedHashSet<DependencyCandidate>()
        val sdks = LinkedHashSet<SdkInfo>()

        for (module in getLibraryUsageIndex().getModulesLibraryIsUsedIn(libraryInfo)) {
            val (moduleLibraries, moduleSdks) = moduleDependenciesCache.getOrPut(module) {
                computeLibrariesAndSdksUsedIn(module)
            }

            libraries.addAll(moduleLibraries)
            sdks.addAll(moduleSdks)
        }

        return libraries to sdks
    }

    private fun computeLibrariesAndSdksUsedIn(module: Module): Pair<Set<DependencyCandidate>, Set<SdkInfo>> {
        val libraries = LinkedHashSet<DependencyCandidate>()
        val sdks = LinkedHashSet<SdkInfo>()

        val processedModules = HashSet<Module>()
        val condition = Condition<OrderEntry> { orderEntry ->
            orderEntry.safeAs<ModuleOrderEntry>()?.let {
                it.module?.run { this !in processedModules } ?: false
            } ?: true
        }

        ModuleRootManager.getInstance(module).orderEntries().recursively().satisfying(condition).process(object : RootPolicy<Unit>() {
            override fun visitModuleSourceOrderEntry(moduleSourceOrderEntry: ModuleSourceOrderEntry, value: Unit) {
                processedModules.add(moduleSourceOrderEntry.ownerModule)
            }

            override fun visitLibraryOrderEntry(libraryOrderEntry: LibraryOrderEntry, value: Unit) {
                libraryOrderEntry.library.safeAs<LibraryEx>()?.takeIf { !it.isDisposed }?.let {
                    libraries += createLibraryInfo(project, it).mapNotNull { libraryInfo ->
                        DependencyCandidate.fromLibraryOrNull(
                            project,
                            libraryInfo.library
                        )
                    }
                }
            }

            override fun visitJdkOrderEntry(jdkOrderEntry: JdkOrderEntry, value: Unit) {
                jdkOrderEntry.jdk?.let { jdk ->
                    sdks += SdkInfo(project, jdk)
                }
            }
        }, Unit)

        return libraries to sdks
    }

    private fun getLibraryUsageIndex(): LibraryUsageIndex {
        return CachedValuesManager.getManager(project).getCachedValue(project) {
            CachedValueProvider.Result(LibraryUsageIndex(), ProjectRootModificationTracker.getInstance(project))
        }!!
    }

    private inner class LibraryUsageIndex {
        val modulesLibraryIsUsedIn: MultiMap<Library, Module> = MultiMap.createSet()

        init {
            for (module in ModuleManager.getInstance(project).modules) {
                for (entry in ModuleRootManager.getInstance(module).orderEntries) {
                    if (entry is LibraryOrderEntry) {
                        val library = entry.library
                        if (library != null) {
                            modulesLibraryIsUsedIn.putValue(library, module)
                        }
                    }
                }
            }
        }

        fun getModulesLibraryIsUsedIn(libraryInfo: LibraryInfo) = sequence<Module> {
            val ideaModelInfosCache = getIdeaModelInfosCache(project)
            for (module in modulesLibraryIsUsedIn[libraryInfo.library]) {
                val mappedModuleInfos = ideaModelInfosCache.getModuleInfosForModule(module)
                if (mappedModuleInfos.any { it.platform.canDependOn(libraryInfo, module.isHMPPEnabled) }) {
                    yield(module)
                }
            }
        }
    }
}

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
import org.jetbrains.kotlin.idea.util.application.getServiceSafe

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

    override fun getLibrariesAndSdksUsedWith(libraryInfo: LibraryInfo): LibrariesAndSdks =
        cache.getOrPut(libraryInfo) { computeLibrariesAndSdksUsedWith(libraryInfo) }


    //NOTE: used LibraryRuntimeClasspathScope as reference
    private fun computeLibrariesAndSdksUsedWith(libraryInfo: LibraryInfo): LibrariesAndSdks {
        val (dependencyCandidates, sdks) = resolveDependencyCandidatesAndSdks(libraryInfo)
        val libraryDependenciesFilter = DefaultLibraryDependenciesFilter union SharedNativeLibraryToNativeInteropFallbackDependenciesFilter
        val libraries = libraryDependenciesFilter(libraryInfo.platform, dependencyCandidates).map { it.libraries }.flatten()
        return Pair(libraries, sdks.toList())
    }

    private fun resolveDependencyCandidatesAndSdks(libraryInfo: LibraryInfo): Pair<Set<LibraryDependencyCandidate>, Set<SdkInfo>> {
        val processedModules = HashSet<Module>()
        val condition = Condition<OrderEntry> { orderEntry ->
            if (orderEntry is ModuleOrderEntry) {
                val module = orderEntry.module
                module != null && module !in processedModules
            } else {
                true
            }
        }

        val libraries = LinkedHashSet<LibraryDependencyCandidate>()
        val sdks = LinkedHashSet<SdkInfo>()

        val platform = libraryInfo.platform

        for (module in getLibraryUsageIndex().modulesLibraryIsUsedIn[libraryInfo.library.wrap()]) {
            if (!processedModules.add(module)) continue

            ModuleRootManager.getInstance(module).orderEntries().recursively().satisfying(condition).process(object : RootPolicy<Unit>() {
                override fun visitModuleSourceOrderEntry(moduleSourceOrderEntry: ModuleSourceOrderEntry, value: Unit) {
                    processedModules.add(moduleSourceOrderEntry.ownerModule)
                }

                override fun visitLibraryOrderEntry(libraryOrderEntry: LibraryOrderEntry, value: Unit) {
                    val otherLibrary = libraryOrderEntry.library
                    if (otherLibrary is LibraryEx && !otherLibrary.isDisposed) {
                        libraries.add(LibraryDependencyCandidate.fromLibraryOrNull(project, otherLibrary) ?: return)
                    }
                }

                override fun visitJdkOrderEntry(jdkOrderEntry: JdkOrderEntry, value: Unit) {
                    val jdk = jdkOrderEntry.jdk ?: return
                    SdkInfo(project, jdk).also { sdkInfo ->
                        if (compatiblePlatforms(platform, sdkInfo.platform))
                            sdks += sdkInfo
                    }
                }
            }, Unit)
        }

        return libraries to sdks
    }

    /**
     * @return true if it's OK to add a dependency from a library with platform [from] to a library with platform [to]
     */
    private fun compatiblePlatforms(from: TargetPlatform, to: TargetPlatform): Boolean {
        return from === to || to.containsAll(from) || to.isCommon()
    }

    private fun getLibraryUsageIndex(): LibraryUsageIndex {
        return CachedValuesManager.getManager(project).getCachedValue(project) {
            CachedValueProvider.Result(LibraryUsageIndex(), ProjectRootModificationTracker.getInstance(project))
        }!!
    }

    private inner class LibraryUsageIndex {
        val modulesLibraryIsUsedIn: MultiMap<LibraryWrapper, Module> = MultiMap.createSet()

        init {
            for (module in ModuleManager.getInstance(project).modules) {
                for (entry in ModuleRootManager.getInstance(module).orderEntries) {
                    if (entry is LibraryOrderEntry) {
                        val library = entry.library
                        if (library != null) {
                            modulesLibraryIsUsedIn.putValue(library.wrap(), module)
                        }
                    }
                }
            }
        }
    }
}

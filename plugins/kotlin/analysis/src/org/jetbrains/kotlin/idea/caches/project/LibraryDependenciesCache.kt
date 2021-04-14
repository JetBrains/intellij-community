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
import org.jetbrains.kotlin.idea.configuration.IdeBuiltInsLoadingState
import org.jetbrains.kotlin.idea.core.util.CachedValue
import org.jetbrains.kotlin.idea.core.util.getValue
import org.jetbrains.kotlin.idea.util.application.getServiceSafe
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import org.jetbrains.kotlin.idea.project.isHMPPEnabled

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
            ContainerUtil.createConcurrentWeakMap<Module, Pair<Set<LibraryDependencyCandidate>, Set<SdkInfo>>>(),
            ProjectRootManager.getInstance(project)
        )
    }

    override fun getLibrariesAndSdksUsedWith(libraryInfo: LibraryInfo): LibrariesAndSdks =
        cache.getOrPut(libraryInfo) { computeLibrariesAndSdksUsedWith(libraryInfo) }

    private fun computeLibrariesAndSdksUsedWith(libraryInfo: LibraryInfo): LibrariesAndSdks {
        val (dependencyCandidates, sdks) = computeLibrariesAndSdksUsedWithNoFilter(libraryInfo)
        val libraryDependenciesFilter = DefaultLibraryDependenciesFilter union SharedNativeLibraryToNativeInteropFallbackDependenciesFilter
        val libraries = libraryDependenciesFilter(libraryInfo.platform, dependencyCandidates).map { it.libraries }.flatten()
        return Pair(libraries, sdks.toList())
    }

    //NOTE: used LibraryRuntimeClasspathScope as reference
    private fun computeLibrariesAndSdksUsedWithNoFilter(libraryInfo: LibraryInfo): Pair<Set<LibraryDependencyCandidate>, Set<SdkInfo>> {
        val libraries = LinkedHashSet<LibraryDependencyCandidate>()
        val sdks = LinkedHashSet<SdkInfo>()

        for (module in getLibraryUsageIndex().getModulesLibraryIsUsedIn(libraryInfo)) {
            val (moduleLibraries, moduleSdks) = moduleDependenciesCache.getOrPut(module) {
                computeLibrariesAndSdksUsedIn(module)
            }

            libraries.addAll(moduleLibraries)
            sdks.addAll(moduleSdks)
        }

        val filteredLibraries = filterForBuiltins(libraryInfo, libraries)

        return filteredLibraries to sdks
    }

    private fun computeLibrariesAndSdksUsedIn(module: Module): Pair<Set<LibraryDependencyCandidate>, Set<SdkInfo>> {
        val libraries = LinkedHashSet<LibraryDependencyCandidate>()
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
                        LibraryDependencyCandidate.fromLibraryOrNull(
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

        val filteredLibraries = filterForBuiltins(libraryInfo, libraries)

        return filteredLibraries to sdks
    }

    /*
    * When built-ins are created from module dependencies (as opposed to loading them from classloader)
    * we must resolve Kotlin standard library containing some of the built-ins declarations in the same
    * resolver for project as JDK. This comes from the following requirements:
    * - JvmBuiltins need JDK and standard library descriptors -> resolver for project should be able to
    *   resolve them
    * - Builtins are created in BuiltinsCache -> module descriptors should be resolved under lock of the
    *   SDK resolver to prevent deadlocks
    * This means we have to maintain dependencies of the standard library manually or effectively drop
    * resolver for SDK otherwise. Libraries depend on superset of their actual dependencies because of
    * the inability to get real dependencies from IDEA model. So moving stdlib with all dependencies
    * down is a questionable option.
    */
    private fun filterForBuiltins(libraryInfo: LibraryInfo, dependencyLibraries: Set<LibraryDependencyCandidate>): Set<LibraryDependencyCandidate> {
        return if (!IdeBuiltInsLoadingState.isFromClassLoader && libraryInfo.isCoreKotlinLibrary(project)) {
            dependencyLibraries.filterTo(mutableSetOf()) { dep ->
                dep.libraries.any { it.isCoreKotlinLibrary(project) }
            }
        } else {
            dependencyLibraries
        }
    }

    private fun getLibraryUsageIndex(): LibraryUsageIndex {
        return CachedValuesManager.getManager(project).getCachedValue(project) {
            CachedValueProvider.Result(LibraryUsageIndex(), ProjectRootModificationTracker.getInstance(project))
        }!!
    }

    /*
    * When built-ins are created from module dependencies (as opposed to loading them from classloader)
    * we must resolve Kotlin standard library containing some of the built-ins declarations in the same
    * resolver for project as JDK. This comes from the following requirements:
    * - JvmBuiltins need JDK and standard library descriptors -> resolver for project should be able to
    *   resolve them
    * - Builtins are created in BuiltinsCache -> module descriptors should be resolved under lock of the
    *   SDK resolver to prevent deadlocks
    * This means we have to maintain dependencies of the standard library manually or effectively drop
    * resolver for SDK otherwise. Libraries depend on superset of their actual dependencies because of
    * the inability to get real dependencies from IDEA model. So moving stdlib with all dependencies
    * down is a questionable option.
    */
    private fun filterForBuiltins(libraryInfo: LibraryInfo, dependencyLibraries: Set<DependencyCandidate>): Set<DependencyCandidate> {
        return if (!IdeBuiltInsLoadingState.isFromClassLoader && libraryInfo.isCoreKotlinLibrary(project)) {
            dependencyLibraries.filterTo(mutableSetOf()) { dep ->
                dep.libraries.any { it.isCoreKotlinLibrary(project) }
            }
        } else {
            dependencyLibraries
        }
    }

    private inner class LibraryUsageIndex {
        private val modulesLibraryIsUsedIn: MultiMap<LibraryWrapper, Module> = MultiMap.createSet()

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

        fun getModulesLibraryIsUsedIn(libraryInfo: LibraryInfo) = sequence<Module> {
            val ideaModelInfosCache = getIdeaModelInfosCache(project)
            for (module in modulesLibraryIsUsedIn[libraryInfo.library.wrap()]) {
                val mappedModuleInfos = ideaModelInfosCache.getModuleInfosForModule(module)
                if (mappedModuleInfos.any { it.platform.canDependOn(libraryInfo, module.isHMPPEnabled) }) {
                    yield(module)
                }
            }
        }
    }
}

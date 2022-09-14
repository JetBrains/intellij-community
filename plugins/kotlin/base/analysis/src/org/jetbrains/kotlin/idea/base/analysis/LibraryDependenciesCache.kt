// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.base.analysis

import com.intellij.ProjectTopics
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProgressManager.checkCanceled
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.*
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.Disposer
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.containers.MultiMap
import com.intellij.workspaceModel.ide.WorkspaceModelTopics
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.api.ModuleEntity
import org.jetbrains.kotlin.idea.base.analysis.libraries.LibraryDependencyCandidate
import org.jetbrains.kotlin.idea.base.facet.isHMPPEnabled
import org.jetbrains.kotlin.idea.base.projectStructure.*
import org.jetbrains.kotlin.idea.base.projectStructure.LibraryDependenciesCache.LibraryDependencies
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.LibraryInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.SdkInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.allSdks
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.checkValidity
import org.jetbrains.kotlin.idea.base.util.caching.*
import org.jetbrains.kotlin.idea.caches.project.*
import org.jetbrains.kotlin.idea.caches.trackers.ModuleModificationTracker
import org.jetbrains.kotlin.idea.configuration.isMavenized
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

typealias LibraryDependencyCandidatesAndSdkInfos = Pair<Set<LibraryDependencyCandidate>, Set<SdkInfo>>

class LibraryDependenciesCacheImpl(private val project: Project) : LibraryDependenciesCache, Disposable {
    companion object {
        fun getInstance(project: Project): LibraryDependenciesCache = project.service()
    }

    private val cache = LibraryDependenciesInnerCache()

    private val moduleDependenciesCache = ModuleDependenciesCache()

    init {
        Disposer.register(this, cache)
        Disposer.register(this, moduleDependenciesCache)
    }

    override fun getLibraryDependencies(library: LibraryInfo): LibraryDependencies = cache[library]

    override fun dispose() = Unit

    private fun computeLibrariesAndSdksUsedWith(libraryInfo: LibraryInfo): LibraryDependencies {
        val (dependencyCandidates, sdks) = computeLibrariesAndSdksUsedWithNoFilter(libraryInfo)

        // Maven is Gradle Metadata unaware, and therefore needs stricter filter. See KTIJ-15758
        val libraryDependenciesFilter = if (project.isMavenized)
            StrictEqualityForPlatformSpecificCandidatesFilter
        else
            DefaultLibraryDependenciesFilter union SharedNativeLibraryToNativeInteropFallbackDependenciesFilter
        val libraries = libraryDependenciesFilter(libraryInfo.platform, dependencyCandidates).flatMap { it.libraries }
        return LibraryDependencies(libraries, sdks.toList())
    }

    //NOTE: used LibraryRuntimeClasspathScope as reference
    private fun computeLibrariesAndSdksUsedWithNoFilter(libraryInfo: LibraryInfo): LibraryDependencyCandidatesAndSdkInfos {
        val libraries = LinkedHashSet<LibraryDependencyCandidate>()
        val sdks = LinkedHashSet<SdkInfo>()

        val modulesLibraryIsUsedIn =
            getLibraryUsageIndex().getModulesLibraryIsUsedIn(libraryInfo)

        for (module in modulesLibraryIsUsedIn) {
            checkCanceled()
            val (moduleLibraries, moduleSdks) = moduleDependenciesCache[module]

            libraries.addAll(moduleLibraries)
            sdks.addAll(moduleSdks)
        }

        val filteredLibraries = filterForBuiltins(libraryInfo, libraries)

        return filteredLibraries to sdks
    }

    private fun computeLibrariesAndSdksUsedIn(module: Module): LibraryDependencyCandidatesAndSdkInfos {
        val libraries = LinkedHashSet<LibraryDependencyCandidate>()
        val sdks = LinkedHashSet<SdkInfo>()

        val processedModules = HashSet<Module>()
        val condition = Condition<OrderEntry> { orderEntry ->
            checkCanceled()
            orderEntry.safeAs<ModuleOrderEntry>()?.let {
                it.module?.run { this !in processedModules } ?: false
            } ?: true
        }

        ModuleRootManager.getInstance(module).orderEntries().recursively().satisfying(condition).process(object : RootPolicy<Unit>() {
            override fun visitModuleSourceOrderEntry(moduleSourceOrderEntry: ModuleSourceOrderEntry, value: Unit) {
                processedModules.add(moduleSourceOrderEntry.ownerModule)
            }

            override fun visitLibraryOrderEntry(libraryOrderEntry: LibraryOrderEntry, value: Unit) {
                checkCanceled()
                libraryOrderEntry.library.safeAs<LibraryEx>()?.takeIf { !it.isDisposed }?.let { library ->
                    for (libraryInfo in LibraryInfoCache.getInstance(project)[library]) {
                        LibraryDependencyCandidate.fromLibraryOrNull(
                            project,
                            libraryInfo.library
                        )?.let {
                            libraries += it
                        }
                    }
                }
            }

            override fun visitJdkOrderEntry(jdkOrderEntry: JdkOrderEntry, value: Unit) {
                checkCanceled()
                jdkOrderEntry.jdk?.let { jdk ->
                    sdks += SdkInfo(project, jdk)
                }
            }
        }, Unit)

        return libraries to sdks
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

    private fun getLibraryUsageIndex(): LibraryUsageIndex =
        CachedValuesManager.getManager(project).getCachedValue(project) {
            CachedValueProvider.Result(
                LibraryUsageIndex(),
                ModuleModificationTracker.getInstance(project),
                LibraryModificationTracker.getInstance(project)
            )
        }!!

    private inner class LibraryDependenciesInnerCache :
      SynchronizedFineGrainedEntityCache<LibraryInfo, LibraryDependencies>(project, cleanOnLowMemory = true),
      LibraryInfoListener,
      ModuleRootListener,
      ProjectJdkTable.Listener {
        override fun subscribe() {
            val connection = project.messageBus.connect(this)
            connection.subscribe(LibraryInfoListener.TOPIC, this)
            connection.subscribe(ProjectTopics.PROJECT_ROOTS, this)
            connection.subscribe(ProjectJdkTable.JDK_TABLE_TOPIC, this)
        }

        override fun libraryInfosRemoved(libraryInfos: Collection<LibraryInfo>) {
            invalidateEntries({ k, v -> k in libraryInfos || v.libraries.any { it in libraryInfos } })
        }

        override fun jdkRemoved(jdk: Sdk) {
            invalidateEntries({ _, v -> v.sdk.any { it.sdk == jdk } })
        }

        override fun jdkNameChanged(jdk: Sdk, previousName: String) {
            jdkRemoved(jdk)
        }

        override fun calculate(key: LibraryInfo): LibraryDependencies =
            computeLibrariesAndSdksUsedWith(key)

        override fun checkKeyValidity(key: LibraryInfo) {
            key.checkValidity()
        }

        override fun checkValueValidity(value: LibraryDependencies) {
            value.libraries.forEach { it.checkValidity() }
        }

        override fun rootsChanged(event: ModuleRootEvent) {
            // SDK could be changed (esp in tests) out of message bus subscription
            val sdks = project.allSdks()
            invalidateEntries(
                { _, value -> value.sdk.any { it.sdk !in sdks } },
                // unable to check entities properly: an event could be not the last
                validityCondition = null
            )
        }
    }

    private inner class ModuleDependenciesCache :
      SynchronizedFineGrainedEntityCache<Module, LibraryDependencyCandidatesAndSdkInfos>(project, cleanOnLowMemory = true),
      ProjectJdkTable.Listener,
      LibraryInfoListener,
      ModuleRootListener {

        override fun subscribe() {
            val connection = project.messageBus.connect(this)
            WorkspaceModelTopics.getInstance(project).subscribeImmediately(connection, ModelChangeListener())
            connection.subscribe(LibraryInfoListener.TOPIC, this)
            connection.subscribe(ProjectJdkTable.JDK_TABLE_TOPIC, this)
            connection.subscribe(ProjectTopics.PROJECT_ROOTS, this)
        }

        override fun calculate(key: Module): LibraryDependencyCandidatesAndSdkInfos =
            computeLibrariesAndSdksUsedIn(key)

        override fun checkKeyValidity(key: Module) {
            key.checkValidity()
        }

        override fun checkValueValidity(value: LibraryDependencyCandidatesAndSdkInfos) {
            value.first.forEach { it.libraries.forEach { libraryInfo -> libraryInfo.checkValidity() } }
        }

        override fun jdkRemoved(jdk: Sdk) {
            invalidateEntries({ _, candidates -> candidates.second.any { it.sdk == jdk } })
        }

        override fun jdkNameChanged(jdk: Sdk, previousName: String) {
            jdkRemoved(jdk)
        }

        override fun rootsChanged(event: ModuleRootEvent) {
            // SDK could be changed (esp in tests) out of message bus subscription
            val sdks = project.allSdks()

            invalidateEntries(
                { _, (_, sdkInfos) -> sdkInfos.any { it.sdk !in sdks } },
                // unable to check entities properly: an event could be not the last
                validityCondition = null
            )
        }

        inner class ModelChangeListener : WorkspaceEntityChangeListener<ModuleEntity, Module>(project) {
            override val entityClass: Class<ModuleEntity>
                get() = ModuleEntity::class.java

            override fun map(storage: EntityStorage, entity: ModuleEntity): Module? =
                storage.findModuleWithHack(entity, project)

            override fun entitiesChanged(outdated: List<Module>) {
                invalidateKeys(outdated) { _, _ -> false }
            }
        }

        override fun libraryInfosRemoved(libraryInfos: Collection<LibraryInfo>) {
            val infos = libraryInfos.toHashSet()
            invalidateEntries(
                { _, v ->
                    v.first.any { candidate -> candidate.libraries.any { it in infos } }
                },
                // unable to check entities properly: an event could be not the last
                validityCondition = null
            )
        }
    }

    private inner class LibraryUsageIndex {
        private val modulesLibraryIsUsedIn: MultiMap<LibraryWrapper, Module>

        init {
            val map: MultiMap<LibraryWrapper, Module> = MultiMap.createSet()
            for (module in runReadAction { ModuleManager.getInstance(project).modules }) {
                checkCanceled()
                runReadAction {
                    for (entry in ModuleRootManager.getInstance(module).orderEntries) {
                        if (entry is LibraryOrderEntry) {
                            val library = entry.library
                            if (library != null) {
                                map.putValue(library.wrap(), module)
                            }
                        }
                    }
                }
            }
            modulesLibraryIsUsedIn = map
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

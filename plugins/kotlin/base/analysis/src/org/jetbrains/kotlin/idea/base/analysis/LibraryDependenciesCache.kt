// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.base.analysis

import com.intellij.ProjectTopics
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProgressManager.checkCanceled
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.*
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.Disposer
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.containers.MultiMap
import com.intellij.workspaceModel.ide.WorkspaceModelChangeListener
import com.intellij.workspaceModel.ide.WorkspaceModelTopics
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.findLibraryBridge
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerBridgeImpl.Companion.findModuleByEntity
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.findModule
import com.intellij.workspaceModel.storage.EntityChange
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.VersionedStorageChange
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.LibraryEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.ModuleEntity
import org.jetbrains.kotlin.idea.base.analysis.libraries.LibraryDependencyCandidate
import org.jetbrains.kotlin.idea.base.facet.isHMPPEnabled
import org.jetbrains.kotlin.idea.base.projectStructure.*
import org.jetbrains.kotlin.idea.base.projectStructure.LibraryDependenciesCache.LibraryDependencies
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.LibraryInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.SdkInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.allSdks
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.checkValidity
import org.jetbrains.kotlin.idea.base.util.caching.FineGrainedEntityCache.Companion.isFineGrainedCacheInvalidationEnabled
import org.jetbrains.kotlin.idea.base.util.caching.SynchronizedFineGrainedEntityCache
import org.jetbrains.kotlin.idea.base.util.caching.WorkspaceEntityChangeListener
import org.jetbrains.kotlin.idea.caches.project.*
import org.jetbrains.kotlin.utils.addIfNotNull
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

typealias LibraryDependencyCandidatesAndSdkInfos = Pair<Set<LibraryDependencyCandidate>, Set<SdkInfo>>

class LibraryDependenciesCacheImpl(private val project: Project) : LibraryDependenciesCache, Disposable {
    companion object {
        fun getInstance(project: Project): LibraryDependenciesCache = project.service()
    }

    private val cache = LibraryDependenciesInnerCache()

    private val moduleDependenciesCache = ModuleDependenciesCache()

    private val libraryUsageIndex = LibraryUsageIndex2()

    init {
        Disposer.register(this, cache)
        Disposer.register(this, moduleDependenciesCache)
        Disposer.register(this, libraryUsageIndex)
    }

    override fun getLibraryDependencies(library: LibraryInfo): LibraryDependencies = cache[library]

    override fun dispose() = Unit

    private fun computeLibrariesAndSdksUsedWith(libraryInfo: LibraryInfo): LibraryDependencies {
        val (dependencyCandidates, sdks) = computeLibrariesAndSdksUsedWithNoFilter(libraryInfo)
        val libraryDependenciesFilter = DefaultLibraryDependenciesFilter union SharedNativeLibraryToNativeInteropFallbackDependenciesFilter
        val libraries = libraryDependenciesFilter(libraryInfo.platform, dependencyCandidates).flatMap { it.libraries }
        return LibraryDependencies(libraries, sdks.toList())
    }

    //NOTE: used LibraryRuntimeClasspathScope as reference
    private fun computeLibrariesAndSdksUsedWithNoFilter(libraryInfo: LibraryInfo): LibraryDependencyCandidatesAndSdkInfos {
        val libraries = LinkedHashSet<LibraryDependencyCandidate>()
        val sdks = LinkedHashSet<SdkInfo>()

        val modulesLibraryIsUsedIn =
            if (!isFineGrainedCacheInvalidationEnabled) {
                getLibraryUsageIndex().getModulesLibraryIsUsedIn(libraryInfo)
            } else {
                libraryUsageIndex.getModulesLibraryIsUsedIn(libraryInfo)
            }

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
                libraryOrderEntry.library.safeAs<LibraryEx>()?.takeIf { !it.isDisposed }?.let {
                    libraries += LibraryInfoCache.getInstance(project)[it].mapNotNull { libraryInfo ->
                        LibraryDependencyCandidate.fromLibraryOrNull(
                            project,
                            libraryInfo.library
                        )
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

    private fun getLibraryUsageIndex(): LibraryUsageIndex {
        return CachedValuesManager.getManager(project).getCachedValue(project) {
            CachedValueProvider.Result(LibraryUsageIndex(), ProjectRootModificationTracker.getInstance(project))
        }!!
    }

    private inner class LibraryDependenciesInnerCache :
        SynchronizedFineGrainedEntityCache<LibraryInfo, LibraryDependencies>(project, cleanOnLowMemory = true),
        OutdatedLibraryInfoListener,
        ModuleRootListener,
        ProjectJdkTable.Listener {
        override fun subscribe() {
            val connection = project.messageBus.connect(this)
            connection.subscribe(OutdatedLibraryInfoListener.TOPIC, this)
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
        OutdatedLibraryInfoListener,
        ModuleRootListener {

        override fun subscribe() {
            val connection = project.messageBus.connect(this)
            WorkspaceModelTopics.getInstance(project).subscribeImmediately(connection, ModelChangeListener())
            connection.subscribe(OutdatedLibraryInfoListener.TOPIC, this)
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

            override fun map(storage: EntityStorage, entity: ModuleEntity): Module? = entity.findModule(storage)

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

    private data class Change<T>(val old: List<T>, val new: List<T>)

    private inner class LibraryUsageIndex2:
        SynchronizedFineGrainedEntityCache<LibraryWrapper, Set<Module>>(project, cleanOnLowMemory = true),
        WorkspaceModelChangeListener {

        init {
            val initialMap = mutableMapOf<LibraryWrapper, MutableSet<Module>>()
            for (module in ModuleManager.getInstance(project).modules) {
                initialMap.populateLibraries(module)
            }
            putAll(initialMap)
        }

        private fun MutableMap<LibraryWrapper, MutableSet<Module>>.populateLibraries(module: Module) {
            for (entry in ModuleRootManager.getInstance(module).orderEntries) {
                if (entry is LibraryOrderEntry) {
                    entry.library?.let { library ->
                        val modules = getOrPut(library.wrap()) { hashSetOf() }
                        modules += module
                    }
                }
            }
        }

        override fun subscribe() {
            val busConnection = project.messageBus.connect(this)
            WorkspaceModelTopics.getInstance(project).subscribeImmediately(busConnection, this)
        }

        override fun calculate(key: LibraryWrapper): Set<Module> {
            // it should not happen - keep it as last resort fallback
            val libraryEx = key.library
            val modules = mutableSetOf<Module>()
            for (module in ModuleManager.getInstance(project).modules) {
                for (entry in ModuleRootManager.getInstance(module).orderEntries) {
                    if (entry is LibraryOrderEntry && entry.library == libraryEx) {
                        modules += module
                    }
                }
            }
            return modules
        }

        override fun checkKeyValidity(key: LibraryWrapper) {
            key.checkValidity()
        }

        override fun checkValueValidity(value: Set<Module>) {
            value.forEach(Module::checkValidity)
        }

        fun getModulesLibraryIsUsedIn(libraryInfo: LibraryInfo) = sequence {
            val ideaModelInfosCache = getIdeaModelInfosCache(project)
            val libraryWrapper = libraryInfo.library.wrap()
            val modulesLibraryIsUsedIn = get(libraryWrapper)
            for (module in modulesLibraryIsUsedIn) {
                checkCanceled()
                val mappedModuleInfos = ideaModelInfosCache.getModuleInfosForModule(module)
                if (mappedModuleInfos.any { it.platform.canDependOn(libraryInfo, module.isHMPPEnabled) }) {
                    yield(module)
                }
            }
        }

        override fun changed(event: VersionedStorageChange) {
            val storageBefore = event.storageBefore
            val storageAfter = event.storageAfter
            val moduleChanges = event.getChanges(ModuleEntity::class.java)
            val libraryChanges = event.getChanges(LibraryEntity::class.java)
            if (moduleChanges.isEmpty() && libraryChanges.isEmpty()) {
                return
            }

            val modulesChange = modulesChange(moduleChanges, storageBefore, storageAfter)
            val librariesChange = librariesChange(libraryChanges, storageBefore, storageAfter)

            val modulesFromNewLibs = libraryChanges.mapNotNull { change ->
                val newEntity = newEntity(change) ?: return@mapNotNull null
                val referrers = storageAfter.referrers(newEntity.persistentId, ModuleEntity::class.java)
                referrers.mapNotNull { storageAfter.findModuleByEntity(it) }
            }.flatMapTo(hashSetOf()) { it }

            val modulesToInvalidate = modulesChange.old.toHashSet() + modulesFromNewLibs
            val librariesToInvalidate = librariesChange.old.toHashSet()

            invalidateEntries(
                { libraryWrapper, modules -> libraryWrapper.library in librariesToInvalidate || modules.any { it in modulesToInvalidate } }
            )

            val newValues = mutableMapOf<LibraryWrapper, MutableSet<Module>>()
            val modulesToUpdate = modulesChange.new + modulesFromNewLibs
            modulesToUpdate.forEach { newValues.populateLibraries(it) }
            putAll(newValues)
        }

        private fun <T: WorkspaceEntity> oldEntity(change: EntityChange<T>) =
            when (change) {
                is EntityChange.Added -> null
                is EntityChange.Removed -> change.entity
                is EntityChange.Replaced -> change.oldEntity
            }

        private fun <T: WorkspaceEntity> newEntity(change: EntityChange<T>) =
            when (change) {
                is EntityChange.Added -> change.entity
                is EntityChange.Removed -> null
                is EntityChange.Replaced -> change.newEntity
            }

        private fun modulesChange(
            moduleChanges: List<EntityChange<ModuleEntity>>,
            storageBefore: EntityStorage,
            storageAfter: EntityStorage
        ): Change<Module> {
            val oldModules = mutableListOf<Module>()
            val newModules = mutableListOf<Module>()
            for (change in moduleChanges) {
                oldEntity(change)?.let { oldModules.addIfNotNull(storageBefore.findModuleByEntity(it)) }
                newEntity(change)?.let { newModules.addIfNotNull(storageAfter.findModuleByEntity(it)) }
            }
            return Change(oldModules, newModules)
        }

        private fun librariesChange(
            moduleChanges: List<EntityChange<LibraryEntity>>,
            storageBefore: EntityStorage,
            storageAfter: EntityStorage
        ): Change<Library> {
            val oldLibraries = mutableListOf<Library>()
            val newLibraries = mutableListOf<Library>()
            for (change in moduleChanges) {
                oldEntity(change)?.let { oldLibraries.addIfNotNull(it.findLibraryBridge(storageBefore)) }
                newEntity(change)?.let { newLibraries.addIfNotNull(it.findLibraryBridge(storageAfter)) }
            }
            return Change(oldLibraries, newLibraries)
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

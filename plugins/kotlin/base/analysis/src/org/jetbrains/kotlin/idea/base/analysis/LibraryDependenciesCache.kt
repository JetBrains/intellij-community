// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.analysis

import com.intellij.ProjectTopics
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
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
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.util.Disposer
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.containers.MultiMap
import com.intellij.workspaceModel.ide.WorkspaceModelChangeListener
import com.intellij.workspaceModel.ide.WorkspaceModelTopics
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.findModule
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.VersionedStorageChange
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity
import org.jetbrains.kotlin.idea.base.analysis.libraries.LibraryDependencyCandidate
import org.jetbrains.kotlin.idea.base.facet.isHMPPEnabled
import org.jetbrains.kotlin.idea.base.projectStructure.*
import org.jetbrains.kotlin.idea.base.projectStructure.LibraryDependenciesCache.LibraryDependencies
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.LibraryInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.SdkInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.allSdks
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.checkValidity
import org.jetbrains.kotlin.idea.base.util.caching.ModuleEntityChangeListener
import org.jetbrains.kotlin.idea.base.util.caching.SynchronizedFineGrainedEntityCache
import org.jetbrains.kotlin.idea.caches.project.*
import org.jetbrains.kotlin.idea.caches.trackers.ModuleModificationTracker
import org.jetbrains.kotlin.idea.configuration.isMavenized
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

private class LibraryDependencyCandidatesAndSdkInfos(
    val libraryDependencyCandidates: MutableSet<LibraryDependencyCandidate> = linkedSetOf(),
    val sdkInfos: MutableSet<SdkInfo> = linkedSetOf()
) {
    operator fun plusAssign(other: LibraryDependencyCandidatesAndSdkInfos) {
        libraryDependencyCandidates += other.libraryDependencyCandidates
        sdkInfos += other.sdkInfos
    }

    operator fun plusAssign(libraryDependencyCandidate: LibraryDependencyCandidate) {
        libraryDependencyCandidates += libraryDependencyCandidate
    }

    operator fun plusAssign(sdkInfo: SdkInfo) {
        sdkInfos += sdkInfo
    }

    override fun toString(): String {
        return "[${Integer.toHexString(System.identityHashCode(this))}] libraryDependencyCandidates: ${
            libraryDependencyCandidates.map { it.libraries.map(LibraryInfo::name) }
        } sdkInfos: ${sdkInfos.map { it.sdk.name }}"
    }
}

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
        val libraryDependencyCandidatesAndSdkInfos = computeLibrariesAndSdksUsedWithNoFilter(libraryInfo)

        // Maven is Gradle Metadata unaware, and therefore needs stricter filter. See KTIJ-15758
        val libraryDependenciesFilter = if (project.isMavenized)
            StrictEqualityForPlatformSpecificCandidatesFilter
        else
            DefaultLibraryDependenciesFilter union SharedNativeLibraryToNativeInteropFallbackDependenciesFilter
        val libraries = libraryDependenciesFilter(
            libraryInfo.platform,
            libraryDependencyCandidatesAndSdkInfos.libraryDependencyCandidates
        ).flatMap { it.libraries }
        return LibraryDependencies(libraryInfo, libraries, libraryDependencyCandidatesAndSdkInfos.sdkInfos.toList())
    }

    //NOTE: used LibraryRuntimeClasspathScope as reference
    private fun computeLibrariesAndSdksUsedWithNoFilter(libraryInfo: LibraryInfo): LibraryDependencyCandidatesAndSdkInfos {
        val libraryDependencyCandidatesAndSdkInfos = LibraryDependencyCandidatesAndSdkInfos()

        val modulesLibraryIsUsedIn =
            getLibraryUsageIndex().getModulesLibraryIsUsedIn(libraryInfo)

        for (module in modulesLibraryIsUsedIn) {
            checkCanceled()
            libraryDependencyCandidatesAndSdkInfos += moduleDependenciesCache[module]
        }

        val filteredLibraries = filterForBuiltins(libraryInfo, libraryDependencyCandidatesAndSdkInfos.libraryDependencyCandidates)

        return LibraryDependencyCandidatesAndSdkInfos(filteredLibraries, libraryDependencyCandidatesAndSdkInfos.sdkInfos)
    }

    /*
    * When built-ins are created from module dependencies (as opposed to loading them from classloader)
    * we must resolve Kotlin standard library containing some built-ins declarations in the same
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
    private fun filterForBuiltins(
        libraryInfo: LibraryInfo,
        dependencyLibraries: MutableSet<LibraryDependencyCandidate>
    ): MutableSet<LibraryDependencyCandidate> {
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
            connection.subscribe(WorkspaceModelTopics.CHANGED, ModelChangeListener())
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
            if (event.isCausedByWorkspaceModelChangesOnly) return

            // SDK could be changed (esp in tests) out of message bus subscription
            val sdks = project.allSdks()
            invalidateEntries(
                { _, value -> value.sdk.any { it.sdk !in sdks } },
                // unable to check entities properly: an event could be not the last
                validityCondition = null
            )
        }

        inner class ModelChangeListener : ModuleEntityChangeListener(project) {
            override fun entitiesChanged(outdated: List<Module>) {
                invalidate()
            }
        }

    }

    private inner class ModuleDependenciesCache :
        SynchronizedFineGrainedEntityCache<Module, LibraryDependencyCandidatesAndSdkInfos>(project),
        WorkspaceModelChangeListener,
        ProjectJdkTable.Listener,
        LibraryInfoListener,
        ModuleRootListener {

        override fun subscribe() {
            val connection = project.messageBus.connect(this)
            connection.subscribe(WorkspaceModelTopics.CHANGED, this)
            connection.subscribe(LibraryInfoListener.TOPIC, this)
            connection.subscribe(ProjectJdkTable.JDK_TABLE_TOPIC, this)
            connection.subscribe(ProjectTopics.PROJECT_ROOTS, this)
        }

        @RequiresReadLock
        override fun get(key: Module): LibraryDependencyCandidatesAndSdkInfos {
            ApplicationManager.getApplication().assertReadAccessAllowed()
            return internalGet(key, hashMapOf(), linkedSetOf(), hashMapOf())
        }

        private fun internalGet(
            key: Module,
            tmpResults: MutableMap<Module, LibraryDependencyCandidatesAndSdkInfos>,
            trace: LinkedHashSet<Module>,
            loops: MutableMap<Module, Set<Module>>
        ): LibraryDependencyCandidatesAndSdkInfos {
            checkKeyAndDisposeIllegalEntry(key)

            useCache { cache ->
                checkEntitiesIfRequired(cache)

                cache[key]
            }?.let { return it }

            checkCanceled()

            val newValue = computeLibrariesAndSdksUsedIn(key, tmpResults, trace, loops)

            if (isValidityChecksEnabled) {
                checkValueValidity(newValue)
            }

            val existedValue = if (trace.isNotEmpty()) {
                dumpLoopsIfPossible(key, newValue, tmpResults, trace, loops)
            } else {
                // it is possible to dump results when all dependencies are resolved hence trace is empty
                useCache { cache ->
                    val existedValue = cache.putIfAbsent(key, newValue)
                    for (entry in tmpResults.entries) {
                        cache.putIfAbsent(entry.key, entry.value)
                    }

                    existedValue
                }
            }

            return existedValue ?: newValue
        }

        /**
         * It is possible to dump loops from the subtree from the last module from the loop
         *
         * @param trace is not empty trace
         * @return existed value if applicable
         */
        private fun dumpLoopsIfPossible(
            key: Module,
            newValue: LibraryDependencyCandidatesAndSdkInfos,
            tmpResults: MutableMap<Module, LibraryDependencyCandidatesAndSdkInfos>,
            trace: LinkedHashSet<Module>,
            loops: MutableMap<Module, Set<Module>>,
        ): LibraryDependencyCandidatesAndSdkInfos? {
            val currentLoop = loops[key] ?: return null
            if (trace.last() in loops) return null

            return useCache { cache ->
                val existedValue = cache.putIfAbsent(key, newValue)
                tmpResults.remove(key)
                for (loopModule in currentLoop) {
                    tmpResults.remove(loopModule)?.let {
                        cache.putIfAbsent(loopModule, it)
                    }

                    loops.remove(loopModule)
                }

                existedValue
            }
        }

        private fun computeLibrariesAndSdksUsedIn(
            module: Module,
            tmpResults: MutableMap<Module, LibraryDependencyCandidatesAndSdkInfos>,
            trace: LinkedHashSet<Module>,
            loops: MutableMap<Module, Set<Module>>
        ): LibraryDependencyCandidatesAndSdkInfos {
            checkCanceled()
            check(trace.add(module)) { "recursion detected" }

            val libraryDependencyCandidatesAndSdkInfos = LibraryDependencyCandidatesAndSdkInfos()
            tmpResults[module] = libraryDependencyCandidatesAndSdkInfos

            val modulesToVisit = HashSet<Module>()

            val infoCache = LibraryInfoCache.getInstance(project)
            ModuleRootManager.getInstance(module).orderEntries()
                .process(object : RootPolicy<Unit>() {
                    override fun visitModuleOrderEntry(moduleOrderEntry: ModuleOrderEntry, value: Unit) {
                        moduleOrderEntry.module?.let(modulesToVisit::add)
                    }

                    override fun visitLibraryOrderEntry(libraryOrderEntry: LibraryOrderEntry, value: Unit) {
                        checkCanceled()
                        val libraryEx = libraryOrderEntry.library.safeAs<LibraryEx>()?.takeUnless { it.isDisposed } ?: return
                        val candidate = LibraryDependencyCandidate.fromLibraryOrNull(infoCache[libraryEx]) ?: return
                        libraryDependencyCandidatesAndSdkInfos += candidate
                    }

                    override fun visitJdkOrderEntry(jdkOrderEntry: JdkOrderEntry, value: Unit) {
                        checkCanceled()
                        jdkOrderEntry.jdk?.let { jdk ->
                            libraryDependencyCandidatesAndSdkInfos += SdkInfo(project, jdk)
                        }
                    }
                }, Unit)

            // handle circular dependency case
            for (moduleToVisit in modulesToVisit) {
                checkCanceled()
                if (moduleToVisit == module) continue

                if (moduleToVisit !in trace) continue

                // circular dependency found
                val reversedTrace = trace.toList().asReversed()

                val sharedLibraryDependencyCandidatesAndSdkInfos: LibraryDependencyCandidatesAndSdkInfos = run {
                    var shared: LibraryDependencyCandidatesAndSdkInfos? = null
                    val loop = hashSetOf<Module>()
                    val duplicates = hashSetOf<LibraryDependencyCandidatesAndSdkInfos>()
                    for (traceModule in reversedTrace) {
                        loop += traceModule
                        loops[traceModule]?.let { loop += it }
                        loops[traceModule] = loop
                        val traceModuleLibraryDependencyCandidatesAndSdkInfos = tmpResults.getValue(traceModule)
                        if (shared == null && !duplicates.add(traceModuleLibraryDependencyCandidatesAndSdkInfos)) {
                            shared = traceModuleLibraryDependencyCandidatesAndSdkInfos
                        }
                        if (traceModule === moduleToVisit) {
                            break
                        }
                    }

                    shared ?: duplicates.first()
                }

                sharedLibraryDependencyCandidatesAndSdkInfos += libraryDependencyCandidatesAndSdkInfos

                for (traceModule in reversedTrace) {
                    val traceModuleLibraryDependencyCandidatesAndSdkInfos: LibraryDependencyCandidatesAndSdkInfos =
                        tmpResults.getValue(traceModule)
                    if (traceModuleLibraryDependencyCandidatesAndSdkInfos === sharedLibraryDependencyCandidatesAndSdkInfos) {
                        if (traceModule === moduleToVisit) {
                            break
                        }
                        continue
                    }
                    sharedLibraryDependencyCandidatesAndSdkInfos += traceModuleLibraryDependencyCandidatesAndSdkInfos
                    tmpResults[traceModule] = sharedLibraryDependencyCandidatesAndSdkInfos

                    loops[traceModule]?.let { loop ->
                        for (loopModule in loop) {
                            if (loopModule == traceModule) continue
                            val value = tmpResults.getValue(loopModule)
                            if (value === sharedLibraryDependencyCandidatesAndSdkInfos) continue
                            sharedLibraryDependencyCandidatesAndSdkInfos += value
                            tmpResults[loopModule] = sharedLibraryDependencyCandidatesAndSdkInfos
                        }
                    }
                    if (traceModule === moduleToVisit) {
                        break
                    }
                }
            }

            // merge
            for (moduleToVisit in modulesToVisit) {
                checkCanceled()
                if (moduleToVisit == module || moduleToVisit in trace) continue

                val moduleToVisitLibraryDependencyCandidatesAndSdkInfos =
                    tmpResults[moduleToVisit] ?: internalGet(moduleToVisit, tmpResults, trace, loops = loops)

                val moduleLibraryDependencyCandidatesAndSdkInfos = tmpResults.getValue(module)

                // We should not include SDK from dependent modules
                // see the traverse way of OrderEnumeratorBase#shouldAddOrRecurse for JdkOrderEntry
                moduleLibraryDependencyCandidatesAndSdkInfos.libraryDependencyCandidates += moduleToVisitLibraryDependencyCandidatesAndSdkInfos.libraryDependencyCandidates
            }

            trace.remove(module)

            return tmpResults.getValue(module)
        }

        override fun calculate(key: Module): LibraryDependencyCandidatesAndSdkInfos =
            throw UnsupportedOperationException("calculate(Module) should not be invoked due to custom impl of get()")

        override fun checkKeyValidity(key: Module) {
            key.checkValidity()
        }

        override fun checkValueValidity(value: LibraryDependencyCandidatesAndSdkInfos) {
            value.libraryDependencyCandidates.forEach { it.libraries.forEach { libraryInfo -> libraryInfo.checkValidity() } }
        }

        override fun jdkRemoved(jdk: Sdk) {
            invalidateEntries({ _, candidates -> candidates.sdkInfos.any { it.sdk == jdk } })
        }

        override fun jdkNameChanged(jdk: Sdk, previousName: String) {
            jdkRemoved(jdk)
        }

        override fun rootsChanged(event: ModuleRootEvent) {
            if (event.isCausedByWorkspaceModelChangesOnly) return

            // TODO: `invalidate()` to be drop when IDEA-298694 is fixed
            //  Reason: unload modules are untracked with WorkspaceModel
            invalidate()
            return

            // SDK could be changed (esp in tests) out of message bus subscription
            val sdks = project.allSdks()

            invalidateEntries(
                { _, candidates -> candidates.sdkInfos.any { it.sdk !in sdks } },
                // unable to check entities properly: an event could be not the last
                validityCondition = null
            )
        }

        override fun beforeChanged(event: VersionedStorageChange) {
            val storageBefore = event.storageBefore
            val changes = event.getChanges(ModuleEntity::class.java).ifEmpty { return }

            val outdatedModules = mutableSetOf<Module>()
            for (change in changes) {
                val moduleEntity = change.oldEntity ?: continue
                collectOutdatedModules(moduleEntity, storageBefore, outdatedModules)
            }

            invalidateKeys(outdatedModules)
        }

        private fun collectOutdatedModules(moduleEntity: ModuleEntity, storage: EntityStorage, outdatedModules: MutableSet<Module>) {
            val module = moduleEntity.findModule(storage) ?: return

            if (!outdatedModules.add(module)) return

            storage.referrers(moduleEntity.symbolicId, ModuleEntity::class.java).forEach {
                collectOutdatedModules(it, storage, outdatedModules)
            }
        }

        override fun libraryInfosRemoved(libraryInfos: Collection<LibraryInfo>) {
            val infos = libraryInfos.toHashSet()
            invalidateEntries(
                { _, v ->
                    v.libraryDependencyCandidates.any { candidate -> candidate.libraries.any { it in infos } }
                },
                // unable to check entities properly: an event could be not the last
                validityCondition = null
            )
        }
    }

    private inner class LibraryUsageIndex {
        private val modulesLibraryIsUsedIn: MultiMap<Library, Module> = runReadAction {
            val map: MultiMap<Library, Module> = MultiMap.createSet()
            val libraryCache = LibraryInfoCache.getInstance(project)
            for (module in ModuleManager.getInstance(project).modules) {
                checkCanceled()
                for (entry in ModuleRootManager.getInstance(module).orderEntries) {
                    if (entry !is LibraryOrderEntry) continue
                    val library = entry.library ?: continue
                    val keyLibrary = libraryCache.deduplicatedLibrary(library)
                    map.putValue(keyLibrary, module)
                }
            }

            map
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

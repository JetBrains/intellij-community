// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.caches.project

import com.intellij.ProjectTopics
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.*
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.containers.MultiMap
import com.intellij.util.messages.MessageBusConnection
import com.intellij.workspaceModel.ide.WorkspaceModelChangeListener
import com.intellij.workspaceModel.ide.WorkspaceModelTopics
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.findLibraryBridge
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.findModule
import com.intellij.workspaceModel.storage.EntityChange
import com.intellij.workspaceModel.storage.VersionedStorageChange
import com.intellij.workspaceModel.storage.bridgeEntities.api.LibraryEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.ModuleEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.SourceRootEntity
import org.jetbrains.kotlin.caches.project.cacheInvalidatingOnRootModifications
import org.jetbrains.kotlin.idea.base.projectStructure.LibraryInfoCache
import org.jetbrains.kotlin.idea.base.projectStructure.LibraryWrapper
import org.jetbrains.kotlin.idea.base.projectStructure.checkValidity
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.*
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.ModuleSourceInfo
import org.jetbrains.kotlin.idea.base.projectStructure.sourceModuleInfos
import org.jetbrains.kotlin.idea.base.util.caching.FineGrainedEntityCache
import org.jetbrains.kotlin.idea.base.util.caching.SynchronizedFineGrainedEntityCache
import org.jetbrains.kotlin.idea.base.util.caching.findModuleByEntityWithHack
import org.jetbrains.kotlin.idea.caches.trackers.KotlinCodeBlockModificationListener
import org.jetbrains.kotlin.idea.util.application.runReadAction
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.isCommon
import org.jetbrains.kotlin.progress.ProgressIndicatorAndCompilationCanceledStatus.checkCanceled
import org.jetbrains.kotlin.types.typeUtil.closure
import org.jetbrains.kotlin.utils.addToStdlib.cast
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import java.util.concurrent.ConcurrentHashMap

/** null-platform means that we should get all modules */
fun getModuleInfosFromIdeaModel(project: Project, platform: TargetPlatform? = null): List<IdeaModuleInfo> {
    val ideaModelInfosCache = getIdeaModelInfosCache(project)

    return if (platform != null && !platform.isCommon()) {
        ideaModelInfosCache.forPlatform(platform)
    } else {
        ideaModelInfosCache.allModules()
    }
}

@Suppress("DEPRECATION")
fun getIdeaModelInfosCache(project: Project): IdeaModelInfosCache =
    if (FineGrainedEntityCache.isFineGrainedCacheInvalidationEnabled) {
        project.service()
    } else {
        project.cacheInvalidatingOnRootModifications {
            collectModuleInfosFromIdeaModel(project)
        }
    }

interface IdeaModelInfosCache {
    fun forPlatform(platform: TargetPlatform): List<IdeaModuleInfo>

    fun allModules(): List<IdeaModuleInfo>

    fun getModuleInfosForModule(module: Module): Collection<ModuleSourceInfo>
    fun getLibraryInfosForLibrary(library: Library): Collection<LibraryInfo>
    fun getSdkInfoForSdk(sdk: Sdk): SdkInfo?

}

private class IdeaModelInfosCacheImpl(
    private val moduleSourceInfosByModules: MultiMap<Module, ModuleSourceInfo>,
    private val libraryInfosByLibraries: MultiMap<Library, LibraryInfo>,
    private val sdkInfosBySdks: Map<Sdk, SdkInfo>,
): IdeaModelInfosCache {
    private val resultByPlatform = ConcurrentHashMap<TargetPlatform, List<IdeaModuleInfo>>()

    private val moduleSourceInfos = moduleSourceInfosByModules.values().toList()
    private val libraryInfos = libraryInfosByLibraries.values().toList()
    private val sdkInfos = sdkInfosBySdks.values.toList()

    override fun forPlatform(platform: TargetPlatform): List<IdeaModuleInfo> {
        return resultByPlatform.getOrPut(platform) {
            mergePlatformModules(moduleSourceInfos, platform) + libraryInfos + sdkInfos
        }
    }

    override fun allModules(): List<IdeaModuleInfo> = moduleSourceInfos + libraryInfos + sdkInfos

    override fun getModuleInfosForModule(module: Module): Collection<ModuleSourceInfo> = moduleSourceInfosByModules[module]
    override fun getLibraryInfosForLibrary(library: Library): Collection<LibraryInfo> = libraryInfosByLibraries[library]
    override fun getSdkInfoForSdk(sdk: Sdk): SdkInfo? = sdkInfosBySdks[sdk]
}

internal fun Library.asLibraryEx(): LibraryEx {
    require(this is LibraryEx) { "Library '${name}' does not implement LibraryEx which is not expected" }
    return this
}

internal fun Library.wrap() = LibraryWrapper(this.asLibraryEx())

private fun collectModuleInfosFromIdeaModel(
    project: Project
): IdeaModelInfosCache {
    val ideaModules = ModuleManager.getInstance(project).modules.toList()

    //TODO: (module refactoring) include libraries that are not among dependencies of any module
    val ideaLibraries = ideaModules.flatMap { module ->
        ModuleRootManager.getInstance(module).orderEntries.filterIsInstance<LibraryOrderEntry>().map { entry ->
            entry.library?.let { LibraryWrapper(it as LibraryEx) }
        }
    }.filterNotNull().toSet()

    val sdksFromModulesDependencies = ideaModules.flatMap { module ->
        ModuleRootManager.getInstance(module).orderEntries.filterIsInstance<JdkOrderEntry>().map {
            it.jdk
        }
    }

    return IdeaModelInfosCacheImpl(
        moduleSourceInfosByModules = MultiMap.create<Module, ModuleSourceInfo>().also { moduleInfosByModules ->
            for (module in ideaModules) {
                checkCanceled()
                moduleInfosByModules.putValues(module, module.sourceModuleInfos)
            }
        },
        libraryInfosByLibraries = MultiMap.create<Library, LibraryInfo>().also { libraryInfosByLibraries ->
            for (libraryWrapper in ideaLibraries) {
                checkCanceled()
                val libraryInfos = LibraryInfoCache.getInstance(project)[libraryWrapper.library]
                libraryInfosByLibraries.putValues(libraryWrapper.library, libraryInfos)
            }
        },
        sdkInfosBySdks = LinkedHashMap<Sdk, SdkInfo>().also { sdkInfosBySdks ->
            fun setSdk(sdk: Sdk) = sdkInfosBySdks.set(sdk, SdkInfo(project, sdk))

            sdksFromModulesDependencies.forEach { if (it != null) setSdk(it) }
            runReadAction { ProjectJdkTable.getInstance().allJdks }.forEach { setSdk(it) }
        }
    )
}

class  FineGrainedIdeaModelInfosCache(private val project: Project): IdeaModelInfosCache, Disposable {
    private val moduleCache: ModuleCache
    private val libraryCache: LibraryCache
    private val sdkCache: SdkCache

    private val resultByPlatform: CachedValue<MutableMap<TargetPlatform, List<IdeaModuleInfo>>>
    private val allModules: CachedValue<List<IdeaModuleInfo>>

    private val modificationTracker = SimpleModificationTracker()

    init {
        moduleCache  = ModuleCache()
        libraryCache = LibraryCache()
        sdkCache = SdkCache()

        val cachedValuesManager = CachedValuesManager.getManager(project)
        resultByPlatform = cachedValuesManager.createCachedValue {
            CachedValueProvider.Result.create(ConcurrentHashMap<TargetPlatform, List<IdeaModuleInfo>>(), modificationTracker)
        }

        allModules = cachedValuesManager.createCachedValue {
            val ideaModuleInfos = moduleCache.fetchValues().flatten() + libraryCache.fetchValues().flatten() + sdkCache.fetchValues()
            CachedValueProvider.Result.create(ideaModuleInfos, modificationTracker)
        }

        Disposer.register(this, moduleCache)
        Disposer.register(this, libraryCache)
        Disposer.register(this, sdkCache)
    }

    override fun dispose() = Unit

    abstract inner class AbstractCache<Key: Any, Value: Any>(initializer: (AbstractCache<Key, Value>) -> Unit):
        SynchronizedFineGrainedEntityCache<Key, Value>(project, cleanOnLowMemory = false),
        WorkspaceModelChangeListener {

        @Volatile
        private var initializerRef: ((AbstractCache<Key, Value>) -> Unit)? = initializer
        private val initializerLock = Any()

        override fun subscribe() {
            val connection = project.messageBus.connect(this)
            WorkspaceModelTopics.getInstance(project).subscribeImmediately(connection, this)
            subscribe(connection)
        }

        protected open fun subscribe(connection: MessageBusConnection) = Unit

        fun fetchValues(): Collection<Value> {
            if (initializerRef != null) {
                synchronized(initializerLock) {
                    initializerRef?.let { it(this) }
                    initializerRef = null
                }
            }
            return values()
        }

        fun applyIfPossible(action: () -> Unit) {
            if (initializerRef != null) return

            action()
        }

        final override fun changed(event: VersionedStorageChange) {
            applyIfPossible {
                modelChanged(event)
            }
        }

        abstract fun modelChanged(event: VersionedStorageChange)
    }

    inner class ModuleCache : AbstractCache<Module, List<ModuleSourceInfo>>(
        initializer = {
            project.ideaModules().forEach(it::get)
        }) {

        override fun calculate(key: Module): List<ModuleSourceInfo> = key.sourceModuleInfos

        override fun checkKeyValidity(key: Module) {
            key.checkValidity()
        }

        override fun modelChanged(event: VersionedStorageChange) {
            val storageBefore = event.storageBefore
            val storageAfter = event.storageAfter

            val moduleChanges = event.getChanges(ModuleEntity::class.java)
            val sourceRootChanges = event.getChanges(SourceRootEntity::class.java)

            if (moduleChanges.isEmpty() && sourceRootChanges.isEmpty()) {
                return
            }

            val modulesToRegister = LinkedHashSet<Module>()
            val modulesToRemove = LinkedHashSet<Module>()

            fun Module.scheduleRegister() = modulesToRegister.add(this)
            fun Module.scheduleRemove() = modulesToRemove.add(this)

            for (moduleChange in moduleChanges) {
                when (moduleChange) {
                    is EntityChange.Added -> {
                        storageAfter.findModuleByEntityWithHack(moduleChange.newEntity, project)?.scheduleRegister()
                    }

                    is EntityChange.Removed -> moduleChange.entity.findModule(storageBefore)?.scheduleRemove()
                    is EntityChange.Replaced -> {
                        moduleChange.oldEntity.findModule(storageBefore)?.scheduleRemove()
                        moduleChange.newEntity.findModule(storageAfter)?.scheduleRegister()
                    }
                }
            }

            val modulesToUpdate = mutableListOf<Module>()

            for (sourceRootChange in sourceRootChanges) {
                val modules: List<Module> = when (sourceRootChange) {
                    is EntityChange.Added -> listOfNotNull(
                        storageAfter.findModuleByEntityWithHack(sourceRootChange.newEntity.contentRoot.module, project)
                    )

                    is EntityChange.Removed -> listOfNotNull(sourceRootChange.entity.contentRoot.module.findModule(storageBefore))
                    is EntityChange.Replaced -> listOfNotNull(
                        sourceRootChange.oldEntity.contentRoot.module.findModule(storageBefore),
                        sourceRootChange.newEntity.contentRoot.module.findModule(storageAfter)
                    )
                }

                for (module in modules) {
                    if (module in modulesToRemove && module !in modulesToRegister) {
                        // The module itself is gone. No need in updating it because of source root modification.
                        // Note that on module deletion, both module and source root deletion events arrive.
                        continue
                    }

                    modulesToUpdate.add(module)
                }
            }

            for (module in modulesToUpdate) {
                module.scheduleRemove()
                module.scheduleRegister()
            }

            invalidateKeys(modulesToRemove)
            modulesToRegister.forEach { get(it) }

            incModificationCount()
        }
    }

    inner class LibraryCache : AbstractCache<Library, List<LibraryInfo>>(
        initializer = {
            val cache = it.cast<LibraryCache>()
            project.ideaModules().forEach(cache::calculateLibrariesForModule)
        }) {

        override fun calculate(key: Library): List<LibraryInfo> = LibraryInfoCache.getInstance(project)[key]

        override fun checkKeyValidity(key: Library) {
            key.checkValidity()
        }

        override fun modelChanged(event: VersionedStorageChange) {
            val storageBefore = event.storageBefore
            val libraryChanges = event.getChanges(LibraryEntity::class.java)

            if (libraryChanges.isEmpty()) return

            val outdatedLibraries: List<Library> = libraryChanges.asSequence()
                .mapNotNull { it.oldEntity }
                .mapNotNull { it.findLibraryBridge(storageBefore) }
                .toList()

            if (outdatedLibraries.isNotEmpty()) {
                invalidateEntries({ k, _ -> k in outdatedLibraries })
            }

            // force calculations
            project.ideaModules().forEach(::calculateLibrariesForModule)

            incModificationCount()
        }

        private fun calculateLibrariesForModule(module: Module) {
            checkCanceled()
            val orderEntries = ModuleRootManager.getInstance(module).orderEntries
            for (orderEntry in orderEntries) {
                orderEntry.safeAs<LibraryOrderEntry>()?.library.safeAs<LibraryEx>()?.let(::get)
            }
        }
    }

    inner class SdkCache : AbstractCache<Sdk, SdkInfo>(
        initializer = {
            val modules = project.ideaModules()
            project.allSdks(modules).forEach(it::get)
        }
    ),
                           ProjectJdkTable.Listener,
                           ModuleRootListener {

        override fun calculate(key: Sdk): SdkInfo = SdkInfo(project, key)

        override fun subscribe(connection: MessageBusConnection) {
            connection.subscribe(ProjectJdkTable.JDK_TABLE_TOPIC, this)
            connection.subscribe(ProjectTopics.PROJECT_ROOTS, this)
        }

        override fun checkKeyValidity(key: Sdk) = Unit

        override fun jdkAdded(jdk: Sdk) {
            applyIfPossible {
                get(jdk)

                incModificationCount()
            }
        }

        override fun jdkRemoved(jdk: Sdk) {
            applyIfPossible {
                invalidateKeys(listOf(jdk))

                incModificationCount()
            }
        }

        override fun jdkNameChanged(jdk: Sdk, previousName: String) {
            applyIfPossible {
                invalidateKeys(listOf(jdk))

                // force calculation
                get(jdk)
                incModificationCount()
            }
        }

        override fun rootsChanged(event: ModuleRootEvent) {
            applyIfPossible {
                // SDK could be changed (esp in tests) out of message bus subscription
                val sdks = runReadAction { ProjectJdkTable.getInstance().allJdks }
                invalidateEntries({ k, _ -> k !in sdks })

                // force calculation
                sdks.forEach(::get)

                incModificationCount()
            }
        }

        override fun modelChanged(event: VersionedStorageChange) {
            val storageBefore = event.storageBefore
            val storageAfter = event.storageAfter
            val moduleChanges = event.getChanges(ModuleEntity::class.java).ifEmpty { return }

            val outdatedModuleSdks: Set<Sdk> = moduleChanges.asSequence()
                .mapNotNull { it.oldEntity }
                .mapNotNull { it.findModule(storageBefore) }
                .flatMapTo(hashSetOf(), ::moduleSdks)

            if (outdatedModuleSdks.isNotEmpty()) {
                invalidateKeys(outdatedModuleSdks)
            }

            val updatedModuleSdks: Set<Sdk> = moduleChanges.asSequence()
                .mapNotNull { it.newEntity }
                .mapNotNull { storageAfter.findModuleByEntityWithHack(it, project) }
                .flatMapTo(hashSetOf(), ::moduleSdks)

            updatedModuleSdks.forEach(::get)

            incModificationCount()
        }
    }

    private fun incModificationCount() {
        modificationTracker.incModificationCount()
        KotlinCodeBlockModificationListener.getInstance(project).incModificationCount()
    }

    override fun forPlatform(platform: TargetPlatform): List<IdeaModuleInfo> =
        if (platform.isCommon()) {
            allModules.value
        } else {
            resultByPlatform.value.getOrPut(platform) {
                val moduleSourceInfos = moduleCache.fetchValues().flatten()
                val platformModules = mergePlatformModules(moduleSourceInfos, platform)
                val libraryInfos = libraryCache.fetchValues().flatten()
                val sdkInfos = sdkCache.fetchValues()
                val ideaModuleInfos = platformModules + libraryInfos + sdkInfos
                ideaModuleInfos
            }
        }

    override fun allModules(): List<IdeaModuleInfo> = allModules.value

    override fun getModuleInfosForModule(module: Module): Collection<ModuleSourceInfo> = moduleCache[module]

    override fun getLibraryInfosForLibrary(library: Library): Collection<LibraryInfo> = libraryCache[library]

    override fun getSdkInfoForSdk(sdk: Sdk): SdkInfo = sdkCache[sdk]
}

private fun mergePlatformModules(
    allModules: List<ModuleSourceInfo>,
    platform: TargetPlatform
): List<IdeaModuleInfo> {
    if (platform.isCommon()) return allModules

    val knownCommonModules = mutableSetOf<ModuleSourceInfo>()
    val platformModules = allModules.mapNotNull { module ->
        if (module.platform != platform || module.expectedBy.isEmpty() || module in knownCommonModules)
            return@mapNotNull null

        val commonModules = module.expectedBy
            .onEach { commonModule -> knownCommonModules.add(commonModule) }
            .closure { it.expectedBy.onEach { commonModule -> knownCommonModules.add(commonModule) } }
            .toList()

        PlatformModuleInfo(module, commonModules)
    }.filter { it.platformModule !in knownCommonModules }

    val rest = allModules - platformModules.flatMapTo(mutableSetOf()) { it.containedModules }
    return rest + platformModules
}

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
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerBridgeImpl.Companion.findModuleByEntity
import com.intellij.workspaceModel.storage.VersionedStorageChange
import com.intellij.workspaceModel.storage.bridgeEntities.api.LibraryEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.ModuleEntity
import org.jetbrains.kotlin.caches.project.cacheInvalidatingOnRootModifications
import org.jetbrains.kotlin.idea.base.projectStructure.LibraryInfoCache
import org.jetbrains.kotlin.idea.base.projectStructure.LibraryWrapper
import org.jetbrains.kotlin.idea.base.projectStructure.checkValidity
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.*
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.ModuleSourceInfo
import org.jetbrains.kotlin.idea.base.projectStructure.sourceModuleInfos
import org.jetbrains.kotlin.idea.base.util.caching.FineGrainedEntityCache
import org.jetbrains.kotlin.idea.base.util.caching.SynchronizedFineGrainedEntityCache
import org.jetbrains.kotlin.idea.util.application.runReadAction
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.isCommon
import org.jetbrains.kotlin.progress.ProgressIndicatorAndCompilationCanceledStatus.checkCanceled
import org.jetbrains.kotlin.types.typeUtil.closure
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
        val ideaModules = project.ideaModules()
        moduleCache  = ModuleCache(ideaModules)
        libraryCache = LibraryCache(ideaModules)
        sdkCache = SdkCache(ideaModules)

        val cachedValuesManager = CachedValuesManager.getManager(project)
        resultByPlatform = cachedValuesManager.createCachedValue {
            CachedValueProvider.Result.create(ConcurrentHashMap<TargetPlatform, List<IdeaModuleInfo>>(), modificationTracker)
        }

        allModules = cachedValuesManager.createCachedValue {
            val ideaModuleInfos = moduleCache.values().flatten() + libraryCache.values().flatten() + sdkCache.values()
            CachedValueProvider.Result.create(ideaModuleInfos, modificationTracker)
        }

        Disposer.register(this, moduleCache)
        Disposer.register(this, libraryCache)
        Disposer.register(this, sdkCache)
    }

    override fun dispose() = Unit

    abstract inner class AbstractCache<Key: Any, Value: Any>:
        SynchronizedFineGrainedEntityCache<Key, Value>(project, cleanOnLowMemory = false),
        WorkspaceModelChangeListener {

        override fun subscribe() {
            val connection = project.messageBus.connect(this)
            WorkspaceModelTopics.getInstance(project).subscribeImmediately(connection, this)
            subscribe(connection)
        }

        protected open fun subscribe(connection: MessageBusConnection) {

        }
    }

    inner class ModuleCache(modules: Array<out Module>) : AbstractCache<Module, List<ModuleSourceInfo>>() {

        init {
            modules.forEach(::get)
        }

        override fun calculate(key: Module): List<ModuleSourceInfo> = key.sourceModuleInfos

        override fun checkKeyValidity(key: Module) {
            key.checkValidity()
        }

        override fun changed(event: VersionedStorageChange) {
            val storageBefore = event.storageBefore
            val storageAfter = event.storageAfter
            val moduleChanges = event.getChanges(ModuleEntity::class.java).ifEmpty { return }

            val outdatedModules: List<Module> = moduleChanges.asSequence()
                .mapNotNull { it.oldEntity }
                .mapNotNull { storageBefore.findModuleByEntity(it) }
                .toList()

            val updatedModules = moduleChanges.asSequence()
                .mapNotNull { it.newEntity }
                .mapNotNull { storageAfter.findModuleByEntity(it) }
                .toList()

            if (outdatedModules.isNotEmpty()) {
                invalidateKeys(outdatedModules)
            }

            // force calculations
            updatedModules.forEach(::get)

            modificationTracker.incModificationCount()
        }
    }

    inner class LibraryCache(modules: Array<out Module>) : AbstractCache<Library, List<LibraryInfo>>() {

        init {
            modules.forEach(::calculateLibrariesForModule)
        }

        override fun calculate(key: Library): List<LibraryInfo> = LibraryInfoCache.getInstance(project)[key]

        override fun checkKeyValidity(key: Library) {
            key.checkValidity()
        }

        override fun changed(event: VersionedStorageChange) {
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

            modificationTracker.incModificationCount()
        }

        private fun calculateLibrariesForModule(module: Module) {
            checkCanceled()
            val orderEntries = ModuleRootManager.getInstance(module).orderEntries
            for (orderEntry in orderEntries) {
                orderEntry.safeAs<LibraryOrderEntry>()?.library.safeAs<LibraryEx>()?.let(::get)
            }
        }
    }

    inner class SdkCache(modules: Array<out Module>) : AbstractCache<Sdk, SdkInfo>(),
                                                       ProjectJdkTable.Listener,
                                                       ModuleRootListener {

        init {
            project.allSdks(modules).forEach(::get)
        }

        override fun calculate(key: Sdk): SdkInfo = SdkInfo(project, key)

        override fun subscribe(connection: MessageBusConnection) {
            connection.subscribe(ProjectJdkTable.JDK_TABLE_TOPIC, this)
            connection.subscribe(ProjectTopics.PROJECT_ROOTS, this)
        }

        override fun checkKeyValidity(key: Sdk) = Unit

        override fun jdkAdded(jdk: Sdk) {
            get(jdk)

            modificationTracker.incModificationCount()
        }

        override fun jdkRemoved(jdk: Sdk) {
            invalidateKeys(listOf(jdk))

            modificationTracker.incModificationCount()
        }

        override fun jdkNameChanged(jdk: Sdk, previousName: String) {
            invalidateKeys(listOf(jdk))

            // force calculation
            get(jdk)
            modificationTracker.incModificationCount()
        }

        override fun rootsChanged(event: ModuleRootEvent) {
            // SDK could be changed (esp in tests) out of message bus subscription
            val sdks = project.allSdks()
            invalidateEntries({ k, _ -> k !in sdks  })

            // force calculation
            sdks.forEach(::get)

            modificationTracker.incModificationCount()
        }

        override fun changed(event: VersionedStorageChange) {
            val storageBefore = event.storageBefore
            val storageAfter = event.storageAfter
            val moduleChanges = event.getChanges(ModuleEntity::class.java).ifEmpty { return }

            val outdatedModuleSdks: Set<Sdk> = moduleChanges.asSequence()
                .mapNotNull { it.oldEntity }
                .mapNotNull { storageBefore.findModuleByEntity(it) }
                .flatMapTo(hashSetOf(), ::moduleSdks)

            if (outdatedModuleSdks.isNotEmpty()) {
                invalidateKeys(outdatedModuleSdks)
            }

            val updatedModuleSdks: Set<Sdk> = moduleChanges.asSequence()
                .mapNotNull { it.newEntity }
                .mapNotNull { storageAfter.findModuleByEntity(it) }
                .flatMapTo(hashSetOf(), ::moduleSdks)

            updatedModuleSdks.forEach(::get)

            modificationTracker.incModificationCount()
        }
    }

    override fun forPlatform(platform: TargetPlatform): List<IdeaModuleInfo> =
        if (platform.isCommon()) {
            allModules.value
        } else {
            resultByPlatform.value.getOrPut(platform) {
                val platformModules = mergePlatformModules(moduleCache.values().flatten(), platform)
                val libraryInfos = libraryCache.values().flatten()
                val sdkInfos = sdkCache.values()
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

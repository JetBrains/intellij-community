// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.caches.project

import com.intellij.java.workspace.entities.JavaModuleSettingsEntity
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.platform.backend.workspace.WorkspaceModelChangeListener
import com.intellij.platform.backend.workspace.WorkspaceModelTopics
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.SdkEntity
import com.intellij.platform.workspace.jps.entities.SourceRootEntity
import com.intellij.platform.workspace.storage.EntityChange
import com.intellij.platform.workspace.storage.VersionedStorageChange
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.messages.MessageBusConnection
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.findModule
import org.jetbrains.kotlin.idea.base.projectStructure.LibraryInfoCache
import org.jetbrains.kotlin.idea.base.projectStructure.ProjectStructureProviderService
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.*
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.ModuleSourceInfo
import org.jetbrains.kotlin.idea.base.projectStructure.sourceModuleInfos
import org.jetbrains.kotlin.idea.base.util.caching.*
import org.jetbrains.kotlin.idea.base.util.caching.SynchronizedFineGrainedEntityCache
import org.jetbrains.kotlin.idea.base.util.caching.findSdkBridge
import org.jetbrains.kotlin.idea.base.util.caching.getChanges
import org.jetbrains.kotlin.idea.base.util.caching.newEntity
import org.jetbrains.kotlin.idea.base.util.caching.oldEntity
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.isCommon
import java.util.concurrent.ConcurrentHashMap

/** null-platform means that we should get all modules */
fun getModuleInfosFromIdeaModel(project: Project, platform: TargetPlatform? = null): List<IdeaModuleInfo> {
    return runReadAction {
        val ideaModelInfosCache = getIdeaModelInfosCache(project)
        if (platform != null && !platform.isCommon()) {
            ideaModelInfosCache.forPlatform(platform)
        } else {
            ideaModelInfosCache.allModules()
        }
    }
}

fun getIdeaModelInfosCache(project: Project): IdeaModelInfosCache = project.service()

interface IdeaModelInfosCache {
    fun forPlatform(platform: TargetPlatform): List<IdeaModuleInfo>

    fun allModules(): List<IdeaModuleInfo>

    fun getModuleInfosForModule(module: Module): Collection<ModuleSourceInfo>
    fun getLibraryInfosForLibrary(library: Library): Collection<LibraryInfo>
    fun getSdkInfoForSdk(sdk: Sdk): SdkInfo?

}

class FineGrainedIdeaModelInfosCache(private val project: Project) : IdeaModelInfosCache, Disposable {
    private val moduleCache = ModuleCache()
    private val sdkCache = SdkCache()

    private val resultByPlatform: CachedValue<MutableMap<TargetPlatform, List<IdeaModuleInfo>>>
    private val modulesAndSdk: CachedValue<List<IdeaModuleInfo>>
    private val libraries: CachedValue<Collection<LibraryInfo>>
    private val modificationTracker = SimpleModificationTracker()

    init {
        val cachedValuesManager = CachedValuesManager.getManager(project)
        resultByPlatform = cachedValuesManager.createCachedValue {
            CachedValueProvider.Result.create(
                ConcurrentHashMap<TargetPlatform, List<IdeaModuleInfo>>(),
                modificationTracker,
                LibraryInfoCache.getInstance(project).removedLibraryInfoTracker(),
            )
        }

        modulesAndSdk = cachedValuesManager.createCachedValue {
            val ideaModuleInfos = moduleCache.fetchValues().flatten().also {
                it.checkValidity { "modulesAndSdk: modules calculation" }
            } + sdkCache.fetchValues().also {
                it.checkValidity { "modulesAndSdk: sdks calculation" }
            }
            CachedValueProvider.Result.create(ideaModuleInfos, modificationTracker)
        }

        libraries = cachedValuesManager.createCachedValue {
            val libraryCache = LibraryInfoCache.getInstance(project)
            val collectedLibraries = mutableSetOf<LibraryInfo>()
            for (module in ModuleManager.getInstance(project).modules) {
                ProgressManager.checkCanceled()
                for (entry in ModuleRootManager.getInstance(module).orderEntries) {
                    if (entry !is LibraryOrderEntry) continue
                    val library = entry.library ?: continue
                    collectedLibraries += libraryCache[library]
                }
            }

            collectedLibraries.checkValidity { "libraries calculation" }

            CachedValueProvider.Result.create(collectedLibraries, libraryCache.removedLibraryInfoTracker(), modificationTracker)
        }

        Disposer.register(this, moduleCache)
        Disposer.register(this, sdkCache)
    }

    override fun dispose() = Unit

    abstract inner class AbstractCache<Key : Any, Value : Any>(initializer: (AbstractCache<Key, Value>) -> Unit) :
        SynchronizedFineGrainedEntityCache<Key, Value>(project),
        WorkspaceModelChangeListener {

        @Volatile
        private var initializerRef: ((AbstractCache<Key, Value>) -> Unit)? = initializer
        private val initializerLock = Any()

        init {
            initialize()
        }

        override fun subscribe() {
            val connection = project.messageBus.connect(this)
            connection.subscribe(WorkspaceModelTopics.CHANGED, this)
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

        override fun beforeChanged(event: VersionedStorageChange) {
            applyIfPossible {
                beforeModelChanged(event)
            }
        }

        final override fun changed(event: VersionedStorageChange) {
            applyIfPossible {
                modelChanged(event)
            }
        }

        open fun beforeModelChanged(event: VersionedStorageChange) {}
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

        override fun beforeModelChanged(event: VersionedStorageChange) {
            val storageBefore = event.storageBefore

            val moduleChanges = event.getChanges<ModuleEntity>().takeIf { it.isNotEmpty() } ?: return

            val modulesToRemove = LinkedHashSet<Module>()
            for (moduleChange in moduleChanges) {
                if (moduleChange is EntityChange.Removed) {
                    moduleChange.oldEntity.findModule(storageBefore)?.let { modulesToRemove.add(it) }
                }
            }

            if (modulesToRemove.isEmpty()) return

            invalidateKeys(modulesToRemove)

            incModificationCount()
        }

        override fun modelChanged(event: VersionedStorageChange) {
            val storageBefore = event.storageBefore
            val storageAfter = event.storageAfter

            val moduleChanges = event.getChanges<ModuleEntity>()
            val sourceRootChanges = event.getChanges<SourceRootEntity>()
            val moduleSettingChanges = event.getChanges<JavaModuleSettingsEntity>()

            if (moduleChanges.isEmpty() && sourceRootChanges.isEmpty() && moduleSettingChanges.isEmpty()) {
                return
            }

            val modulesToRegister = LinkedHashSet<Module>()
            val modulesToRemove = LinkedHashSet<Module>()

            fun Module.scheduleRegister() = modulesToRegister.add(this)
            fun Module.scheduleRemove() = modulesToRemove.add(this)

            for (moduleChange in moduleChanges) {
                moduleChange.oldEntity()?.findModule(storageBefore)?.scheduleRemove()
                moduleChange.newEntity()?.findModule(storageAfter)?.scheduleRegister()
            }

            val changedModules = mutableSetOf<Module>()
            for (sourceRootChange in sourceRootChanges) {
                sourceRootChange.oldEntity()?.contentRoot?.module?.findModule(storageBefore)?.let {
                    changedModules.add(it)
                }
                sourceRootChange.newEntity()?.contentRoot?.module?.findModule(storageAfter)?.let {
                    changedModules.add(it)
                }
            }

            for (moduleSettingChange in moduleSettingChanges) {
                moduleSettingChange.oldEntity()?.module?.findModule(storageBefore)?.let {
                    changedModules.add(it)
                }
                moduleSettingChange.newEntity()?.module?.findModule(storageAfter)?.let {
                    changedModules.add(it)
                }
            }

            for (module in changedModules) {
                if (module in modulesToRemove && module !in modulesToRegister) {
                    // The module itself is gone. No need in updating it because of source root modification.
                    // Note that on module deletion, both module and source root deletion events arrive.
                    continue
                }

                module.scheduleRemove()
                module.scheduleRegister()
            }

            invalidateKeys(modulesToRemove)
            modulesToRegister.forEach { get(it) }

            incModificationCount()
        }
    }

    inner class SdkCache : AbstractCache<Sdk, SdkInfo>(
        initializer = {
            val modules = project.ideaModules()
            project.allSdks(modules).forEach(it::get)
        }
    ), ModuleRootListener {

        override fun calculate(key: Sdk): SdkInfo = SdkInfo(project, key)

        override fun subscribe(connection: MessageBusConnection) {
            connection.subscribe(ModuleRootListener.TOPIC, this)
        }

        override fun checkKeyValidity(key: Sdk) = Unit

        override fun rootsChanged(event: ModuleRootEvent) {
            if (event.isCausedByWorkspaceModelChangesOnly) return

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
            val moduleChanges = event.getChanges<ModuleEntity>()
            val sdkChanges = event.getChanges<SdkEntity>()

            if (moduleChanges.isEmpty() && sdkChanges.isEmpty()) return

            val outdatedModuleSdks: Set<Sdk> = moduleChanges.asSequence()
                .mapNotNull { it.oldEntity }
                .mapNotNull { it.findModule(storageBefore) }
                .flatMapTo(hashSetOf(), ::moduleSdks) +
                    sdkChanges.mapNotNull { it.oldEntity?.findSdkBridge(storageBefore) }

            if (outdatedModuleSdks.isNotEmpty()) {
                invalidateKeys(outdatedModuleSdks)
            }

            val updatedModuleSdks: Set<Sdk> = moduleChanges.asSequence()
                .mapNotNull { it.newEntity }
                .mapNotNull { it.findModule(storageAfter) }
                .flatMapTo(hashSetOf(), ::moduleSdks) +
                    sdkChanges.mapNotNull { it.newEntity?.findSdkBridge(storageAfter) }

            updatedModuleSdks.forEach(::get)

            incModificationCount()
        }
    }

    private fun incModificationCount() {
        modificationTracker.incModificationCount()
        ProjectStructureProviderService.getInstance(project).incOutOfBlockModificationCount()
    }

    override fun forPlatform(platform: TargetPlatform): List<IdeaModuleInfo> =
        if (platform.isCommon()) {
            allModules()
        } else {
            resultByPlatform.value.getOrPut(platform) {
                val moduleSourceInfos = moduleCache.fetchValues().flatten()
                val platformModules = mergePlatformModules(moduleSourceInfos, platform)
                val libraryInfos = libraries.value
                val sdkInfos = sdkCache.fetchValues()
                val ideaModuleInfos = platformModules + libraryInfos + sdkInfos
                ideaModuleInfos.checkValidity { "resultByPlatform $platform calculation" }
                ideaModuleInfos
            }.also {
                it.checkValidity { "forPlatform $platform" }
            }
        }

    override fun allModules(): List<IdeaModuleInfo> = (modulesAndSdk.value + libraries.value).also {
        it.checkValidity { "allModules" }
    }

    override fun getModuleInfosForModule(module: Module): Collection<ModuleSourceInfo> = moduleCache[module]

    override fun getLibraryInfosForLibrary(library: Library): Collection<LibraryInfo> = LibraryInfoCache.getInstance(project)[library]

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

private fun <T> Collection<T>.closure(preserveOrder: Boolean = false, f: (T) -> Collection<T>): Collection<T> {
    if (isEmpty()) return this

    val result = if (preserveOrder) LinkedHashSet(this) else HashSet(this)
    var elementsToCheck = result
    var oldSize = 0
    while (result.size > oldSize) {
        oldSize = result.size
        val toAdd = if (preserveOrder) linkedSetOf() else hashSetOf<T>()
        elementsToCheck.forEach { toAdd.addAll(f(it)) }
        result.addAll(toAdd)
        elementsToCheck = toAdd
    }

    return result
}
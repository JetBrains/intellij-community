// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.projectStructure.provider

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.platform.workspace.jps.entities.LibraryId
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.jps.entities.SdkId
import com.intellij.util.messages.MessageBus
import org.jetbrains.kotlin.analysis.api.platform.analysisMessageBus
import org.jetbrains.kotlin.analysis.api.platform.modification.*
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaSourceModule
import org.jetbrains.kotlin.idea.base.fir.projectStructure.modules.KaEntityBasedModuleCreationData
import org.jetbrains.kotlin.idea.base.fir.projectStructure.modules.library.KaLibraryModuleImpl
import org.jetbrains.kotlin.idea.base.fir.projectStructure.modules.library.KaLibrarySdkModuleImpl
import org.jetbrains.kotlin.idea.base.fir.projectStructure.modules.source.KaSourceModuleImpl
import org.jetbrains.kotlin.idea.base.projectStructure.KaSourceModuleKind
import java.util.concurrent.ConcurrentHashMap

@RequiresOptIn("KaModule should be created only by K2IDEProjectStructureProviderCache")
internal annotation class InternalKaModuleConstructor

/**
 * Caches certain types of `KaModule` by their workspace model `com.intellij.platform.workspace.storage.SymbolicEntityId`.
 *
 * The cache is invalidated by a `KotlinModificationEvent`.
 *
 * @see [isItSafeToCacheModules] for notes on cache invalidation order
 * @see KTIJ-33277
 */
@Service(Service.Level.PROJECT)
internal class K2IDEProjectStructureProviderCache(
    private val project: Project,
) : Disposable {
    private val libraryCache = ConcurrentHashMap<LibraryId, KaLibraryModuleImpl>()
    private val sdkCache = ConcurrentHashMap<SdkId, KaLibrarySdkModuleImpl>()

    private val productionSourceCache = ConcurrentHashMap<ModuleId, KaSourceModule>()
    private val testSourceCache = ConcurrentHashMap<ModuleId, KaSourceModule>()

    private val analysisMessageBus: MessageBus = project.analysisMessageBus

    private val sdkAndLibrariesTracker = SimpleModificationTracker()
    private val sourcesTracker = SimpleModificationTracker()

    /**
     * A tracker is incremented when corresponding modules are removed from [K2IDEProjectStructureProviderCache]
     */
    fun getCacheSdkAndLibrariesTracker(): ModificationTracker = sdkAndLibrariesTracker

    /**
     * A tracker is incremented when corresponding modules are removed from [K2IDEProjectStructureProviderCache]
     */
    fun getCacheSourcesTracker(): ModificationTracker = sourcesTracker

    init {
        analysisMessageBus.connect(this).apply {
            subscribe(KotlinModificationEvent.TOPIC, KotlinModificationEventListener { event ->
                when (event) {
                    KotlinGlobalModuleStateModificationEvent -> invalidateAllModuleCaches()
                    is KotlinModuleStateModificationEvent -> invalidateCaches(event.module)
                    is KotlinGlobalSourceModuleStateModificationEvent -> invalidateSourceModuleCaches()
                    is KotlinCodeFragmentContextModificationEvent -> {}
                    KotlinGlobalSourceOutOfBlockModificationEvent -> {}
                    is KotlinModuleOutOfBlockModificationEvent -> {}
                    KotlinGlobalScriptModuleStateModificationEvent -> {
                        /* scripts are not cached */
                    }
                }
            })
        }
    }

    /**
     * Invalidates the cache of the provided module and all modules that may depend on it.
     *
     * For source modules, all caches are invalidated, as computing dependent modules may be expensive.
     * This should not cause issues, as the primary purpose of this cache is to optimize long static operations like finding usages.
     */
    private fun invalidateCaches(module: KaModule) {
        when (module) {
            is KaSourceModuleImpl -> {
                invalidateSourceModuleCaches()
            }

            is KaLibraryModuleImpl -> {
                libraryCache.remove(module.entityId)
                sdkAndLibrariesTracker.incModificationCount()
                invalidateSourceModuleCaches()
            }

            is KaLibrarySdkModuleImpl -> {
                sdkCache.remove(module.entityId)
                sdkAndLibrariesTracker.incModificationCount()
                invalidateSourceModuleCaches()
            }
        }
    }

    private fun invalidateAllModuleCaches() {
        invalidateSourceModuleCaches()
        invalidateLibraryModuleCaches()
    }

    private fun invalidateLibraryModuleCaches() {
        libraryCache.clear()
        sdkCache.clear()
        sdkAndLibrariesTracker.incModificationCount()
    }

    fun invalidateSourceModuleCaches() {
        productionSourceCache.clear()
        testSourceCache.clear()
        sourcesTracker.incModificationCount()
    }

    @OptIn(InternalKaModuleConstructor::class)
    fun cachedKaLibraryModule(id: LibraryId): KaLibraryModuleImpl {
        if (!isItSafeToCacheModules()) return KaLibraryModuleImpl(id, project, creationData())
        return libraryCache.computeIfAbsent(id) { KaLibraryModuleImpl(id, project, creationData()) }
    }

    @OptIn(InternalKaModuleConstructor::class)
    fun cachedKaSdkModule(id: SdkId): KaLibrarySdkModuleImpl {
        if (!isItSafeToCacheModules()) return KaLibrarySdkModuleImpl(project, id, creationData())
        return sdkCache.computeIfAbsent(id) { KaLibrarySdkModuleImpl(project, id, creationData()) }
    }

    @OptIn(InternalKaModuleConstructor::class)
    fun cachedKaSourceModule(id: ModuleId, kind: KaSourceModuleKind): KaSourceModule {
        if (!isItSafeToCacheModules()) return KaSourceModuleImpl(id, kind, project, creationData())
        val cache = moduleCacheForKind(kind)
        return cache.computeIfAbsent(id) { KaSourceModuleImpl(id, kind, project, creationData()) }
    }

    private fun creationData() = KaEntityBasedModuleCreationData(
        createdWithoutCaching = isItSafeToCacheModules(),
        createdSourceTrackerValue = sourcesTracker.modificationCount,
        createdLibrariesTrackerValue = sdkAndLibrariesTracker.modificationCount,
    )

    /**
     * Checks if it is safe to cache a `KaModule` inside `K2IDEProjectStructureProviderCache` and `K2IDEProjectStructureProvider`.
     *
     * The primary method in the Analysis API to notify about `KaModule` changes is `KotlinModificationEvent`, which is sent via `MessageBus`.
     * There are multiple listeners within the Analysis API and the Kotlin Plugin that invalidate their own caches for specified modules.
     * Some of these listeners may use `K2IDEProjectStructureProvider` internally to query a `KaModule`.
     * One such listener is inside `K2IDEProjectStructureProviderCache`, which invalidates itself, but we cannot ensure the proper ordering of these events.
     * Ideally, the event listeners from `K2IDEProjectStructureProviderCache` should be processed last
     * so that our caches are cleaned after all other listeners have processed them. Before cleaning the `K2IDEProjectStructureProviderCache`, other listeners may access it safely.
     * However, since the listener's position is undefined, listeners that may come after might try accessing it and repopulate the cache with invalid modules.
     * To prevent `K2IDEProjectStructureProviderCache` from caching already invalidated modules, we do not cache it if we know some listeners have not been processed yet,
     * and simply return an uncached version, which is equal in terms of the `equals` operator to the invalidated one.
     *
     * This function should only be used by `K2IDEProjectStructureProviderCache` and `K2IDEProjectStructureProvider`.
     */
    fun isItSafeToCacheModules(): Boolean {
        return !analysisMessageBus.hasUndeliveredEvents(KotlinModificationEvent.TOPIC)
    }

    private fun moduleCacheForKind(kind: KaSourceModuleKind): ConcurrentHashMap<ModuleId, KaSourceModule> {
        return when (kind) {
            KaSourceModuleKind.PRODUCTION -> productionSourceCache
            KaSourceModuleKind.TEST -> testSourceCache
        }
    }

    override fun dispose() {}

    companion object {
        fun getInstance(project: Project): K2IDEProjectStructureProviderCache =
            project.service<K2IDEProjectStructureProviderCache>()
    }
}

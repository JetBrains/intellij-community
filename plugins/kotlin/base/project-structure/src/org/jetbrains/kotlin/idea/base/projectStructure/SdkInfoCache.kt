// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.projectStructure

import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.platform.backend.workspace.WorkspaceModelChangeListener
import com.intellij.platform.backend.workspace.WorkspaceModelTopics
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.SdkEntity
import com.intellij.platform.workspace.storage.VersionedStorageChange
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.IdeaModuleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.LibraryInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.SdkInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.allSdks
import org.jetbrains.kotlin.idea.base.util.caching.LockFreeFineGrainedEntityCache
import org.jetbrains.kotlin.idea.base.util.caching.findSdkBridge
import org.jetbrains.kotlin.utils.addIfNotNull
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import java.util.*

/**
 * Maintains and caches mapping ModuleInfo -> SdkInfo *form its dependencies*
 * (note that this SDK might be different from Project SDK)
 *
 * Cache is needed because one and the same library might (and usually does)
 * participate as dependency in several modules, so if someone queries a lot
 * of modules for their SDKs (ex.: determine built-ins for each module in a
 * project), we end up inspecting one and the same dependency multiple times.
 *
 * With projects with abnormally high amount of dependencies this might lead
 * to performance issues.
 */
interface SdkInfoCache {
    fun findOrGetCachedSdk(moduleInfo: ModuleInfo): SdkInfo?

    companion object {
        fun getInstance(project: Project): SdkInfoCache = project.service()
    }
}

internal class SdkInfoCacheImpl(project: Project) :
    SdkInfoCache,
    LockFreeFineGrainedEntityCache<ModuleInfo, SdkInfoCacheImpl.SdkDependency>(project, doSelfInitialization = false, cleanOnLowMemory = true),
    ModuleRootListener,
    LibraryInfoListener,
    WorkspaceModelChangeListener {

    @JvmInline
    value class SdkDependency(val sdk: SdkInfo?)

    override fun subscribe() {
        val connection = project.messageBus.connect(this)
        connection.subscribe(LibraryInfoListener.TOPIC, this)
        connection.subscribe(ModuleRootListener.TOPIC, this)
        connection.subscribe(WorkspaceModelTopics.CHANGED, this)
    }

    override fun changed(event: VersionedStorageChange) {
        val storageBefore = event.storageBefore
        val moduleChanges = event.getChanges(ModuleEntity::class.java)
        val sdkChanges = event.getChanges(SdkEntity::class.java)

        if (moduleChanges.isEmpty() && sdkChanges.isEmpty()) return

        val outdatedSdks = mutableSetOf<Sdk>()
        for (sdkChange in sdkChanges) {
            val sdk = sdkChange.oldEntity?.findSdkBridge(storageBefore)
            outdatedSdks.addIfNotNull(sdk)
        }

        invalidateEntries(
            { k, _ ->
                k !is LibraryInfo && k !is SdkInfo || k is SdkInfo && k.sdk in outdatedSdks
            },
            validityCondition = null
        )
    }

    override fun libraryInfosRemoved(libraryInfos: Collection<LibraryInfo>) {
        useCache { instance ->
            libraryInfos.forEach { instance.remove(it) }
        }
    }

    override fun rootsChanged(event: ModuleRootEvent) {
        if (event.isCausedByWorkspaceModelChangesOnly) return

        // SDK could be changed (esp in tests) out of message bus subscription
        val sdks = project.allSdks()
        useCache { instance ->
            val iterator = instance.entries.iterator()
            while (iterator.hasNext()) {
                val (key, value) = iterator.next()
                if (key.safeAs<SdkInfo>()?.sdk?.let { it !in sdks } == true || value.sdk?.sdk?.let { it !in sdks } == true) {
                    iterator.remove()
                }
            }
        }
    }

    override fun checkKeyValidity(key: ModuleInfo) {
        key.safeAs<IdeaModuleInfo>()?.checkValidity()
    }

    override fun findOrGetCachedSdk(moduleInfo: ModuleInfo): SdkInfo? = get(moduleInfo).sdk

    override fun calculate(cache: MutableMap<ModuleInfo, SdkDependency>, key: ModuleInfo): SdkDependency {
        key.safeAs<SdkInfo>()?.let {
            val sdkDependency = SdkDependency(it)
            cache[key] = sdkDependency
            return sdkDependency
        }

        val visitedModuleInfos = mutableSetOf<ModuleInfo>()

        val (path, sdkInfo) = run(fun(): Pair<List<ModuleInfo>?, SdkDependency> {
            val libraryDependenciesCache = LibraryDependenciesCache.getInstance(this.project)

            // graphs is a stack of paths is used to implement DFS without recursion
            // it depends on a number of libs, that could be > 10k for a huge monorepos
            val graphs = ArrayDeque<List<ModuleInfo>>().apply {
                // initial graph item
                add(listOf(key))
            }

            while (graphs.isNotEmpty()) {
                ProgressManager.checkCanceled()
                // graph of DFS from the root i.e from `moduleInfo`

                // note: traverse of graph goes in a depth over the most left edge:
                // - poll() retrieves and removes the head of the queue
                // - push(element) inserts the element at the front of the deque
                val graph = graphs.poll()

                val last = graph.last()
                // the result could be immediately returned when cache already has it
                val cached = cache[last]
                if (cached != null) {
                    cached.sdk?.let { return graph to cached }
                }

                if (!visitedModuleInfos.add(last)) continue

                val dependencies = if (last is LibraryInfo) {
                    // use a special case for LibraryInfo to reuse values from a library dependencies cache
                    val libraryDependencies = libraryDependenciesCache.getLibraryDependencies(last)
                    libraryDependencies.sdk.firstOrNull()?.let {
                        return graph to SdkDependency(it)
                    }

                    libraryDependencies.libraries
                } else {
                    last.dependencies().also { dependencies ->
                        dependencies.firstIsInstanceOrNull<SdkInfo>()?.let {
                            return graph to SdkDependency(it)
                        }
                    }
                }

                dependencies.forEach { dependency ->
                    val sdkDependency = cache[dependency]
                    if (sdkDependency != null) {
                        sdkDependency.sdk?.let {
                            // sdk is found when some dependency is already resolved
                            return (graph + dependency) to sdkDependency
                        }
                    } else {
                        // otherwise add a new graph of (existed graph + dependency) as candidates for DFS lookup
                        if (!visitedModuleInfos.contains(dependency)) {
                            graphs.push(graph + dependency)
                        }
                    }
                }
            }

            return null to noSdkDependency
        })

        // when sdk is found: mark all graph elements could be resolved to the same sdk
        if (path != null) {
            path.forEach { info -> cache[info] = sdkInfo }
            visitedModuleInfos.removeAll(path)
        }

        // mark all visited modules (apart from found path) as dead ends
        visitedModuleInfos.forEach { info -> cache.putIfAbsent(info, noSdkDependency) }

        return cache[key] ?: noSdkDependency
    }

    companion object {
        private val noSdkDependency = SdkDependency(null)
    }
}
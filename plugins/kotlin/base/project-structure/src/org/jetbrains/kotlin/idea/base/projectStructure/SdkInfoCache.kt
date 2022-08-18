// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.projectStructure

import com.intellij.ProjectTopics
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.workspaceModel.ide.WorkspaceModelChangeListener
import com.intellij.workspaceModel.ide.WorkspaceModelTopics
import com.intellij.workspaceModel.storage.VersionedStorageChange
import com.intellij.workspaceModel.storage.bridgeEntities.api.ModuleEntity
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.*
import org.jetbrains.kotlin.idea.base.util.caching.LockFreeFineGrainedEntityCache
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import java.util.ArrayDeque

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
    LockFreeFineGrainedEntityCache<ModuleInfo, SdkInfoCacheImpl.SdkDependency>(project, true),
    ProjectJdkTable.Listener,
    ModuleRootListener,
    OutdatedLibraryInfoListener,
    WorkspaceModelChangeListener {

    @JvmInline
    value class SdkDependency(val sdk: SdkInfo?)

    override fun subscribe() {
        val connection = project.messageBus.connect(this)
        connection.subscribe(OutdatedLibraryInfoListener.TOPIC, this)
        connection.subscribe(ProjectJdkTable.JDK_TABLE_TOPIC, this)
        connection.subscribe(ProjectTopics.PROJECT_ROOTS, this)
        WorkspaceModelTopics.getInstance(project).subscribeImmediately(connection, this)
    }

    override fun changed(event: VersionedStorageChange) {
        event.getChanges(ModuleEntity::class.java).ifEmpty { return }
        invalidateEntries(
            { k, _ ->
                k !is LibraryInfo && k !is SdkInfo
            },
            validityCondition = null
        )
    }

    override fun libraryInfosRemoved(libraryInfos: Collection<LibraryInfo>) {
        useCache { instance ->
            libraryInfos.forEach { instance.remove(it) }
        }
    }

    override fun jdkRemoved(jdk: Sdk) {
        useCache { instance ->
            val iterator = instance.entries.iterator()
            while (iterator.hasNext()) {
                val (key, value) = iterator.next()
                if (key.safeAs<SdkInfo>()?.sdk == jdk || value.sdk?.sdk == jdk) {
                    iterator.remove()
                }
            }
        }
    }

    override fun jdkNameChanged(jdk: Sdk, previousName: String) {
        jdkRemoved(jdk)
    }

    override fun rootsChanged(event: ModuleRootEvent) {
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

        val libraryDependenciesCache = LibraryDependenciesCache.getInstance(this.project)
        val visitedModuleInfos = mutableSetOf<ModuleInfo>()

        // graphs is a stack of paths is used to implement DFS without recursion
        // it depends on a number of libs, that could be > 10k for a huge monorepos
        val graphs = ArrayDeque<List<ModuleInfo>>().also {
            // initial graph item
            it.add(listOf(key))
        }

        val (path, sdkInfo) = run {
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
                    cached.sdk?.let { return@run graph to cached }
                    continue
                }

                if (!visitedModuleInfos.add(last)) continue

                val dependencies = run deps@{
                    if (last is LibraryInfo) {
                        // use a special case for LibraryInfo to reuse values from a library dependencies cache
                        val libraryDependencies = libraryDependenciesCache.getLibraryDependencies(last)
                        libraryDependencies.sdk.firstOrNull()?.let {
                            return@run graph to SdkDependency(it)
                        }
                        libraryDependencies.libraries
                    } else {
                        last.dependencies()
                            .also { dependencies ->
                                dependencies.firstIsInstanceOrNull<SdkInfo>()?.let {
                                    return@run graph to SdkDependency(it)
                                }
                            }
                    }
                }

                dependencies.forEach { dependency ->
                    val sdkDependency = cache[dependency]
                    if (sdkDependency != null) {
                        sdkDependency.sdk?.let {
                            // sdk is found when some dependency is already resolved
                            return@run (graph + dependency) to sdkDependency
                        }
                    } else {
                        // otherwise add a new graph of (existed graph + dependency) as candidates for DFS lookup
                        if (!visitedModuleInfos.contains(dependency)) {
                            graphs.push(graph + dependency)
                        }
                    }
                }
            }
            return@run null to noSdkDependency
        }
        // when sdk is found: mark all graph elements could be resolved to the same sdk
        path?.let {
            it.forEach { info -> cache[info] = sdkInfo }

            visitedModuleInfos.removeAll(it)
        }
        // mark all visited modules (apart from found path) as dead ends
        visitedModuleInfos.forEach { info -> cache[info] = noSdkDependency }

        return cache[key] ?: noSdkDependency
    }

    companion object {
        private val noSdkDependency = SdkDependency(null)
    }
}
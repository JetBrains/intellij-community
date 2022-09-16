// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.util.Disposer
import com.intellij.util.PathUtil
import com.intellij.util.messages.Topic
import com.intellij.workspaceModel.ide.WorkspaceModelTopics
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.base.util.caching.SynchronizedFineGrainedEntityCache
import org.jetbrains.kotlin.idea.base.platforms.LibraryEffectiveKindProvider
import org.jetbrains.kotlin.idea.base.platforms.isKlibLibraryRootForPlatform
import org.jetbrains.kotlin.idea.base.platforms.platform
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.*
import org.jetbrains.kotlin.idea.base.util.caching.LibraryEntityChangeListener
import org.jetbrains.kotlin.platform.IdePlatformKind
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.idePlatformKind
import org.jetbrains.kotlin.platform.impl.CommonIdePlatformKind
import org.jetbrains.kotlin.platform.impl.JsIdePlatformKind
import org.jetbrains.kotlin.platform.impl.JvmIdePlatformKind
import org.jetbrains.kotlin.platform.impl.NativeIdePlatformKind
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.utils.addToStdlib.flattenTo
import kotlin.collections.ArrayList

class LibraryInfoCache(project: Project): Disposable {

    private val libraryInfoCache = LibraryInfoInnerCache(project)

    init {
        Disposer.register(this, libraryInfoCache)
    }

    internal class LibraryInfoInnerCache(project: Project) : SynchronizedFineGrainedEntityCache<LibraryEx, List<LibraryInfo>>(project, cleanOnLowMemory = true) {

        private val deduplicationCache = hashMapOf<String, MutableList<LibraryEx>>()

        override fun subscribe() {
            val busConnection = project.messageBus.connect(this)
            WorkspaceModelTopics.getInstance(project).subscribeImmediately(busConnection, ModelChangeListener(this, project))
        }

        override fun dispose() {
            super.dispose()
            useCache { deduplicationCache.clear() }
        }

        override fun get(key: LibraryEx): List<LibraryInfo> {
            checkKeyAndDisposeIllegalEntry(key)

            useCache { cache ->
                checkEntitiesIfRequired(cache)

                cache[key]
            }?.let { return it }

            val urlsByType = buildMap {
                val rootProvider = key.rootProvider
                for (orderRootType in OrderRootType.getAllTypes()) {
                    ProgressManager.checkCanceled()
                    put(orderRootType, rootProvider.getUrls(orderRootType))
                }
            }

            val firstRoot = key.firstRoot()

            val (deduplicatedLibrary: LibraryEx?, libraryInfos: List<LibraryInfo>?) =
                useCache { cache ->
                    val deduplicatedLibrary = deduplicationCache[firstRoot]?.firstOrNull {
                        it === key || urlsByType.all { (k, v) ->
                            v.contentEquals(it.getUrls(k))
                        }
                    } ?: return@useCache null
                    checkEntitiesIfRequired(cache)

                    val libraryInfos = cache[deduplicatedLibrary]
                    if (libraryInfos != null && deduplicatedLibrary !== key) {
                        cache[key] = libraryInfos
                    }
                    deduplicatedLibrary to libraryInfos
                } ?: Pair(null, null)

            libraryInfos?.let { return it }

            ProgressManager.checkCanceled()

            val newValue = calculate(key)

            if (isValidityChecksEnabled) {
                checkValueValidity(newValue)
            }

            useCache { cache ->
                val putIfAbsent = cache.putIfAbsent(key, newValue)
                if (key.name == null) {
                    Unit
                }
                val libraryExMutableList = deduplicationCache.computeIfAbsent(firstRoot) { mutableListOf() }
                if (putIfAbsent == null) {
                    libraryExMutableList.add(key)
                }
                if (deduplicatedLibrary != null) {
                    val putIfAbsent2 = cache.putIfAbsent(deduplicatedLibrary, newValue)
                    if (putIfAbsent2 == null) {
                        libraryExMutableList.add(deduplicatedLibrary)
                    }
                }
                putIfAbsent
            }?.let { return it }

            postProcessNewValue(key, newValue)

            return newValue
        }

        private fun LibraryEx.firstRoot() = getUrls(OrderRootType.CLASSES).firstOrNull() ?: ""

        override fun checkKeyValidity(key: LibraryEx) {
            key.checkValidity()
        }

        override fun calculate(key: LibraryEx): List<LibraryInfo> {
            val libraryWrapper = LibraryWrapper.wrapLibrary(key)
            val libraryInfos = when (val platformKind = getPlatform(key).idePlatformKind) {
                is JvmIdePlatformKind -> listOf(JvmLibraryInfo(project, libraryWrapper))
                is CommonIdePlatformKind -> createLibraryInfos(libraryWrapper, platformKind, ::CommonKlibLibraryInfo, ::CommonMetadataLibraryInfo)
                is JsIdePlatformKind -> createLibraryInfos(libraryWrapper, platformKind, ::JsKlibLibraryInfo, ::JsMetadataLibraryInfo)
                is NativeIdePlatformKind -> createLibraryInfos(libraryWrapper, platformKind, ::NativeKlibLibraryInfo, null)
                else -> error("Unexpected platform kind: $platformKind")
            }
            return libraryInfos
        }

        override fun postProcessNewValue(key: LibraryEx, value: List<LibraryInfo>) {
            project.messageBus.syncPublisher(LibraryInfoListener.TOPIC).libraryInfosAdded(value)
        }

        fun invalidateKeysAndGetOutdatedValues(
            keys: Collection<LibraryEx>,
        ): Collection<List<LibraryInfo>> {
            val outdatedValues = super.invalidateKeysAndGetOutdatedValues(keys, CHECK_ALL)
            useCache {_ ->
                for (key in keys) {
                    val firstRoot = key.firstRoot()
                    deduplicationCache[firstRoot]?.remove(key)
                }
            }
            return outdatedValues
        }

        private fun createLibraryInfos(
            libraryWrapper: LibraryWrapper,
            platformKind: IdePlatformKind,
            klibLibraryInfoFactory: (Project, LibraryWrapper, String) -> LibraryInfo,
            metadataLibraryInfoFactory: ((Project, LibraryWrapper) -> LibraryInfo)?
        ): List<LibraryInfo> {
            val defaultPlatform = platformKind.defaultPlatform
            val klibFiles = libraryWrapper.getFiles(OrderRootType.CLASSES).filter { it.isKlibLibraryRootForPlatform(defaultPlatform) }

            return if (klibFiles.isNotEmpty()) {
                ArrayList<LibraryInfo>(klibFiles.size).apply {
                    for (file in klibFiles) {
                        val path = PathUtil.getLocalPath(file) ?: continue
                        add(klibLibraryInfoFactory(project, libraryWrapper, path))
                    }
                }
            } else if (metadataLibraryInfoFactory != null) {
                listOf(metadataLibraryInfoFactory(project, libraryWrapper))
            } else {
                listOf(EmptyKlibLibraryInfo(project, libraryWrapper))
            }
        }

        private fun getPlatform(library: LibraryEx): TargetPlatform =
            if (!library.isDisposed) {
                project.service<LibraryEffectiveKindProvider>().getEffectiveKind(library).platform
            } else {
                JvmPlatforms.defaultJvmPlatform
            }
    }

    internal class ModelChangeListener(private val libraryInfoCache: LibraryInfoInnerCache, project: Project) : LibraryEntityChangeListener(project, afterChangeApplied = false) {

        override fun entitiesChanged(outdated: List<Library>) {
            val droppedLibraryInfos = libraryInfoCache.invalidateKeysAndGetOutdatedValues(outdated.map { it as LibraryEx }).flattenTo(hashSetOf())

            if (droppedLibraryInfos.isNotEmpty()) {
                project.messageBus.syncPublisher(LibraryInfoListener.TOPIC).libraryInfosRemoved(droppedLibraryInfos)
            }
        }
    }

    operator fun get(key: Library): List<LibraryInfo> {
        return libraryInfoCache[key as LibraryEx]
    }

    override fun dispose() = Unit

    companion object {
        fun getInstance(project: Project): LibraryInfoCache = project.service()
    }
}

@ApiStatus.Internal
interface LibraryInfoListener {

    @ApiStatus.Internal
    fun libraryInfosRemoved(libraryInfos: Collection<LibraryInfo>)

    @ApiStatus.Internal
    fun libraryInfosAdded(libraryInfos: Collection<LibraryInfo>) {}

    companion object {
        @ApiStatus.Internal
        @JvmStatic
        @Topic.ProjectLevel
        val TOPIC = Topic.create("library info listener", LibraryInfoListener::class.java)
    }
}

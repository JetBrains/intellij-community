// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.assertReadAccessAllowed
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.platform.workspace.storage.VersionedStorageChange
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.ModuleDependencyItem
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.serviceContainer.AlreadyDisposedException
import com.intellij.util.PathUtil
import com.intellij.util.messages.Topic
import com.intellij.platform.backend.workspace.WorkspaceModelChangeListener
import com.intellij.platform.backend.workspace.WorkspaceModelTopics
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.findLibraryBridge
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.base.platforms.detectLibraryKind
import org.jetbrains.kotlin.idea.base.platforms.isKlibLibraryRootForPlatform
import org.jetbrains.kotlin.idea.base.platforms.platform
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.*
import org.jetbrains.kotlin.idea.base.util.caching.SynchronizedFineGrainedEntityCache
import org.jetbrains.kotlin.platform.IdePlatformKind
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.idePlatformKind
import org.jetbrains.kotlin.platform.impl.CommonIdePlatformKind
import org.jetbrains.kotlin.platform.impl.JsIdePlatformKind
import org.jetbrains.kotlin.platform.impl.JvmIdePlatformKind
import org.jetbrains.kotlin.platform.impl.NativeIdePlatformKind
import org.jetbrains.kotlin.platform.impl.WasmIdePlatformKind
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.utils.KotlinExceptionWithAttachments
import org.jetbrains.kotlin.utils.addIfNotNull
import org.jetbrains.kotlin.utils.addToStdlib.flattenTo

class LibraryInfoCache(project: Project) : Disposable {

    private val libraryInfoCache = LibraryInfoInnerCache(project)

    init {
        Disposer.register(this, libraryInfoCache)
    }

    private class LibraryInfoInnerCache(project: Project) :
        SynchronizedFineGrainedEntityCache<LibraryEx, List<LibraryInfo>>(project) {

        val removedLibraryInfoTracker = SimpleModificationTracker()

        private val deduplicationCache = hashMapOf<String, MutableList<LibraryEx>>()

        init {
          initialize()
        }

        override fun subscribe() {
            project.messageBus.connect(this).subscribe(WorkspaceModelTopics.CHANGED, ModelChangeListener(project))
        }

        override fun doInvalidate(cache: MutableMap<LibraryEx, List<LibraryInfo>>) {
            super.doInvalidate(cache)
            deduplicationCache.clear()
        }

        override fun get(key: LibraryEx): List<LibraryInfo> {
            assertReadAccessAllowed()
            checkKeyAndDisposeIllegalEntry(key)

            /**
             * Project model could provide different instances of libraries
             * those have the same content (roots + excluded roots) but different names
             * e.g. module level library has `null` name while project level library has smth like `kotlin-stdlib-1.5.10`.
             *
             * When it is needed to check equality (e.g. to eliminate duplicates) of LibraryInfo we have to
             * check roots of underlying library.
             *
             * To have faster equality checks we need to deduplicate libraries: [deduplicationCache] is used to address it.
             * It uses first root of a library as a key.
             * Values are only unique (in terms of content) libraries those are used in some LibraryInfo.
             *
             * if we have two different instances of the same (in terms of content) library
             * we ALWAYS have the same instance of libraryInfo.
             *
             * It even allows to perform equality check based on object identity.
             */

            getCachedOrPutNewValue(key, newValue = null)?.let { return it }

            ProgressManager.checkCanceled()

            val newValue = calculate(key)

            if (isValidityChecksEnabled) {
                checkValueValidity(newValue)
            }

            getCachedOrPutNewValue(key, newValue)?.let { return it }

            postProcessNewValue(key, newValue)

            return newValue
        }

        /**
         * @return cached value or null
         */
        private fun getCachedOrPutNewValue(key: LibraryEx, newValue: List<LibraryInfo>?): List<LibraryInfo>? = useCache { cache ->
            checkEntitiesIfRequired(cache)

            cache[key]?.let { return@useCache it }

            val root = key.firstRoot()
            val deduplicatedValue = cachedDeduplicatedValue(cache, key, root)
            val resultValue = deduplicatedValue ?: newValue ?: return@useCache null
            addEntryToCache(cache, key, root, resultValue)

            deduplicatedValue
        }

        private fun addEntryToCache(
            cache: MutableMap<LibraryEx, List<LibraryInfo>>,
            key: LibraryEx,
            root: String,
            value: List<LibraryInfo>,
        ) {
            cache[key] = value
            deduplicationCache.getOrPut(root) { mutableListOf() } += key
        }

        private fun cachedDeduplicatedValue(
            cache: MutableMap<LibraryEx, List<LibraryInfo>>,
            key: LibraryEx,
            root: String,
        ): List<LibraryInfo>? {
            val deduplicatedLibraries = deduplicationCache[root]
            if (deduplicatedLibraries.isNullOrEmpty()) return null

            val keyUrlsByType = key.urlsByType()
            val deduplicatedLibrary = deduplicatedLibraries.find { keyUrlsByType.rootEquals(it) } ?: return null
            val cachedValue = cache[deduplicatedLibrary]
            if (cachedValue == null) {
                val exception = KotlinExceptionWithAttachments(
                    """
                        inconsistent state:
                        is the same key: ${deduplicatedLibrary === key}
                        root consistent: ${key.firstRoot() == root}
                        urls consistent: ${key.urlsByType() == keyUrlsByType}
                        key name: ${key.presentableName}
                        deduplicated key name: ${deduplicatedLibrary.presentableName}
                    """.trimIndent()
                )
                    .withAttachment("key.txt", key.toString())
                    .withAttachment("deduplicated.txt", deduplicatedLibrary.toString())
                    .withAttachment("librariesBefore.txt", deduplicatedLibraries.joinToString(separator = "\n"))

                deduplicatedLibraries -= deduplicatedLibrary
                exception.withAttachment("librariesAfter.txt", deduplicatedLibraries.joinToString(separator = "\n"))
                logger.error(exception)

                return cachedDeduplicatedValue(cache, key, root)
            }

            return cachedValue
        }

        private fun LibraryEx.firstRoot() = getUrls(OrderRootType.CLASSES).firstOrNull() ?: ""

        override fun checkKeyValidity(key: LibraryEx) {
            key.checkValidity()
        }

        override fun checkKeyConsistency(cache: MutableMap<LibraryEx, List<LibraryInfo>>, key: LibraryEx) {
            super.checkKeyConsistency(cache, key)
            checkCacheConsistency(cache, key)
        }

        private fun checkCacheConsistency(cache: MutableMap<LibraryEx, List<LibraryInfo>>, key: LibraryEx) {
            val isCached = key in cache
            val isDeduplicated = deduplicationCache[key.firstRoot()]?.contains(key) == true
            if (isCached != isDeduplicated) {
                error("inconsistent state ${key.presentableName}: is cached: $isCached, is deduplicated: $isDeduplicated")
            }
        }

        override fun additionalEntitiesCheck(cache: MutableMap<LibraryEx, List<LibraryInfo>>) {
            for (values in deduplicationCache.values) {
                val iterator = values.iterator()
                while (iterator.hasNext()) {
                    val library = iterator.next()
                    try {
                        checkCacheConsistency(cache, library)
                    } catch (e: Throwable) {
                        iterator.remove()
                        cache.remove(library)
                        logger.error(e)
                    }
                }
            }
        }

        override fun disposeIllegalEntry(cache: MutableMap<LibraryEx, List<LibraryInfo>>, key: LibraryEx) {
            super.disposeIllegalEntry(cache, key)
            dropDisposedKey(key)
        }

        override fun disposeEntry(
            cache: MutableMap<LibraryEx, List<LibraryInfo>>,
            entry: MutableMap.MutableEntry<LibraryEx, List<LibraryInfo>>,
        ) {
            dropDisposedKey(entry.key)

            val libInfoKey = entry.value.first().library
            if (libInfoKey == entry.key) return

            val iterator = cache.iterator()
            while (iterator.hasNext()) {
                val cacheEntry = iterator.next()
                if (cacheEntry.value.first().library == libInfoKey) {
                    iterator.remove()
                    dropDisposedKey(cacheEntry.key)
                }
            }
        }

        private fun dropDisposedKey(key: LibraryEx) {
            for (values in deduplicationCache.values) {
                if (values.remove(key)) break
            }
        }

        override fun checkValueValidity(value: List<LibraryInfo>) {
            value.forEach(LibraryInfo::checkValidity)
        }

        override fun calculate(key: LibraryEx): List<LibraryInfo> = when (val platformKind = getPlatform(key).idePlatformKind) {
            is JvmIdePlatformKind -> listOf(JvmLibraryInfo(project, key))
            is CommonIdePlatformKind -> createLibraryInfos(key, platformKind, ::CommonKlibLibraryInfo, ::CommonMetadataLibraryInfo)
            is JsIdePlatformKind -> createLibraryInfos(key, platformKind, ::JsKlibLibraryInfo, ::JsMetadataLibraryInfo)
            is WasmIdePlatformKind -> createLibraryInfos(key, platformKind, ::WasmKlibLibraryInfo, ::WasmMetadataLibraryInfo)
            is NativeIdePlatformKind -> createLibraryInfos(key, platformKind, ::NativeKlibLibraryInfo, ::NativeMetadataLibraryInfo)
            else -> error("Unexpected platform kind: $platformKind")
        }.also {
            require(it.isNotEmpty()) { "Must be not empty for consistency with LibraryInfoCache#deduplicatedLibrary" }
        }

        override fun postProcessNewValue(key: LibraryEx, value: List<LibraryInfo>) {
            project.messageBus.syncPublisher(LibraryInfoListener.TOPIC).libraryInfosAdded(value)
        }

        override fun doInvalidateKeysAndGetOutdatedValues(
            keys: Collection<LibraryEx>,
            cache: MutableMap<LibraryEx, List<LibraryInfo>>,
        ): Collection<List<LibraryInfo>> {
            val outdatedValues = mutableListOf<List<LibraryInfo>>()
            for ((root, invalidatedLibraries) in keys.groupBy { it.firstRoot() }) {
                val deduplicatedLibraries = deduplicationCache[root] ?: continue
                if (deduplicatedLibraries.isEmpty()) continue
                deduplicatedLibraries.removeAll(invalidatedLibraries)

                for (invalidatedLibrary in invalidatedLibraries) {
                    val anchorInfo = cache.remove(invalidatedLibrary)?.takeIf { it.first().library == invalidatedLibrary } ?: continue
                    outdatedValues += anchorInfo

                    if (deduplicatedLibraries.isEmpty()) continue
                    val invalidatedLibraryUrlsByType = invalidatedLibrary.urlsByType()
                    val deduplicatedLibrariesIterator = deduplicatedLibraries.iterator()
                    while (deduplicatedLibrariesIterator.hasNext()) {
                        val deduplicatedLibrary = deduplicatedLibrariesIterator.next()
                        if (invalidatedLibraryUrlsByType.rootEquals(deduplicatedLibrary)) {
                            deduplicatedLibrariesIterator.remove()
                            cache.remove(deduplicatedLibrary)
                        }
                    }
                }
            }

            return outdatedValues
        }

        private fun createLibraryInfos(
            library: LibraryEx,
            platformKind: IdePlatformKind,
            klibLibraryInfoFactory: (Project, LibraryEx, String) -> LibraryInfo,
            metadataLibraryInfoFactory: ((Project, LibraryEx) -> LibraryInfo)
        ): List<LibraryInfo> {
            val defaultPlatform = platformKind.defaultPlatform
            val klibFiles = library.getFiles(OrderRootType.CLASSES).filter {
                it.isKlibLibraryRootForPlatform(defaultPlatform)
            }

            if (klibFiles.isEmpty()) {
                return listOf(metadataLibraryInfoFactory(project, library))
            }

            return ArrayList<LibraryInfo>(klibFiles.size).apply {
                for (file in klibFiles) {
                    val path = PathUtil.getLocalPath(file) ?: continue
                    add(klibLibraryInfoFactory(project, library, path))
                }
            }
        }

        private fun getPlatform(library: LibraryEx): TargetPlatform =
            if (!library.isDisposed) {
                detectLibraryKind(library, project).platform
            } else {
                JvmPlatforms.defaultJvmPlatform
            }

        inner class ModelChangeListener(project: Project) : WorkspaceModelChangeListener {
            override fun beforeChanged(event: VersionedStorageChange) {
                val storageBefore = event.storageBefore
                val libraryChanges = event.getChanges(LibraryEntity::class.java)
                val moduleChanges = event.getChanges(ModuleEntity::class.java)

                if (libraryChanges.none() && moduleChanges.none()) return

                val outdatedLibraries: MutableList<Library> = libraryChanges
                  .mapNotNull { it.oldEntity?.findLibraryBridge(storageBefore) }
                  .toMutableList()

                val oldLibDependencies = moduleChanges.mapNotNull {
                    it.oldEntity?.dependencies?.filterIsInstance<ModuleDependencyItem.Exportable.LibraryDependency>()
                }.flatten().associateBy { it.library }

                val newLibDependencies = moduleChanges.mapNotNull {
                    it.newEntity?.dependencies?.filterIsInstance<ModuleDependencyItem.Exportable.LibraryDependency>()
                }.flatten().associateBy { it.library }

                for (entry in oldLibDependencies.entries) {
                    val value = entry.value
                    if (value != newLibDependencies[entry.key]) {
                        val libraryBridge = value.library.findLibraryBridge(storageBefore, project)
                        outdatedLibraries.addIfNotNull(libraryBridge)
                    }
                }

                if (outdatedLibraries.isNotEmpty()) {
                    val droppedLibraryInfos =
                      invalidateKeysAndGetOutdatedValues(outdatedLibraries.map { it as LibraryEx }).flattenTo(hashSetOf())

                    if (droppedLibraryInfos.isNotEmpty()) {
                        removedLibraryInfoTracker.incModificationCount()
                        project.messageBus.syncPublisher(LibraryInfoListener.TOPIC).libraryInfosRemoved(droppedLibraryInfos)
                    }
                }
            }
        }
    }

    operator fun get(key: Library): List<LibraryInfo> {
        require(key is LibraryEx) { "Library '${key.presentableName}' does not implement LibraryEx which is not expected" }
        return libraryInfoCache[key]
    }

    fun deduplicatedLibrary(key: Library): Library = get(key).first().library

    override fun dispose() = Unit

    fun removedLibraryInfoTracker(): ModificationTracker = libraryInfoCache.removedLibraryInfoTracker

    fun values(): Collection<List<LibraryInfo>> = libraryInfoCache.values()

    companion object {
        fun getInstance(project: Project): LibraryInfoCache = project.service()
    }
}

@ApiStatus.Internal
interface LibraryInfoListener {

    @ApiStatus.Internal
    fun libraryInfosRemoved(libraryInfos: Collection<LibraryInfo>)

    @ApiStatus.Internal
    fun libraryInfosAdded(libraryInfos: Collection<LibraryInfo>) = Unit

    companion object {
        @JvmStatic
        @Topic.ProjectLevel
        @ApiStatus.Internal
        val TOPIC = Topic(LibraryInfoListener::class.java, Topic.BroadcastDirection.NONE, true)
    }
}

fun Library.checkValidity() {
    if (this is LibraryEx && isDisposed) {
        throw AlreadyDisposedException("Library '${name}' is already disposed")
    }
}

private fun LibraryEx.urlsByType(): Map<OrderRootType, Array<String>> = buildMap {
    for (orderRootType in OrderRootType.getAllTypes()) {
        put(orderRootType, getUrls(orderRootType))
    }
}

private fun Map<OrderRootType, Array<String>>.rootEquals(another: LibraryEx): Boolean = all { (k, v) ->
    v.contentEquals(another.getUrls(k))
}

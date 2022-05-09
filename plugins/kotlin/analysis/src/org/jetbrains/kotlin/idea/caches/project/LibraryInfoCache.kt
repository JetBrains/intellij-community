// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.caches.project

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.util.LowMemoryWatcher
import com.intellij.openapi.util.registry.Registry
import com.intellij.serviceContainer.AlreadyDisposedException
import com.intellij.workspaceModel.ide.WorkspaceModelChangeListener
import com.intellij.workspaceModel.ide.WorkspaceModelTopics
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.findLibraryBridge
import com.intellij.workspaceModel.storage.EntityChange
import com.intellij.workspaceModel.storage.VersionedStorageChange
import com.intellij.workspaceModel.storage.bridgeEntities.api.LibraryEntity
import org.jetbrains.kotlin.caches.project.cacheInvalidatingOnRootModifications
import org.jetbrains.kotlin.caches.resolve.resolution
import org.jetbrains.kotlin.idea.base.platforms.LibraryEffectiveKindProvider
import org.jetbrains.kotlin.idea.base.platforms.platform
import org.jetbrains.kotlin.platform.DefaultIdeTargetPlatformKindProvider
import org.jetbrains.kotlin.platform.idePlatformKind
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class LibraryInfoCache(private val project: Project): Disposable {

    private val libraryInfoCache: MutableMap<Library, List<LibraryInfo>>
        get() = if (fineGrainedCacheInvalidation) hashMapOf() else project.libraryInfoCache

    private val lock = Any()

    init {
        // drop entire cache when it is low free memory
        LowMemoryWatcher.register(this::clear, this)
        if (fineGrainedCacheInvalidation) {
            val busConnection = project.messageBus.connect(this)
            WorkspaceModelTopics.getInstance(project).subscribeImmediately(busConnection, ModelChangeListener(project))
        }
    }

    fun createLibraryInfo(library: Library): List<LibraryInfo> {
        library.safeAs<LibraryEx>()?.takeIf { it.isDisposed }?.let {
            synchronized(lock) {
                libraryInfoCache.remove(it)
            }
            throw AlreadyDisposedException("${it.name} is already disposed")
        }

        // fast check
        synchronized(lock) {
            libraryInfoCache[library]?.let { return it }
        }

        ProgressManager.checkCanceled()

        val approximatePlatform = if (library is LibraryEx && !library.isDisposed) {
            // for Native returns 'unspecifiedNativePlatform', thus "approximate"
            LibraryEffectiveKindProvider.getInstance(project).getEffectiveKind(library).platform
        } else {
            DefaultIdeTargetPlatformKindProvider.defaultPlatform
        }

        val libraryInfos = approximatePlatform.idePlatformKind.resolution.createLibraryInfo(project, library)

        ProgressManager.checkCanceled()
        synchronized(lock) {
            libraryInfoCache.putIfAbsent(library, libraryInfos)?.let { return it }
        }

        return libraryInfos
    }

    override fun dispose() {
        clear()
    }

    private fun clear() {
        synchronized(lock) {
            libraryInfoCache.clear()
        }
    }

    private fun cleanupCache(libraries: Collection<Library>) {
        synchronized(lock) {
            libraries.forEach(libraryInfoCache::remove)
        }
    }

    internal class ModelChangeListener(private val project: Project) : WorkspaceModelChangeListener {
        override fun changed(event: VersionedStorageChange) {
            val storageBefore = event.storageBefore
            val changes = event.getChanges(LibraryEntity::class.java).ifEmpty { return }

            val infoCache = getInstance(project)
            val outdatedLibraries: List<Library> = changes.asSequence()
                .mapNotNull {
                    when (it) {
                        is EntityChange.Added -> null
                        is EntityChange.Removed -> it.entity
                        is EntityChange.Replaced -> it.oldEntity
                    }
                }
                .mapNotNull { it.findLibraryBridge(storageBefore) }
                .toList()
            infoCache.cleanupCache(outdatedLibraries)
        }
    }

    companion object {
        val fineGrainedCacheInvalidation = Registry.`is`("kotlin.caches.fine.grained.invalidation")

        fun getInstance(project: Project): LibraryInfoCache = project.service()
    }
}

private val Project.libraryInfoCache: MutableMap<Library, List<LibraryInfo>>
    get() = cacheInvalidatingOnRootModifications { hashMapOf() }
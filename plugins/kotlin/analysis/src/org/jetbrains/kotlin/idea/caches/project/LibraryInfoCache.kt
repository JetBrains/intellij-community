// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.caches.project

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.workspaceModel.ide.WorkspaceModelChangeListener
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.findLibraryBridge
import com.intellij.workspaceModel.storage.EntityChange
import com.intellij.workspaceModel.storage.VersionedStorageChange
import com.intellij.workspaceModel.storage.bridgeEntities.api.LibraryEntity
import org.jetbrains.kotlin.caches.resolve.resolution
import org.jetbrains.kotlin.idea.framework.effectiveKind
import org.jetbrains.kotlin.idea.framework.platform
import org.jetbrains.kotlin.platform.DefaultIdeTargetPlatformKindProvider
import org.jetbrains.kotlin.platform.idePlatformKind
import java.util.concurrent.ConcurrentHashMap

class LibraryInfoCache(private val project: Project): Disposable {
    private val libraryInfoCache: MutableMap<Library, List<LibraryInfo>> = ConcurrentHashMap<Library, List<LibraryInfo>>()

    fun createLibraryInfo(library: Library): List<LibraryInfo> =
        libraryInfoCache.getOrPut(library) {
            val approximatePlatform = if (library is LibraryEx && !library.isDisposed) {
                // for Native returns 'unspecifiedNativePlatform', thus "approximate"
                library.effectiveKind(project).platform
            } else {
                DefaultIdeTargetPlatformKindProvider.defaultPlatform
            }

            approximatePlatform.idePlatformKind.resolution.createLibraryInfo(project, library)
        }

    override fun dispose() {
        libraryInfoCache.clear()
    }

    internal class ModelChangeListener(private val project: Project) : WorkspaceModelChangeListener {
        override fun changed(event: VersionedStorageChange) {
            val storageBefore = event.storageBefore
            val changes = event.getChanges(LibraryEntity::class.java).ifEmpty { return }

            val cache = getInstance(project).libraryInfoCache.ifEmpty { return }
            for (change in changes) {
                val oldEntity = when (change) {
                    is EntityChange.Added -> null
                    is EntityChange.Removed -> change.entity
                    is EntityChange.Replaced -> change.oldEntity
                } ?: continue
                // cache refers to an old library entity
                oldEntity.findLibraryBridge(storageBefore)?.let {
                    // drop outdated library from a cache
                    cache.remove(it)
                }
            }
        }
    }

    companion object {
        fun getInstance(project: Project): LibraryInfoCache = project.service()
    }
}
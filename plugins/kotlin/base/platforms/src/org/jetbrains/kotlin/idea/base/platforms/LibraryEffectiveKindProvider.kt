// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.platforms

import com.intellij.ProjectTopics
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.PersistentLibraryKind
import com.intellij.workspaceModel.ide.WorkspaceModelChangeListener
import com.intellij.workspaceModel.ide.WorkspaceModelTopics
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.VersionedStorageChange
import com.intellij.workspaceModel.storage.bridgeEntities.api.LibraryEntity
import org.jetbrains.kotlin.idea.base.util.caching.findLibraryByEntityWithHack
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class LibraryEffectiveKindProvider(project: Project): Disposable {
    private val effectiveKindMap = ConcurrentHashMap<LibraryEx, PersistentLibraryKind<*>?>()

    init {
        val connection = project.messageBus.connect(this)
        WorkspaceModelTopics.getInstance(project).subscribeAfterModuleLoading(connection, object : WorkspaceModelChangeListener {
            override fun beforeChanged(event: VersionedStorageChange) {
                event.getChanges(LibraryEntity::class.java).forEach { dropKindMapEntry(it.oldEntity, event.storageBefore) }
            }

            override fun changed(event: VersionedStorageChange) {
                event.getChanges(LibraryEntity::class.java).forEach { dropKindMapEntry(it.newEntity, event.storageAfter) }
            }

            private fun dropKindMapEntry(libraryEntity: LibraryEntity?, storage: EntityStorage) {
                val entity = libraryEntity ?: return
                val lib = storage.findLibraryByEntityWithHack(entity, project) ?: return
                effectiveKindMap.remove(lib)
            }
        })

        connection.subscribe(ProjectTopics.PROJECT_ROOTS, object : ModuleRootListener {
            override fun rootsChanged(event: ModuleRootEvent) {
                if (!event.isCausedByWorkspaceModelChangesOnly) {
                    effectiveKindMap.clear()
                }
            }
        })
    }

    fun getEffectiveKind(library: LibraryEx): PersistentLibraryKind<*>? {
        if (library.isDisposed) {
            return null
        }

        return when (val kind = library.kind) {
            is KotlinLibraryKind -> kind
            else -> effectiveKindMap.computeIfAbsent(library) {
                detectLibraryKind(it.getFiles(OrderRootType.CLASSES))
            }
        }
    }

    companion object {
        @JvmStatic
        fun getInstance(project: Project): LibraryEffectiveKindProvider = project.service()
    }

    override fun dispose() = Unit
}
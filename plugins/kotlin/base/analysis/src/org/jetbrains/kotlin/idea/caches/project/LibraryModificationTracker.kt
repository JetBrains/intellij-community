// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.caches.project

import com.intellij.ide.highlighter.ArchiveFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.fileTypes.FileTypeEvent
import com.intellij.openapi.fileTypes.FileTypeListener
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.workspaceModel.ide.WorkspaceModelChangeListener
import com.intellij.workspaceModel.ide.WorkspaceModelTopics
import com.intellij.workspaceModel.storage.VersionedStorageChange
import com.intellij.workspaceModel.storage.bridgeEntities.api.LibraryEntity
import org.jetbrains.kotlin.idea.core.KotlinPluginDisposable

class LibraryModificationTracker(project: Project) : SimpleModificationTracker() {
    companion object {
        @JvmStatic
        fun getInstance(project: Project): LibraryModificationTracker = project.service()
    }

    init {
        val disposable = KotlinPluginDisposable.getInstance(project)
        val connection = project.messageBus.connect(disposable)
        connection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                events.filter(::isRelevantEvent).let { createEvents ->
                    if (createEvents.isNotEmpty()) {
                        ApplicationManager.getApplication().invokeLater({
                           processBulk(createEvents) {
                               projectFileIndex.isInLibraryClasses(it) || isLibraryArchiveRoot(
                                   it
                               )
                           }
                       }, project.disposed)
                    }
                }
            }

            override fun before(events: List<VFileEvent>) {
                processBulk(events) {
                    projectFileIndex.isInLibraryClasses(it)
                }
            }
        })

        connection.subscribe(DumbService.DUMB_MODE, object : DumbService.DumbModeListener {
            override fun enteredDumbMode() {
                incModificationCount()
            }

            override fun exitDumbMode() {
                incModificationCount()
            }
        })

        connection.subscribe(FileTypeManager.TOPIC, object : FileTypeListener {
            override fun beforeFileTypesChanged(event: FileTypeEvent) {
                incModificationCount()
            }

            override fun fileTypesChanged(event: FileTypeEvent) {
                incModificationCount()
            }
        })

        connection.subscribe(WorkspaceModelTopics.CHANGED, object : WorkspaceModelChangeListener {
            override fun changed(event: VersionedStorageChange) {
                event.getChanges(LibraryEntity::class.java).ifEmpty { return }
                incModificationCount()
            }
        })
    }

    private val projectFileIndex = ProjectFileIndex.getInstance(project)

    private inline fun processBulk(events: List<VFileEvent>, check: (VirtualFile) -> Boolean) {
        events.forEach { event ->
            if (event.isValid) {
                val file = event.file
                if (file != null && check(file)) {
                    incModificationCount()
                    return
                }
            }
        }
    }

    // if library points to a jar, the jar does not pass isInLibraryClasses check, so we have to perform additional check for this case
    private fun isLibraryArchiveRoot(virtualFile: VirtualFile): Boolean {
        if (!FileTypeRegistry.getInstance().isFileOfType(virtualFile, ArchiveFileType.INSTANCE)) return false

        val archiveRoot = JarFileSystem.getInstance().getRootByLocal(virtualFile) ?: return false
        return projectFileIndex.isInLibraryClasses(archiveRoot)
    }

}

private fun isRelevantEvent(vFileEvent: VFileEvent): Boolean {
    return vFileEvent is VFileCreateEvent
            || vFileEvent is VFileMoveEvent
            || vFileEvent is VFileCopyEvent
}

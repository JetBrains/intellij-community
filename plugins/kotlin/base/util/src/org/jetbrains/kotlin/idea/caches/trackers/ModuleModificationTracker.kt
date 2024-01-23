// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.caches.trackers

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.platform.backend.workspace.WorkspaceModelChangeListener
import com.intellij.platform.backend.workspace.WorkspaceModelTopics
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.VersionedStorageChange
import org.jetbrains.kotlin.idea.base.util.caching.getChanges

@Service(Service.Level.PROJECT)
class ModuleModificationTracker(project: Project) :
    SimpleModificationTracker(), WorkspaceModelChangeListener, Disposable {

    init {
        project.messageBus.connect(this).subscribe(WorkspaceModelTopics.CHANGED, this)
    }

    override fun changed(event: VersionedStorageChange) {
        event.getChanges<ModuleEntity>().ifEmpty { return }
        incModificationCount()
    }

    override fun dispose() = Unit

    companion object {
        @JvmStatic
        fun getInstance(project: Project): ModuleModificationTracker = project.service()
    }
}
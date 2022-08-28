// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.caches.trackers

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.workspaceModel.ide.WorkspaceModelChangeListener
import com.intellij.workspaceModel.ide.WorkspaceModelTopics
import com.intellij.workspaceModel.storage.VersionedStorageChange
import com.intellij.workspaceModel.storage.bridgeEntities.api.ModuleEntity

class ModuleModificationTracker(project: Project) :
    SimpleModificationTracker(), WorkspaceModelChangeListener, Disposable {

    init {
        val connection = project.messageBus.connect(this)
        WorkspaceModelTopics.getInstance(project).subscribeImmediately(connection, this)
    }

    override fun changed(event: VersionedStorageChange) {
        event.getChanges(ModuleEntity::class.java).ifEmpty { return }
        incModificationCount()
    }

    override fun dispose() = Unit

    companion object {
        @JvmStatic
        fun getInstance(project: Project): ModuleModificationTracker = project.service()
    }
}
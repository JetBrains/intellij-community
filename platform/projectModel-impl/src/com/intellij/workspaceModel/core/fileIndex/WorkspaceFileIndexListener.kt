// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex

import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus
import java.util.*

@ApiStatus.Internal
interface WorkspaceFileIndexListener : EventListener {

  companion object {
    @Topic.ProjectLevel
    val TOPIC: Topic<WorkspaceFileIndexListener> = Topic(WorkspaceFileIndexListener::class.java)
  }

  fun workspaceFileIndexChanged(event: WorkspaceFileIndexChangedEvent)

}

@ApiStatus.Internal
abstract class WorkspaceFileIndexChangedEvent(project: Project): EventObject(project) {

  abstract fun getRemovedFileSets(): Collection<WorkspaceFileSet>
  abstract fun getStoredFileSets(): Collection<WorkspaceFileSet>
}

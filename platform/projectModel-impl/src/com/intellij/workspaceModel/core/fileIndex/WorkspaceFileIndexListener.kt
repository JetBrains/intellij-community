// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus
import java.util.EventListener

/**
 * Defines a listener for changes that occur in the [WorkspaceFileIndex].
 *
 * The event reports the [WorkspaceFileSet]s registered by the change together with the [VirtualFile]s whose exclusions were removed.
 * Note that [WorkspaceFileIndex] itself is not versioned, so it is not guaranteed that a registered [WorkspaceFileSet] will
 * still be present in [WorkspaceFileIndex] by the time the event is processed.
 *
 * The listener is called inside Write Action, but processing this event in Write Action may lead to unexpected results.
 */
@ApiStatus.Internal
interface WorkspaceFileIndexListener : EventListener {

  companion object {
    @Topic.ProjectLevel
    val TOPIC: Topic<WorkspaceFileIndexListener> = Topic(WorkspaceFileIndexListener::class.java)
  }

  fun workspaceFileIndexChanged(event: WorkspaceFileIndexChangedEvent)
}

@ApiStatus.Internal
class WorkspaceFileIndexChangedEvent(
  val registeredFileSets: Collection<WorkspaceFileSet>,
  val removedExclusions: Collection<VirtualFile>,
)

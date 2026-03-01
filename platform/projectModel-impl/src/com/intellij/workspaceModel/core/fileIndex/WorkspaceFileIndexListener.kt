// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus
import java.util.EventListener

/**
 * Defines a listener for changes that occur in the [WorkspaceFileIndex].
 *
 * It sends two sets of [WorkspaceFileSet], deleted and added.
 * It also sends two [EntityStorage] before and after the changes.
 * This is necessary because it is expected to resolve [com.intellij.platform.workspace.storage.EntityPointer] of the deleted [WorkspaceFileSet]
 * in storageBefore and [com.intellij.platform.workspace.storage.EntityPointer] of the registered [WorkspaceFileSet] in storageAfter.
 * However, note that [WorkspaceFileIndex] itself is not versioned, so it is not guaranteed that the deleted or added [WorkspaceFileSet] will
 * be present in [WorkspaceFileIndex].
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
  val storageAfter: EntityStorage,
)

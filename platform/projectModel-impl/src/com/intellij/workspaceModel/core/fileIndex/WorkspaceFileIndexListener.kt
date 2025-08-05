// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex

import com.intellij.platform.workspace.storage.EntityStorage
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
sealed interface WorkspaceFileIndexChangedEvent {
  val removedFileSets: Collection<Set<WorkspaceFileSet>>
  val registeredFileSets: Collection<Set<WorkspaceFileSet>>
}

@ApiStatus.Internal
class WorkspaceFileIndexChangedEventImpl(
  override val removedFileSets: Collection<Set<WorkspaceFileSet>>,
  override val registeredFileSets: Collection<Set<WorkspaceFileSet>>,
) : WorkspaceFileIndexChangedEvent

@ApiStatus.Internal
class VersionedWorkspaceFileIndexChangeEvent(
  override val removedFileSets: Collection<Set<WorkspaceFileSet>>,
  override val registeredFileSets: Collection<Set<WorkspaceFileSet>>,
  val storageBefore: EntityStorage,
  val storageAfter: EntityStorage,
) : WorkspaceFileIndexChangedEvent

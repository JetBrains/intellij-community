// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.messages.MessageBusConnection
import com.intellij.util.messages.Topic
import com.intellij.workspaceModel.storage.VersionedStorageChange
import java.util.*

interface WorkspaceModelChangeListener : EventListener {
  /**
   * This method is invoked under Write Action before changes are applied.
   * Please note that [event] contains information about old and new versions of the changed entities, and it's recommended to override it 
   * instead. 
   */
  fun beforeChanged(event: VersionedStorageChange) {}

  /**
   * This method is invoked under Write Action after changes are applied. 
   * If its implementation involves heavy computations, it's better to schedule its execution on a separate thread to avoid blocking Event Dispatch Thread.
   */
  fun changed(event: VersionedStorageChange) {}
}

/**
 * Topics to subscribe to Workspace changes
 */
@Service(Service.Level.PROJECT)
class WorkspaceModelTopics : Disposable {
  companion object {
    @Topic.ProjectLevel
    @JvmField
    val CHANGED = Topic(WorkspaceModelChangeListener::class.java, Topic.BroadcastDirection.NONE, true)

    @JvmStatic
    fun getInstance(project: Project): WorkspaceModelTopics = project.service()
  }

  var modulesAreLoaded = false

  fun notifyModulesAreLoaded() {
    modulesAreLoaded = true
  }

  override fun dispose() {
  }
}

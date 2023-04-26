// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic
import com.intellij.workspaceModel.storage.VersionedStorageChange
import java.util.*

/**
 * For the asynchronous handling of changes form workspace model collect them from [com.intellij.workspaceModel.ide.WorkspaceModel.changesEventFlow]
 */
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
 * Topics to subscribe to Workspace changes.
 *
 * For the asynchronous approach please consider to collect changes from [com.intellij.workspaceModel.ide.WorkspaceModel.changesEventFlow]
 */
@Service(Service.Level.PROJECT)
class WorkspaceModelTopics : Disposable {
  companion object {
    @Topic.ProjectLevel
    @JvmField
    val CHANGED = Topic(WorkspaceModelChangeListener::class.java, Topic.BroadcastDirection.NONE, true)

    /**
     * Subscribe to this topic to be notified about changes in unloaded entities. 
     * Note that most of the code should simply ignore unloaded entities and therefore shouldn't be interested in these changes. 
     */
    @Topic.ProjectLevel
    @JvmField
    val UNLOADED_ENTITIES_CHANGED = Topic(WorkspaceModelChangeListener::class.java, Topic.BroadcastDirection.NONE, true)

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

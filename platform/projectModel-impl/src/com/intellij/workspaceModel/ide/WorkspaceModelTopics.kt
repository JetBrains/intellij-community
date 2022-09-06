// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide

import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.messages.MessageBus
import com.intellij.util.messages.MessageBusConnection
import com.intellij.util.messages.Topic
import com.intellij.workspaceModel.storage.VersionedStorageChange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

interface WorkspaceModelChangeListener : EventListener {
  /**
   * This method is invoked under Write Action before changes are applied.
   * Please note that [event] contains information about old and new versions of the changed entities, and it's recommended to override it 
   * instead. 
   */
  @JvmDefault
  fun beforeChanged(event: VersionedStorageChange) {}

  /**
   * This method is invoked under Write Action after changes are applied. 
   * If its implementation involves heavy computations, it's better to schedule its execution on a separate thread to avoid blocking Event Dispatch Thread.
   */
  @JvmDefault
  fun changed(event: VersionedStorageChange) {}
}

/**
 * Topics to subscribe to Workspace changes
 *
 * Please use [subscribeImmediately] and [subscribeAfterModuleLoading] to subscribe to changes
 */
@Service(Service.Level.PROJECT)
class WorkspaceModelTopics : Disposable {
  companion object {
    /** Please use [subscribeImmediately] and [subscribeAfterModuleLoading] to subscribe to changes */
    @Topic.ProjectLevel
    private val CHANGED = Topic(WorkspaceModelChangeListener::class.java, Topic.BroadcastDirection.NONE, true)

    @JvmStatic
    fun getInstance(project: Project): WorkspaceModelTopics = project.service()
  }

  var modulesAreLoaded = false

  /**
   * Subscribe to topic and start to receive changes immediately.
   *
   * Topic is project-level only without broadcasting - connection expected to be to project message bus only.
   */
  fun subscribeImmediately(connection: MessageBusConnection, listener: WorkspaceModelChangeListener) {
    connection.subscribe(CHANGED, listener)
  }

  /**
   * Subscribe to the topic and start to receive changes only *after* all the modules get loaded.
   * All the events that will be fired before the modules loading, will be collected to the queue. After the modules are loaded, all events
   *   from the queue will be dispatched to listener under the write action and the further events will be dispatched to listener
   *   without passing to event queue.
   *
   * Topic is project-level only without broadcasting - connection expected to be to project message bus only.
   */
  fun subscribeAfterModuleLoading(connection: MessageBusConnection, listener: WorkspaceModelChangeListener) {
    subscribeImmediately(connection, listener)
  }

  fun syncPublisher(messageBus: MessageBus): WorkspaceModelChangeListener = messageBus.syncPublisher(CHANGED)

  suspend fun notifyModulesAreLoaded() {
    modulesAreLoaded = true
  }

  override fun dispose() {
  }
}

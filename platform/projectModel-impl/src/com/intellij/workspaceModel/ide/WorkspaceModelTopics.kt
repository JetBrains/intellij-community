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

    @Topic.ProjectLevel
    private val MODULE_BRIDGE_INITIALIZER = Topic(WorkspaceModelChangeListener::class.java, Topic.BroadcastDirection.NONE, true)

    @Topic.ProjectLevel
    private val PROJECT_LIBS_INITIALIZER = Topic(WorkspaceModelChangeListener::class.java, Topic.BroadcastDirection.NONE, true)

    @JvmStatic
    fun getInstance(project: Project): WorkspaceModelTopics = project.service()
  }

  private val allEventsForModuleBridge = ContainerUtil.createConcurrentList<EventsDispatcher>()
  private val allEvents = ContainerUtil.createConcurrentList<EventsDispatcher>()
  private var sendToQueue = true
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
    if (!sendToQueue) {
      subscribeImmediately(connection, listener)
    }
    else {
      val queue = EventsDispatcher(listener)
      allEvents += queue
      subscribeImmediately(connection, queue)
    }
  }

  /**
   * For internal use only
   */
  fun subscribeProjectLibsInitializer(connection: MessageBusConnection, listener: WorkspaceModelChangeListener) {
    connection.subscribe(PROJECT_LIBS_INITIALIZER, listener)
  }

  /**
   * Internal use only
   */
  fun subscribeModuleBridgeInitializer(connection: MessageBusConnection, listener: WorkspaceModelChangeListener) {
    if (!sendToQueue) {
      connection.subscribe(MODULE_BRIDGE_INITIALIZER, listener)
    }
    else {
      val queue = EventsDispatcher(listener)
      allEventsForModuleBridge += queue
      connection.subscribe(MODULE_BRIDGE_INITIALIZER, queue)
    }
  }

  fun syncProjectLibs(messageBus: MessageBus): WorkspaceModelChangeListener = messageBus.syncPublisher(PROJECT_LIBS_INITIALIZER)
  fun syncModuleBridge(messageBus: MessageBus): WorkspaceModelChangeListener = messageBus.syncPublisher(MODULE_BRIDGE_INITIALIZER)
  fun syncPublisher(messageBus: MessageBus): WorkspaceModelChangeListener = messageBus.syncPublisher(CHANGED)

  suspend fun notifyModulesAreLoaded() {
    val activity = StartUpMeasurer.startActivity("postponed events sending")
    sendToQueue = false

    if (allEvents.isNotEmpty() && allEvents.any { it.events.isNotEmpty() }) {
      val activityInQueue = activity.startChild("events sending (in queue)")
      val application = ApplicationManager.getApplication()
      withContext(Dispatchers.EDT) {
        application.runWriteAction {
          val innerActivity = activityInQueue.endAndStart("events sending")
          allEvents.forEach { queue ->
            queue.collectToQueue = false
            queue.events.forEach { (isBefore, event) ->
              if (isBefore) queue.originalListener.beforeChanged(event)
              else queue.originalListener.changed(event)
            }
            queue.events.clear()
          }
          innerActivity.end()
        }
      }
    }
    else {
      allEvents.forEach { queue -> queue.collectToQueue = false }
    }
    allEvents.clear()

    if (allEventsForModuleBridge.isNotEmpty() && allEventsForModuleBridge.any { it.events.isNotEmpty() }) {
      val activityInQueue = activity.startChild("events sending (in queue first)")
      val application = ApplicationManager.getApplication()
      withContext(Dispatchers.EDT) {
        application.runWriteAction {
          val innerActivity = activityInQueue.endAndStart("events sending first")
          allEventsForModuleBridge.forEach { queue ->
            queue.collectToQueue = false
            queue.events.forEach { (isBefore, event) ->
              if (isBefore) queue.originalListener.beforeChanged(event)
              else queue.originalListener.changed(event)
            }
            queue.events.clear()
          }
          innerActivity.end()
        }
      }
    }
    else {
      allEventsForModuleBridge.forEach { queue -> queue.collectToQueue = false }
    }
    allEventsForModuleBridge.clear()

    modulesAreLoaded = true
    activity.end()
  }

  private class EventsDispatcher(val originalListener: WorkspaceModelChangeListener) : WorkspaceModelChangeListener {
    val events = mutableListOf<Pair<Boolean, VersionedStorageChange>>()
    var collectToQueue = true

    override fun beforeChanged(event: VersionedStorageChange) {
      if (collectToQueue) {
        events += true to event
      }
      else {
        originalListener.beforeChanged(event)
      }
    }

    override fun changed(event: VersionedStorageChange) {
      if (collectToQueue) {
        events += false to event
      }
      else {
        originalListener.changed(event)
      }
    }
  }

  override fun dispose() {
    allEvents.forEach { it.events.clear() }
    allEvents.clear()
  }
}

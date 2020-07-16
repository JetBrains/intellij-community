// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide

import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.messages.MessageBus
import com.intellij.util.messages.MessageBusConnection
import com.intellij.util.messages.Topic
import com.intellij.workspaceModel.ide.impl.moduleLoadingActivity
import com.intellij.workspaceModel.storage.VersionedStorageChanged
import java.util.*

interface WorkspaceModelChangeListener : EventListener {
  fun beforeChanged(event: VersionedStorageChanged) {}
  fun changed(event: VersionedStorageChanged) {}
}

/**
 * Topics to subscribe to Workspace changes
 *
 * Please use [subscribeImmediately] and [subscribeAfterModuleLoading] to subscribe to changes
 */
class WorkspaceModelTopics : Disposable {
  companion object {
    private val CHANGED = Topic("Workspace Model Changed", WorkspaceModelChangeListener::class.java)

    fun getInstance(project: Project): WorkspaceModelTopics = ServiceManager.getService(project, WorkspaceModelTopics::class.java)
  }

  private val allEvents = ContainerUtil.createConcurrentList<EventsDispatcher>()
  private var sendToQueue = true
  internal var modulesAreLoaded = false

  /**
   * Subscribe to topic and start to receive changes immediately
   */
  fun subscribeImmediately(connection: MessageBusConnection, listener: WorkspaceModelChangeListener) {
    connection.subscribe(CHANGED, listener)
  }

  /**
   * Subscribe to the topic and start to receive changes only *after* all the modules get loaded.
   * All the events that will be fired before the modules loading, will be collected to the queue. After the modules are loaded, all events
   *   from the queue will be dispatched to listener under the write action and the further events will be dispatched to listener
   *   without passing to event queue.
   */
  fun subscribeAfterModuleLoading(connection: MessageBusConnection, listener: WorkspaceModelChangeListener) {
    if (!sendToQueue) {
      connection.subscribe(CHANGED, listener)
    }
    else {
      val queue = EventsDispatcher(listener)
      allEvents += queue
      connection.subscribe(CHANGED, queue)
    }
  }

  fun syncPublisher(messageBus: MessageBus): WorkspaceModelChangeListener = messageBus.syncPublisher(CHANGED)

  internal fun notifyModulesAreLoaded() {
    val activity = StartUpMeasurer.startActivity("(wm) After modules are loaded")
    sendToQueue = false
    val application = ApplicationManager.getApplication()
    application.invokeAndWait {
      application.runWriteAction {
        val innerActivity = activity.startChild("(wm) WriteAction. After modules are loaded")
        allEvents.forEach { queue ->
          queue.events.forEach { (isBefore, event) ->
            if (isBefore) queue.originalListener.beforeChanged(event)
            else queue.originalListener.changed(event)
          }
          queue.events.clear()
        }
        innerActivity.end()
      }
    }
    allEvents.forEach { it.collectToQueue = false }
    allEvents.clear()
    modulesAreLoaded = true
    activity.end()
    moduleLoadingActivity.end()
  }

  private class EventsDispatcher(val originalListener: WorkspaceModelChangeListener) : WorkspaceModelChangeListener {

    internal val events = mutableListOf<Pair<Boolean, VersionedStorageChanged>>()
    internal var collectToQueue = true

    override fun beforeChanged(event: VersionedStorageChanged) {
      if (collectToQueue) {
        events += true to event
      }
      else {
        originalListener.beforeChanged(event)
      }
    }

    override fun changed(event: VersionedStorageChanged) {
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

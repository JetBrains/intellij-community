// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.recentProjects

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.ide.RecentProjectsManager
import com.intellij.ide.RecentProjectsManager.RecentProjectsChange
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

private val LOG = logger<RecentProjectsPreload>()

/**
 * Starts the background collection of the recent projects without [project] that its welcome-experience frame shows.
 * The first collection of the panel takes the list, see [createPreloadedRecentProjectsWithoutCurrentCollector].
 */
@Internal
fun preloadRecentProjectsWithoutCurrent(project: Project) {
  val holder = project.service<RecentProjectsWithoutCurrentPreloadHolder>()
  holder.preload = RecentProjectsPreload(holder.coroutineScope, ProjectCollectors.createRecentProjectsWithoutCurrentCollector(project))
}

/**
 * Returns the collector of the recent projects without [currentProject].
 * Its first call takes the list of [preloadRecentProjectsWithoutCurrent] when the list is ready; every other call collects.
 */
@Internal
fun createPreloadedRecentProjectsWithoutCurrentCollector(currentProject: Project): () -> List<RecentProjectTreeItem> {
  val collector = ProjectCollectors.createRecentProjectsWithoutCurrentCollector(currentProject)
  val preload = currentProject.serviceIfCreated<RecentProjectsWithoutCurrentPreloadHolder>()?.preload ?: return collector
  return { preload.take() ?: collector() }
}

@Service(Service.Level.PROJECT)
private class RecentProjectsWithoutCurrentPreloadHolder(@JvmField val coroutineScope: CoroutineScope) {
  @Volatile
  @JvmField
  var preload: RecentProjectsPreload? = null
}

/**
 * Runs [collector] in the background, so the first collection of a panel can take the list instead of collecting on the EDT.
 * The list goes to the panel only when the collection is complete and no recent-project change came after its start.
 */
@VisibleForTesting
@Internal
class RecentProjectsPreload(scope: CoroutineScope, collector: () -> List<RecentProjectTreeItem>) {
  private val isStale = AtomicBoolean(false)
  private val isTaken = AtomicBoolean(false)
  private val collected = AtomicReference<List<RecentProjectTreeItem>?>()
  private val connection = ApplicationManager.getApplication().messageBus.connect(scope)

  @VisibleForTesting
  val job: Job

  init {
    // the subscription comes first: a change during the collection may be missing from the list, so it makes the list stale
    connection.subscribe(RecentProjectsManager.RECENT_PROJECTS_CHANGE_TOPIC, object : RecentProjectsChange {
      override fun change() {
        isStale.set(true)
      }
    })
    job = scope.launch(Dispatchers.Default) {
      try {
        collected.set(collector())
      }
      catch (e: Throwable) {
        rethrowControlFlowException(e)
        // the panel collects on the EDT then, and the failure shows there as before
        LOG.debug("Cannot preload the recent projects", e)
      }
    }
  }

  /**
   * Returns the list on the first call when the collection is complete and no change came after its start.
   * Otherwise, and on every later call, returns null at once.
   */
  fun take(): List<RecentProjectTreeItem>? {
    if (!isTaken.compareAndSet(false, true)) {
      return null
    }
    connection.disconnect()
    val list = collected.get()
    if (list == null || isStale.get()) {
      job.cancel()
      return null
    }
    return list
  }
}

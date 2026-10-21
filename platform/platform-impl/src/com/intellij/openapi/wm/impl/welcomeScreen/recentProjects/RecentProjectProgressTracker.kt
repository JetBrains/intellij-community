// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.recentProjects

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.impl.welcomeScreen.cloneableProjects.CloneableProjectsService
import com.intellij.openapi.wm.impl.welcomeScreen.cloneableProjects.CloneableProjectsService.CloneProjectListener
import com.intellij.openapi.wm.impl.welcomeScreen.cloneableProjects.CloneableProjectsService.CloneStatus
import com.intellij.openapi.wm.impl.welcomeScreen.cloneableProjects.CloneableProjectsService.CloneableProject
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.TaskHandle
import com.intellij.platform.ide.progress.TaskId
import com.intellij.platform.ide.progress.TaskStatus
import com.intellij.platform.ide.progress.TaskStorage
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.projectIdOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.nio.file.Path

/**
 * Tells the welcome screen which of its recent projects run a background task.
 *
 * [TaskStorage] holds the tasks of the whole IDE. This narrows them to the projects [RecentProjectsService] loaded without a frame, and
 * keys them the way the rows do. It keeps no task state of its own.
 */
@Service(Service.Level.APP)
internal class RecentProjectProgressTracker(coroutineScope: CoroutineScope) {
  private val taskStorage = TaskStorage.getInstance()

  // The one thing this is told rather than works out: the recent projects the welcome screen holds, by the id their tasks carry.
  private val followedProjects = MutableStateFlow<Map<ProjectId, Path>>(emptyMap())

  // What [projectsProgress] is made of, which the live flow of one row reads to follow each task.
  private val taskHandles: StateFlow<Map<Path, List<TaskHandle>>> =
    combine(taskStorage.tasks, followedProjects, ::tasksByProject).stateIn(coroutineScope, SharingStarted.Eagerly, emptyMap())

  /**
   * What every recent project the welcome screen holds is running.
   *
   * This reports again when an operation starts or ends, so a row knows whether to show its spinner and what to call it. The text and
   * the fraction are the ones reported then. A window takes [getProgressFor] instead, which reports on every report.
   */
  val projectsProgress: StateFlow<ProjectsProgress> = taskHandles
    .map { byProject -> ProjectsProgress(byProject.mapValues { (_, running) -> running.map { it.toProgress(it.progress.value) } }) }
    .stateIn(coroutineScope, SharingStarted.Eagerly, ProjectsProgress.NONE)

  /**
   * Follows the tasks of [project], held for the recent project at [projectPath], until the project is disposed.
   *
   * Call it once, when the project is held: disposing the project stops the follow, so no caller has to remember to.
   */
  fun follow(projectPath: Path, project: Project) {
    if (project.isDisposed) return
    val projectId = project.projectIdOrNull() ?: return
    followedProjects.update { it + (projectId to projectPath) }
    Disposer.register(project) {
      followedProjects.update { followed -> followed.filterKeys { it != projectId } }
    }
  }

  /**
   * What each operation of the row of [item] shows, and again on every report.
   *
   * Nothing is read until something collects this, and nothing is left behind when the collector stops.
   */
  fun getProgressFor(item: RecentProjectTreeItem): Flow<List<RecentProjectProgress>> = when (item) {
    is RecentProjectItem -> getTaskProgress(item)
    is CloneableProjectItem -> getCloneProgress(item.cloneableProject)
    is ProjectsGroupItem,
    is ProviderRecentProjectItem,
    is RootItem,
      -> flowOf(emptyList())
  }

  /**
   * Cancels the operation [id].
   *
   * The window asks for this once the user has answered what [RecentProjectProgress.cancelQuestion] asks, or at once when it asks
   * nothing. An operation that has already ended is gone from here, and cancelling it does nothing.
   */
  fun cancel(id: ProgressId) {
    when (id) {
      // The storage refuses this for a task the platform did not declare cancellable, which is also one the window offers no button for.
      is ProgressId.Task -> {
        taskStorage.tasks.value[id.taskId]?.let { taskStorage.setStatus(it, TaskStatus.Canceled(TaskStatus.Source.USER)) }
      }
      is ProgressId.Clone -> {
        val clones = CloneableProjectsService.getInstance()
        clones.collectCloneableProjects()
          .map { it.cloneableProject }
          .find { it.projectPath == id.projectPath }
          ?.let(clones::cancelClone)
      }
    }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private fun getTaskProgress(item: RecentProjectItem): Flow<List<RecentProjectProgress>> =
    taskHandles.map { byProject -> item.projectNioPath()?.let(byProject::get).orEmpty() }.distinctUntilChanged().flatMapLatest { tasks ->
      if (tasks.isEmpty()) {
        flowOf(emptyList())
      }
      else {
        combine(tasks.map { task -> task.progress.map { task.toProgress(it) } }) { it.asList() }
      }
    }

  /** A clone reports into an indicator of its own, and the service tells of its end on the UI thread. */
  private fun getCloneProgress(clone: CloneableProject): Flow<List<RecentProjectProgress>> = callbackFlow {
    fun report() {
      trySend(if (clone.cloneStatus == CloneStatus.PROGRESS) listOf(clone.toProgress()) else emptyList())
    }

    // The indicator takes a delegate through its interface but gives it back only through the class, so the delegate also stops itself.
    val reportsProgress = object : AbstractProgressIndicatorExBase() {
      @Volatile
      var stopped = false

      override fun onProgressChange() {
        if (!stopped) report()
      }
    }
    clone.progressIndicator.addStateDelegate(reportsProgress)
    val connection = ApplicationManager.getApplication().messageBus.connect()
    connection.subscribe(CloneableProjectsService.TOPIC, object : CloneProjectListener {
      override fun onCloneRemoved(): Unit = report()
      override fun onCloneSuccess(): Unit = report()
      override fun onCloneFailed(): Unit = report()
      override fun onCloneCanceled(): Unit = report()
    })
    report()
    awaitClose {
      reportsProgress.stopped = true
      (clone.progressIndicator as? AbstractProgressIndicatorExBase)?.removeStateDelegate(reportsProgress)
      connection.disconnect()
    }
  }.conflate()

  private fun tasksByProject(tasks: Map<TaskId, TaskHandle>, followed: Map<ProjectId, Path>): Map<Path, List<TaskHandle>> =
    tasks.values
      .mapNotNull { task -> followed[task.info.projectId]?.let { path -> path to task } }
      .groupBy({ (path, _) -> path }) { (_, task) -> task }
      .mapValues { (_, running) -> running.sortedWith(ROW_ORDER) }

  companion object {
    fun getInstance(): RecentProjectProgressTracker = service()
  }
}

/**
 * Puts the task the user most likely asked for first.
 *
 * A task the platform keeps out of the status bar yields to one meant to be seen, which is what tells indexing and the like apart from a
 * version control operation, and a task that cannot be cancelled yields to one that can. Ties keep the task that started first, because
 * [TaskStorage] hands out its ids in that order.
 */
private val ROW_ORDER = compareBy<TaskHandle>(
  { !it.info.visibleInStatusBar },
  { it.info.cancellation !is TaskCancellation.Cancellable },
  { it.info.id.value },
)

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.recentProjects

import com.intellij.ide.IdeBundle
import com.intellij.ide.startup.impl.StartupManagerImpl
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.ide.trustedProjects.TrustedProjectsDialog
import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

private val LOG = logger<RecentProjectsService>()

/**
 * Runs recent-project operations for the welcome screen on projects loaded without a frame.
 *
 * It loads a recent project once per path and holds it, so several projectless actions (Update, New Branch...) share one instance and do not
 * load or dispose it from under each other. It relays the loaded project's notifications to the welcome screen, because a frameless project
 * shows none of its own.
 *
 * The service scope loads the project. Each held project then gets its own child scope that runs the project's operations. On disposal that
 * scope is canceled and joined before the project is closed, so no operation runs on a disposed project.
 *
 * It disposes every held project before the welcome screen opens a recent project, so a held instance does not collide with the project being
 * opened (for example on the VCS log). TODO(IJPL-256216): a finer policy that disposes a held project when no operation and no relayed
 * notification needs it, and that also covers non-welcome-screen open paths.
 */
@Service(Service.Level.APP)
internal class RecentProjectsService(private val coroutineScope: CoroutineScope) {
  private val projectManager = ProjectManagerEx.getInstanceEx()
  private val taskTracker = RecentProjectTaskTracker.getInstance()
  private val loadMutex = Mutex()

  // Concurrent because [isFramelessRecentProject] reads it from the project that is initializing, while the load still holds [loadMutex].
  private val heldProjects: MutableMap<Path, HeldProject> = ConcurrentHashMap()

  /**
   * A held project and the scope that runs its operations. The scope is a child of the service scope. [owned] is true when the service loaded
   * the project. It is false when the project was already open, so the service reuses that instance and never closes it.
   */
  private class HeldProject(val project: Project, val scope: CoroutineScope, val owned: Boolean)

  /**
   * Loads and holds the recent project at [projectPath], then runs [operation] on the project scope. Confirms trust first, and does nothing
   * when the user declines. The load runs on the service scope, so the caller does not manage a coroutine.
   */
  fun runOnRecentProject(projectPath: Path, operation: suspend (Project) -> Unit) {
    coroutineScope.launch {
      val held = getOrLoad(projectPath) ?: return@launch
      held.scope.launch {
        if (held.project.isDisposed) {
          LOG.warn("Cannot run the operation: the recent project at $projectPath is already disposed")
          return@launch
        }
        operation(held.project)
      }
    }
  }

  /**
   * Confirms trust, then loads the recent project at [projectPath] once and holds it with its own operation scope. Later calls return the same
   * instance. Returns null when the user declines to trust the project.
   */
  private suspend fun getOrLoad(projectPath: Path): HeldProject? {
    loadMutex.withLock {
      cachedHeldProject(projectPath)?.let { return it }
      findOpenProject(projectPath)?.let { return hold(projectPath, it, owned = false) }
    }
    if (!confirmTrust(projectPath)) {
      return null
    }
    loadMutex.withLock {
      cachedHeldProject(projectPath)?.let { return it }
      findOpenProject(projectPath)?.let { return hold(projectPath, it, owned = false) }
      LOG.info("Recent project $projectPath: loading it without a frame")
      val project = projectManager.loadProject(projectPath)
      // Held before the project is initialized, because the init activities ask whether it is one of these and the answer is cached from the
      // first time it is asked.
      val held = hold(projectPath, project, owned = true)
      try {
        initLoadedProject(project)
      }
      catch (e: Throwable) {
        // The project is loaded by now, so a failed initialization has to close it: it is out of the map, and nothing else would.
        heldProjects.remove(projectPath)
        withContext(NonCancellable) { dispose(held) }
        throw e
      }
      return held
    }
  }

  /**
   * Runs the init project activities on a project that was loaded but never opened.
   *
   * [ProjectManagerEx.loadProject] stops before them: the platform runs them from `ProjectManagerImpl.runInitProjectActivities`, on the
   * open path only. Without them [com.intellij.openapi.startup.StartupManagerEx.startupActivityPassed] stays false for the whole life of
   * the held project, and that flag backs [Project.isInitialized]: the project would report itself as uninitialized to the whole IDE
   * while it is being used to run actions.
   *
   * @see `com.intellij.ide.lightEdit.project.LightEditProjectManager`
   */
  private suspend fun initLoadedProject(project: Project) {
    (project.serviceAsync<StartupManager>() as StartupManagerImpl).initProject()
  }

  // Returns the held project when it is still alive, and forgets a disposed one. Must run under [loadMutex].
  private fun cachedHeldProject(projectPath: Path): HeldProject? {
    val held = heldProjects[projectPath] ?: return null
    if (held.project.isDisposed) {
      heldProjects.remove(projectPath)
      return null
    }
    return held
  }

  // Holds [project] with its own operation scope. Relays only a project the service loaded, because an already-open project shows its own
  // notifications on its frame. Must run under [loadMutex].
  private fun hold(projectPath: Path, project: Project, owned: Boolean): HeldProject {
    val held = HeldProject(project, coroutineScope.childScope("WelcomeScreenRecentProject"), owned)
    if (owned) {
      relayNotificationsToWelcomeScreen(project)
      taskTracker.follow(projectPath, project)
    }
    heldProjects[projectPath] = held
    return held
  }

  /** Whether [project] is one the service loaded without a frame, which has no use for the activities a frame's project runs. */
  internal fun isFramelessRecentProject(project: Project): Boolean =
    heldProjects.values.any { it.owned && it.project === project }

  // An already-open project for the same location. IntelliJ does not support two live projects for one location: loading a second instance
  // collides on the project storages (for example the VCS user storage), so the open one is reused instead.
  private fun findOpenProject(projectPath: Path): Project? {
    val target = projectPath.toString()
    return projectManager.openProjects.firstOrNull { project ->
      if (project.isDisposed) {
        return@firstOrNull false
      }
      val basePath = project.basePath
      basePath != null && FileUtil.pathsEqual(basePath, target)
    }
  }

  /**
   * Cancels the operations of [held] and closes its project, so an in-flight operation never runs on a disposed project. Closes only a project
   * the service loaded: an already-open project is owned by its own frame. The caller forgets it first, so nothing finds it while it goes.
   */
  private suspend fun dispose(held: HeldProject) {
    held.scope.coroutineContext.job.cancelAndJoin()
    if (held.owned) {
      projectManager.forceCloseProjectAsync(held.project)
    }
  }

  /**
   * Disposes and forgets every held project, without saving, so nothing is written to the project settings. Called before the welcome screen
   * opens a recent project. Each project's operation scope is canceled and joined before the project is closed, so an in-flight operation never runs on a
   * disposed project. [ProjectManagerEx.forceCloseProjectAsync] switches to the EDT under a write-intent lock and skips an already-disposed
   * project.
   */
  suspend fun disposeHeldProjects() {
    val held = loadMutex.withLock {
      val copy = heldProjects.toMap()
      heldProjects.clear()
      copy
    }
    held.values.forEach { dispose(it) }
  }

  /**
   * Confirms trust before the project is loaded. An already-answered project shows no dialog.
   *
   * The dialog's own result only says the user did not cancel: "Preview in Safe Mode" also returns true, and it records the project as
   * distrusted. A safe mode project refuses every Git command, so the trust state, not the dialog's result, decides whether to go on.
   */
  private suspend fun confirmTrust(projectPath: Path): Boolean {
    val projectName = projectPath.fileName?.toString() ?: projectPath.toString()
    TrustedProjectsDialog.confirmOpeningOrLinkingUntrustedProject(
      projectRoot = projectPath,
      project = null,
      title = IdeBundle.message("untrusted.project.open.dialog.title", projectName),
    )
    return TrustedProjects.isProjectTrusted(projectPath)
  }

  // The loaded project has no frame, so its notifications show nowhere. Re-post each one at the application level, where the welcome frame
  // shows it. The subscription ends with the project.
  private fun relayNotificationsToWelcomeScreen(project: Project) {
    project.messageBus.connect(project).subscribe(Notifications.TOPIC, object : Notifications {
      override fun notify(notification: Notification) {
        notification.notify(null)
      }
    })
  }

  companion object {
    fun getInstance(): RecentProjectsService = service()
  }
}

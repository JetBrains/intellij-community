// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.recentProjects

import com.intellij.ide.IdeBundle
import com.intellij.ide.trustedProjects.TrustedProjectsDialog
import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

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
@ApiStatus.Internal
@Service(Service.Level.APP)
class RecentProjectsService(private val coroutineScope: CoroutineScope) {
  private val loadMutex = Mutex()
  private val heldProjects = mutableMapOf<Path, HeldProject>()

  /**
   * A held project and the scope that runs its operations. The scope is a child of the service scope. [owned] is true when the service loaded
   * the project. It is false when the project was already open, so the service reuses that instance and never closes it.
   */
  private class HeldProject(val project: Project, val scope: CoroutineScope, val owned: Boolean)

  /**
   * Loads and holds the recent project at [projectPath], then runs its registered update on the project scope. Confirms trust first, and does
   * nothing when the user declines. The load runs on the service scope, so the caller does not manage a coroutine.
   */
  fun updateRecentProject(projectPath: Path) {
    coroutineScope.launch {
      val held = getOrLoad(projectPath) ?: return@launch
      held.scope.launch {
        RecentProjectUpdater.findUpdater(projectPath)?.update(held.project, projectPath)
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
      val project = ProjectManagerEx.getInstanceEx().loadProject(projectPath)
      return hold(projectPath, project, owned = true)
    }
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
    }
    heldProjects[projectPath] = held
    return held
  }

  // An already-open project for the same location. IntelliJ does not support two live projects for one location: loading a second instance
  // collides on the project storages (for example the VCS user storage), so the open one is reused instead.
  private fun findOpenProject(projectPath: Path): Project? {
    val target = projectPath.toString()
    return ProjectManager.getInstance().openProjects.firstOrNull { project ->
      val basePath = project.basePath
      basePath != null && FileUtil.pathsEqual(basePath, target)
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
      val copy = heldProjects.values.toList()
      heldProjects.clear()
      copy
    }
    val projectManager = ProjectManagerEx.getInstanceEx()
    for (heldProject in held) {
      heldProject.scope.coroutineContext.job.cancelAndJoin()
      // Close only a project the service loaded. An already-open project is owned by its own frame.
      if (heldProject.owned) {
        projectManager.forceCloseProjectAsync(heldProject.project)
      }
    }
  }

  // Trust must be confirmed before the project is loaded. Already-trusted projects return true without a dialog.
  private suspend fun confirmTrust(projectPath: Path): Boolean {
    val projectName = projectPath.fileName?.toString() ?: projectPath.toString()
    return TrustedProjectsDialog.confirmOpeningOrLinkingUntrustedProject(
      projectRoot = projectPath,
      project = null,
      title = IdeBundle.message("untrusted.project.open.dialog.title", projectName),
    )
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

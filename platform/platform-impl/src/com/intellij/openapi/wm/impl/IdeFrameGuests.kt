// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.ex.WindowManagerEx
import org.jetbrains.annotations.ApiStatus

/**
 * The frame-guest queries of `WindowManagerEx` for the platform code that runs with and without a window manager.
 *
 * A frame has three projects that the platform keeps apart:
 * - the frame owner, [frameOwnerOf]: the project that owns the frame and its tool windows;
 * - the active project, [activeProjectOf]: the project that the frame UI gives to an action;
 * - the project of the tool-window manager of an action event, `getToolWindowManagerForEvent` in `ToolWindowManagerLifecycle.kt`:
 *   the manager of the tool window in the event context, else the manager of the frame owner.
 *
 * A frame guest is recorded through the window manager, see `WindowManagerEx.addFrameGuest`. A window manager that does not
 * exist yet, or that is no `WindowManagerEx`, holds no guest, so each query gives the answer of a project with a frame of its
 * own. The queries never create the window manager service.
 */
@ApiStatus.Internal
object IdeFrameGuests {
  /** The project that owns the frame that shows [project]: [project] itself unless it is a frame guest. */
  @JvmStatic
  fun frameOwnerOf(project: Project): Project = windowManager()?.getFrameOwner(project) ?: project

  /**
   * The frame owner of the project of [event], or `null` without a live project: the project whose manager shows the tool
   * windows of the frame of the event.
   */
  @JvmStatic
  fun frameOwnerOf(event: AnActionEvent): Project? = event.project?.takeIf { !it.isDisposed }?.let(::frameOwnerOf)

  /** Tells whether [project] is a frame guest, see `WindowManagerEx.isFrameGuest`. */
  @JvmStatic
  fun isFrameGuest(project: Project): Boolean = windowManager()?.isFrameGuest(project) == true

  /** The frame guests of [frameOwner], in the order they joined. */
  @JvmStatic
  fun frameGuestsOf(frameOwner: Project): List<Project> = windowManager()?.getFrameGuests(frameOwner) ?: emptyList()

  /** The open projects that have a frame of their own: every open project except the frame guests. */
  @JvmStatic
  fun openFrameOwners(): List<Project> {
    val record = windowManager()
    val openProjects = ProjectManager.getInstance().openProjects
    return if (record == null) openProjects.asList() else openProjects.filter { !record.isFrameGuest(it) }
  }

  /**
   * The active project of the frame that shows [project] for the UI, see `WindowManagerEx.getActiveProject`.
   * A guest that is not initialized, because it still opens or closes, gives way to the owner of the frame.
   */
  @JvmStatic
  fun activeProjectOf(project: Project): Project {
    val record = windowManager() ?: return project
    val active = record.getActiveProject(project)
    return if (active === project || active.isInitialized) active else record.getFrameOwner(project)
  }

  private fun windowManager(): WindowManagerEx? {
    return ApplicationManager.getApplication()?.serviceIfCreated<WindowManager>() as? WindowManagerEx
  }
}

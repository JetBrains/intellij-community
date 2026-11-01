// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.ex.WindowManagerEx

/*
 * The frame-guest queries of `WindowManagerEx` for the platform code that runs with and without a window manager.
 *
 * A frame guest is recorded through the window manager, see `WindowManagerEx.addFrameGuest`. A window manager that does not
 * exist yet, or that is no `WindowManagerEx`, holds no guest, so each query gives the answer of a project with a frame of its
 * own. The queries never create the window manager service.
 */

/**
 * The project that owns the frame that shows [project]: [project] itself unless it is a frame guest.
 * The JVM name lets a Java action of this module call it as `FrameGuestsKt.frameOwnerOf`.
 */
@JvmName("frameOwnerOf")
internal fun frameOwnerOf(project: Project): Project = frameGuestRecord()?.getFrameOwner(project) ?: project

/** Tells whether [project] is a frame guest, see `WindowManagerEx.isFrameGuest`. */
internal fun isFrameGuest(project: Project): Boolean = frameGuestRecord()?.isFrameGuest(project) == true

/** The frame guests of [frameOwner], in the order they joined. */
internal fun frameGuestsOf(frameOwner: Project): List<Project> = frameGuestRecord()?.getFrameGuests(frameOwner) ?: emptyList()

/** The open projects that have a frame of their own: every open project except the frame guests. */
internal fun openFrameOwners(): List<Project> {
  val record = frameGuestRecord()
  val openProjects = ProjectManager.getInstance().openProjects
  return if (record == null) openProjects.asList() else openProjects.filter { !record.isFrameGuest(it) }
}

/** The active project of the frame that shows [project], see `WindowManagerEx.getActiveProject`. */
internal fun activeProjectOf(project: Project): Project = frameGuestRecord()?.getActiveProject(project) ?: project

private fun frameGuestRecord(): WindowManagerEx? {
  return ApplicationManager.getApplication()?.serviceIfCreated<WindowManager>() as? WindowManagerEx
}

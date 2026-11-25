// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options.newEditor

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.options.ConfigurableGroup
import com.intellij.openapi.options.ex.FrameProjectsConfigurableGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ex.ProjectFrameCapabilitiesService
import com.intellij.openapi.wm.ex.ProjectFrameCapability
import com.intellij.openapi.wm.impl.IdeFrameGuests
import com.intellij.util.containers.DisposableWrapperList
import org.jetbrains.annotations.ApiStatus

/**
 * The projects that the Settings window of one frame configures, and the project that the window shows now.
 *
 * A frame with frame guests shows several projects. The Settings window of such a frame belongs to the frame owner, and its
 * project selector lists [projects]. A frame owner with [ProjectFrameCapability.SUPPRESS_OWN_PROJECT_SETTINGS] gets a selection
 * also when [projects] is empty, so that its window can take the projects that join later.
 * With an empty [projects], [selected] is the frame owner. A project-level page shows the settings of [selected], and all pages share that
 * selection. The frame owner stores the selection, so the next Settings window opens on the same project.
 *
 * [projects] and [selected] change on the EDT.
 */
@ApiStatus.Internal
class SettingsFrameProjects internal constructor(val frameOwner: Project, projects: List<Project>) {
  private val listeners = DisposableWrapperList<Runnable>()

  /** The projects of the selector, in the order of the frame, see [IdeFrameGuests.orderedProjectsOf]. */
  var projects: List<Project> = projects
    private set

  var selected: Project = storedSelection(frameOwner, projects) ?: projects.firstOrNull() ?: frameOwner
    private set

  /** [listener] runs once after each call that changed [projects] or [selected], until [parentDisposable] is disposed. */
  fun addListener(listener: Runnable, parentDisposable: Disposable) {
    listeners.add(listener, parentDisposable)
  }

  private fun fireChanged() {
    for (listener in listeners) {
      listener.run()
    }
  }

  /** Makes [project] the project that the Settings window shows. A project that the selector does not list changes nothing. */
  fun select(project: Project) {
    if (project === selected || project !in projects) {
      return
    }
    selected = project
    PropertiesComponent.getInstance(frameOwner).setValue(SELECTED_PROJECT, selectionKey(project))
    fireChanged()
  }

  /**
   * Removes [project] from the selector, because the project closes. The selection moves to the next project of the selector.
   * When the selector lists no project after the call, [frameOwner] is selected, so no closed project stays selected.
   *
   * The stored selection stays the last project that the user chose. A frame closes its frame guests one by one, so a
   * selection that this call stored would replace that choice on each close of the frame.
   */
  fun remove(project: Project) {
    val index = projects.indexOf(project)
    if (index < 0) {
      return
    }
    projects = projects - project
    if (selected === project) {
      selected = if (projects.isEmpty()) frameOwner else projects[index.coerceAtMost(projects.lastIndex)]
    }
    fireChanged()
  }

  /**
   * Adds [project] to the selector, because the project joins the frame. The selector keeps the order of [listProjects].
   * When the selector lists no project before the call, [project] becomes the selected project.
   */
  fun add(project: Project) {
    if (project in projects) {
      return
    }
    val order = listProjects(frameOwner)
    projects = (projects + project).sortedBy { order.indexOf(it).let { index -> if (index < 0) Int.MAX_VALUE else index } }
    if (selected !in projects) {
      selected = project
    }
    fireChanged()
  }

  companion object {
    private const val SELECTED_PROJECT = "settings.editor.selected.project"

    /**
     * The projects that the Settings window of the frame of [frameOwner] configures: the frame owner and its frame guests.
     * A frame owner with [ProjectFrameCapability.SUPPRESS_OWN_PROJECT_SETTINGS] is not one of them.
     * A frame guest that still opens is not one of them either.
     */
    @JvmStatic
    fun listProjects(frameOwner: Project): List<Project> {
      val suppressesOwner = suppressesOwnSettings(frameOwner)
      return IdeFrameGuests.orderedProjectsOf(frameOwner).filter { project ->
        if (project === frameOwner) !suppressesOwner else !project.isDisposed && project.isInitialized
      }
    }

    /** `true` when [frameOwner] has [ProjectFrameCapability.SUPPRESS_OWN_PROJECT_SETTINGS]. */
    @JvmStatic
    fun suppressesOwnSettings(frameOwner: Project): Boolean {
      return service<ProjectFrameCapabilitiesService>().has(frameOwner, ProjectFrameCapability.SUPPRESS_OWN_PROJECT_SETTINGS)
    }

    /**
     * The selection of the Settings window of the frame of [frameOwner], or `null` when that window needs no project selector:
     * the frame owner is the only project to configure. A frame owner that suppresses its own settings always gets a selection,
     * also with no project to configure.
     */
    @JvmStatic
    fun create(frameOwner: Project): SettingsFrameProjects? {
      if (frameOwner.isDefault || frameOwner.isDisposed) {
        return null
      }
      val projects = listProjects(frameOwner)
      if (!suppressesOwnSettings(frameOwner) && (projects.isEmpty() || projects.size == 1 && projects[0] === frameOwner)) {
        return null
      }
      return SettingsFrameProjects(frameOwner, projects)
    }

    /** The selection of the Settings window that shows [groups], or `null` when that window has no project selector. */
    @JvmStatic
    fun of(groups: List<ConfigurableGroup>): SettingsFrameProjects? {
      return groups.firstNotNullOfOrNull { (it as? FrameProjectsConfigurableGroup)?.projects }
    }

    private fun storedSelection(frameOwner: Project, projects: List<Project>): Project? {
      val key = PropertiesComponent.getInstance(frameOwner).getValue(SELECTED_PROJECT) ?: return null
      return projects.firstOrNull { selectionKey(it) == key }
    }

    private fun selectionKey(project: Project): String = project.basePath ?: project.name
  }
}

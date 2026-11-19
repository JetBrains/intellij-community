// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

/**
 * The project that one tool window shows, in a frame that shows several projects.
 *
 * Each entry is a project of the frame that has the tool window. Each tool window has its own selection.
 * The selection does not follow the active project of the frame. It reads [activeProject] only in [resolve], and once more
 * for a provisional selection, see [add].
 *
 * The class has no state of the UI and no lock. Its owner calls it on the EDT.
 */
internal class ToolWindowProjectSelection<P : Any>(private val activeProject: () -> P?) {
  /** The frame owner first, when it is an entry, and then the frame guests in the order they joined. */
  val entries: List<P>
    field = ArrayList<P>()

  var selected: P? = null
    private set

  /** The selection is the first entry, because the active project had no entry at the time of [resolve]. */
  private var isProvisional = false

  /**
   * Adds [entry]. With [first], the entry goes before every other entry, as the frame owner does.
   * A provisional selection moves to the active project when that project has an entry now.
   *
   * @return `true` when the selection changed
   */
  fun add(entry: P, first: Boolean = false): Boolean {
    if (entry in entries) {
      return false
    }

    if (first) entries.add(0, entry) else entries.add(entry)
    if (!isProvisional) {
      return false
    }

    val active = activeProject()?.takeIf { it in entries } ?: return false
    isProvisional = false
    if (active == selected) {
      return false
    }
    selected = active
    return true
  }

  /**
   * Removes [entry]. When it was the selection, no entry is selected after the call.
   * The owner calls [resolve] then when it shows the tool window.
   *
   * @return `true` when the selection changed
   */
  fun remove(entry: P): Boolean {
    if (!entries.remove(entry) || entry != selected) {
      return false
    }

    selected = null
    isProvisional = false
    return true
  }

  /**
   * Selects [entry] on a request of the user or of the project of [entry]. Such a selection is never provisional.
   *
   * @return `true` when the selection changed
   */
  fun select(entry: P): Boolean {
    require(entry in entries) { "$entry is no entry of the selection" }
    isProvisional = false
    if (entry == selected) {
      return false
    }
    selected = entry
    return true
  }

  /**
   * Gives the selection, and makes one when no entry is selected: the entry of the active project, or else the first entry.
   * The owner calls it when it shows the tool window.
   */
  fun resolve(): P? {
    selected?.let { return it }

    val active = activeProject()?.takeIf { it in entries }
    selected = active ?: entries.firstOrNull()
    isProvisional = active == null && selected != null
    return selected
  }
}

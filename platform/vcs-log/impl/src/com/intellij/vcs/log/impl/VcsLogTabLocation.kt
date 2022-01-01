// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl

import com.intellij.openapi.project.Project
import com.intellij.vcs.log.VcsLogUi
import org.jetbrains.annotations.ApiStatus

/**
 * Represents a location of the [VcsLogUi].
 *
 * Location information is used to create, select, close tabs and to identify visible tabs to refresh them when needed.
 *
 * @see VcsLogManager.createLogUi
 * @see VcsLogTabsWatcher
 * @see VcsLogTabsWatcherExtension
 */
@ApiStatus.Experimental
enum class VcsLogTabLocation {
  TOOL_WINDOW {
    override fun select(project: Project, logUi: VcsLogUi): Boolean = VcsLogContentUtil.selectLogUi(project, logUi)
  },
  EDITOR {
    override fun select(project: Project, logUi: VcsLogUi): Boolean = VcsLogEditorUtil.selectLogUi(project, logUi)
  },
  STANDALONE {
    override fun select(project: Project, logUi: VcsLogUi): Boolean {
      throw UnsupportedOperationException("Selecting standalone log tabs is not supported")
    }
  };

  abstract fun select(project: Project, logUi: VcsLogUi): Boolean

  companion object {
    /**
     * Finds a [VcsLogUi] instance opened in this [VcsLogManager] by specified class and condition, and selects it if needed.
     *
     * @param location location of the tab to find
     * @param clazz subclass of [VcsLogUi] to find instance of
     * @param select true is the fount tab should be selected
     * @param condition condition to match tabs by
     * @return true is the tab was found and selected (if selection was necessary), false otherwise.
     */
    fun <U : VcsLogUi> VcsLogManager.findLogUi(location: VcsLogTabLocation, clazz: Class<U>, select: Boolean, condition: (U) -> Boolean): U? {
      val logUi = getLogUis(location).filterIsInstance(clazz).find(condition)
      if (select && logUi != null) {
        if (!location.select(dataManager.project, logUi)) return null
      }
      return logUi
    }
  }
}
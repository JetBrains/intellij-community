// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl

import com.intellij.openapi.diagnostic.logger
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
enum class VcsLogTabLocation {
  TOOL_WINDOW,

  @ApiStatus.Experimental
  EDITOR,

  @ApiStatus.Experimental
  STANDALONE;

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
      val logUi = getLogUis(location).filterIsInstance(clazz).find(condition) ?: return null
      if (select) {
        val project = dataManager.project
        when (location) {
          TOOL_WINDOW -> {
            VcsLogContentUtil.selectLogUi(project, logUi)
          }
          EDITOR -> {
            VcsLogEditorUtil.selectLogUi(project, logUi)
          }
          else -> {
            logger<VcsLogTabLocation>().error("Selection in $location is not supported")
          }
        }
      }
      return logUi
    }
  }
}
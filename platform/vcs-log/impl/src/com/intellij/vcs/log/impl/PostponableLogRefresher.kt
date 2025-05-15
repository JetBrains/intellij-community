// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl

import com.intellij.ide.PowerSaveMode
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry.Companion.`is`
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.vcs.log.data.DataPackChangeListener
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.ui.VcsLogUiEx
import com.intellij.vcs.log.visible.VisiblePackRefresher
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

private val LOG = logger<PostponableLogRefresher>()

@ApiStatus.Internal
class PostponableLogRefresher internal constructor(private val logData: VcsLogData) {
  private val rootsToRefresh = mutableSetOf<VirtualFile?>()
  private val _logWindows = mutableSetOf<VcsLogWindow>()
  val logWindows: Set<VcsLogWindow> get() = _logWindows
  private val creationTraces = mutableMapOf<String?, Throwable?>()

  init {
    logData.addDataPackChangeListener(DataPackChangeListener { dataPack ->
      LOG.debug("Refreshing log windows " + logWindows)
      for (window in logWindows) {
        window.refresher.setDataPack(window.isVisible(), dataPack)
      }
    })
  }

  fun addLogWindow(window: VcsLogWindow): Disposable {
    val windowId = window.id
    if (logWindows.any { it.id == windowId }) {
      throw CannotAddVcsLogWindowException("Log window with id '" + windowId + "' was already added. " +
                                           "Existing windows:\n" + getLogWindowsInformation(),
                                           creationTraces[windowId])
    }

    _logWindows.add(window)
    creationTraces[windowId] = Throwable("Creation trace for " + window)
    refresherActivated(window.refresher, true)
    return Disposable {
      LOG.debug("Removing disposed log window " + window)
      _logWindows.remove(window)
      creationTraces.remove(windowId)
    }
  }

  private fun canRefreshNow(): Boolean {
    if (keepUpToDate()) return true
    return this.isLogVisible
  }

  val isLogVisible: Boolean
    get() = logWindows.any { it.isVisible() }

  fun refresherActivated(refresher: VisiblePackRefresher, firstTime: Boolean) {
    logData.initialize()

    if (!rootsToRefresh.isEmpty()) {
      refreshPostponedRoots()
    }
    else {
      refresher.setValid(true, firstTime)
    }
  }

  @RequiresEdt
  fun refresh(root: VirtualFile) {
    if (canRefreshNow()) {
      logData.refresh(setOf(root), true)
    }
    else {
      LOG.debug("Postponed refresh for " + root)
      rootsToRefresh.add(root)
    }
  }

  @RequiresEdt
  fun hasPostponedRoots(): Boolean {
    return !rootsToRefresh.isEmpty()
  }

  @RequiresEdt
  fun refreshPostponedRoots() {
    val toRefresh = rootsToRefresh.toSet()
    rootsToRefresh.removeAll(toRefresh) // clear the set, but keep roots which could possibly arrive after collecting them in the var.
    logData.refresh(toRefresh)
  }

  fun getLogWindowsInformation(): String {
    return logWindows.joinToString("\n") { window ->
      val isVisible = if (window.isVisible()) " (visible)" else ""
      val isDisposed = if (Disposer.isDisposed(window.refresher)) " (disposed)" else ""
      window.toString() + isVisible + isDisposed
    }
  }

  open class VcsLogWindow(val ui: VcsLogUiEx) {
    val id: String
      get() = ui.getId()

    val refresher: VisiblePackRefresher
      get() = ui.getRefresher()

    open fun isVisible(): Boolean {
      return true
    }

    override fun toString(): @NonNls String {
      return "VcsLogWindow '" + ui.getId() + "'"
    }
  }

  companion object {
    @JvmStatic
    fun keepUpToDate(): Boolean {
      return `is`("vcs.log.keep.up.to.date") && !PowerSaveMode.isEnabled()
    }
  }
}

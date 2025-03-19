// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.ui.content.custom.options.PersistentContentCustomLayoutOption
import com.intellij.ui.content.custom.options.PersistentContentCustomLayoutOptions
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.frame.XDebugView
import com.intellij.xdebugger.impl.frame.XFramesView
import com.intellij.xdebugger.impl.frame.XThreadsFramesView
import com.intellij.xdebugger.impl.frame.XThreadsView
import org.jetbrains.annotations.ApiStatus.Internal

internal object ThreadsViewConstants {
  const val DEFAULT_THREADS_VIEW_KEY = "Default"
  const val THREADS_TREE_VIEW_KEY = "Threads"
  const val SIDE_BY_SIDE_THREADS_VIEW_KEY = "SideBySide"
  const val FRAMES_ONLY_THREADS_VIEW_KEY = "FramesOnly"
}

@Internal
abstract class FramesAndThreadsLayoutOptionBase(options: XDebugTabLayoutSettings.XDebugFramesAndThreadsLayoutOptions) : PersistentContentCustomLayoutOption(
  options) {
  abstract fun createView(session: XDebugSessionImpl): XDebugView
}

internal class DefaultLayoutOption(options: XDebugTabLayoutSettings.XDebugFramesAndThreadsLayoutOptions) : FramesAndThreadsLayoutOptionBase(
  options) {
  override fun getDisplayName(): String = XDebuggerBundle.message("debug.threads.and.frames.default.layout.option")

  override fun createView(session: XDebugSessionImpl): XFramesView = XFramesView(session)

  override fun getOptionKey(): String = ThreadsViewConstants.DEFAULT_THREADS_VIEW_KEY
}

internal class ThreadsTreeLayoutOption(
  options: XDebugTabLayoutSettings.XDebugFramesAndThreadsLayoutOptions) : FramesAndThreadsLayoutOptionBase(options) {
  override fun getDisplayName(): String = XDebuggerBundle.message("debug.threads.and.frames.threads.tree.layout.option")

  override fun createView(session: XDebugSessionImpl): XThreadsView = XThreadsView(session.project, session)

  override fun getOptionKey(): String = ThreadsViewConstants.THREADS_TREE_VIEW_KEY
}

@Internal
abstract class SideBySideLayoutOptionBase(private val options: XDebugTabLayoutSettings.XDebugFramesAndThreadsLayoutOptions,
                                          private val areThreadsVisible: Boolean) : FramesAndThreadsLayoutOptionBase(options) {

  override fun createView(session: XDebugSessionImpl): XThreadsFramesView = XThreadsFramesView(options.debugTab).apply {
    this.setThreadsVisible(areThreadsVisible)
  }
}

@Internal
class SideBySideLayoutOption(options: XDebugTabLayoutSettings.XDebugFramesAndThreadsLayoutOptions) : SideBySideLayoutOptionBase(options,
                                                                                                                                true) {
  override fun getDisplayName(): String = XDebuggerBundle.message("debug.threads.and.frames.side.by.side.layout.option")

  override fun getOptionKey(): String = ThreadsViewConstants.SIDE_BY_SIDE_THREADS_VIEW_KEY
}

@Internal
class FramesOnlyLayoutOption(options: XDebugTabLayoutSettings.XDebugFramesAndThreadsLayoutOptions) : SideBySideLayoutOptionBase(options,
                                                                                                                                false) {
  override fun getDisplayName(): String = XDebuggerBundle.message("debug.threads.and.frames.frames.only.layout.option")
  override fun getOptionKey(): String = ThreadsViewConstants.FRAMES_ONLY_THREADS_VIEW_KEY
}

internal object XDebugThreadsFramesViewChangeCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  private val GROUP = EventLogGroup("debugger.frames.view", 2)

  private const val UNKNOWN_KEY = "UNKNOWN"

  private val VIEW_IDS = listOf(
    UNKNOWN_KEY,
    PersistentContentCustomLayoutOptions.HIDE_OPTION_KEY,
    ThreadsViewConstants.DEFAULT_THREADS_VIEW_KEY,
    ThreadsViewConstants.THREADS_TREE_VIEW_KEY,
    ThreadsViewConstants.SIDE_BY_SIDE_THREADS_VIEW_KEY,
    ThreadsViewConstants.FRAMES_ONLY_THREADS_VIEW_KEY
  )

  private val VIEW_ID = EventFields.String("view_id", VIEW_IDS)
  private val VIEW_SELECTED = GROUP.registerEvent("selected", VIEW_ID)

  fun framesViewSelected(viewId: String?) {
    val verifiedTabId = if (VIEW_IDS.contains(viewId)) viewId else UNKNOWN_KEY
    VIEW_SELECTED.log(verifiedTabId)
  }
}

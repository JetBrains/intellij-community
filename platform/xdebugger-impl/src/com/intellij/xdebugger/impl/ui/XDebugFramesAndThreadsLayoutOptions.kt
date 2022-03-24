package com.intellij.xdebugger.impl.ui

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.ui.content.custom.options.PersistentContentCustomLayoutOption
import com.intellij.ui.content.custom.options.PersistentContentCustomLayoutOptions
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.impl.frame.XDebugView
import com.intellij.xdebugger.impl.frame.XFramesView
import com.intellij.xdebugger.impl.frame.XThreadsFramesView
import com.intellij.xdebugger.impl.frame.XThreadsView

object ThreadsViewConstants {
  const val DEFAULT_THREADS_VIEW_KEY = "Default"
  const val THREADS_TREE_VIEW_KEY = "Threads"
  const val SIDE_BY_SIDE_THREADS_VIEW_KEY = "SideBySide"
  const val FRAMES_ONLY_THREADS_VIEW_KEY = "FramesOnly"

}

abstract class FramesAndThreadsLayoutOptionBase(options: XDebugTabLayoutSettings.XDebugFramesAndThreadsLayoutOptions) : PersistentContentCustomLayoutOption(options) {
  abstract fun createView(): XDebugView
}

class DefaultLayoutOption(private val options: XDebugTabLayoutSettings.XDebugFramesAndThreadsLayoutOptions) : FramesAndThreadsLayoutOptionBase(options) {
  override fun getDisplayName(): String = XDebuggerBundle.message("debug.threads.and.frames.default.layout.option")

  override fun createView(): XFramesView = XFramesView(options.session.project)

  override fun getOptionKey(): String = ThreadsViewConstants.DEFAULT_THREADS_VIEW_KEY
}

class ThreadsTreeLayoutOption(
  private val options: XDebugTabLayoutSettings.XDebugFramesAndThreadsLayoutOptions) : FramesAndThreadsLayoutOptionBase(options) {
  override fun getDisplayName(): String = XDebuggerBundle.message("debug.threads.and.frames.threads.tree.layout.option")

  override fun createView(): XThreadsView = XThreadsView(options.session.project, options.session)

  override fun getOptionKey(): String = ThreadsViewConstants.THREADS_TREE_VIEW_KEY
}

abstract class SideBySideLayoutOptionBase(private val options: XDebugTabLayoutSettings.XDebugFramesAndThreadsLayoutOptions, private val areThreadsVisible: Boolean) : FramesAndThreadsLayoutOptionBase(options) {

  override fun createView(): XThreadsFramesView = XThreadsFramesView(options.session.project).apply { this.setThreadsVisible(areThreadsVisible) }
}

class SideBySideLayoutOption(options: XDebugTabLayoutSettings.XDebugFramesAndThreadsLayoutOptions) : SideBySideLayoutOptionBase(options, true) {
  override fun getDisplayName(): String = XDebuggerBundle.message("debug.threads.and.frames.side.by.side.layout.option")

  override fun getOptionKey(): String = ThreadsViewConstants.SIDE_BY_SIDE_THREADS_VIEW_KEY
}

class FramesOnlyLayoutOption(options: XDebugTabLayoutSettings.XDebugFramesAndThreadsLayoutOptions) : SideBySideLayoutOptionBase(options, false) {
  override fun getDisplayName(): String = XDebuggerBundle.message("debug.threads.and.frames.frames.only.layout.option")
  override fun getOptionKey(): String = ThreadsViewConstants.FRAMES_ONLY_THREADS_VIEW_KEY
}

class XDebugThreadsFramesViewChangeCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  companion object {
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
}

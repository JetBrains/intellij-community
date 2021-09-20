package com.intellij.xdebugger.impl.ui

import com.intellij.execution.ui.layout.impl.RunnerContentUi
import com.intellij.execution.ui.layout.impl.RunnerLayoutUiImpl
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.content.Content
import com.intellij.ui.content.custom.options.CustomContentLayoutOption
import com.intellij.ui.content.custom.options.PersistentContentCustomLayoutOption
import com.intellij.ui.content.custom.options.PersistentContentCustomLayoutOptions
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.frame.XDebugView
import com.intellij.xdebugger.impl.frame.XFramesView
import com.intellij.xdebugger.impl.frame.XThreadsFramesView
import com.intellij.xdebugger.impl.frame.XThreadsView

class XDebugFramesAndThreadsLayoutOptions(
  val session: XDebugSessionImpl,
  val content: Content,
  val debugTab: XDebugSessionTab3) : PersistentContentCustomLayoutOptions(content, VIEW_KEY) {

  companion object {
    const val DEFAULT_VIEW_KEY = "Default"
    const val THREADS_VIEW_KEY = "Threads"
    const val SIDE_BY_SIDE_VIEW_KEY = "SideBySide"
    const val FRAMES_ONLY_VIEW_KEY = "FramesOnly"

    const val VIEW_KEY = "ThreadsFramesSelectedView"
  }

  private val options = arrayOf<PersistentContentCustomLayoutOption>(
    DefaultLayoutOption(this),
    ThreadsTreeLayoutOption(this),
    SideBySideLayoutOption(this),
    FramesOnlyLayoutOption(this)
  )

  override fun doSelect(option: CustomContentLayoutOption) {
    option as? FramesAndThreadsLayoutOptionBase ?: throw IllegalStateException("Unexpected option type: ${option::class.java}")
    if (!option.isSelected) {
      val newView = option.createView()
      debugTab.registerThreadsView(session, content, newView)
      XDebugThreadsFramesViewChangeCollector.framesViewSelected(option.getOptionKey())
    }

    val contentUi = RunnerContentUi.KEY.getData(debugTab.ui as RunnerLayoutUiImpl)
    if (contentUi != null && !isContentVisible()) {
      contentUi.restore(content)
      contentUi.select(content, true)
    }
  }

  override fun getDefaultOptionKey(): String = Registry.stringValue("debugger.default.selected.view.key")

  override fun getAvailableOptions() = options

  override fun onHide() {
    super.onHide()
    XDebugThreadsFramesViewChangeCollector.framesViewSelected(HIDE_OPTION_KEY)
  }
}


abstract class FramesAndThreadsLayoutOptionBase(options: XDebugFramesAndThreadsLayoutOptions) : PersistentContentCustomLayoutOption(options) {
  abstract fun createView(): XDebugView
}

class DefaultLayoutOption(private val options: XDebugFramesAndThreadsLayoutOptions) : FramesAndThreadsLayoutOptionBase(options) {
  override fun getDisplayName(): String = XDebuggerBundle.message("debug.threads.and.frames.default.layout.option")

  override fun createView(): XFramesView = XFramesView(options.session.project)

  override fun isThisOptionSelected(): Boolean = options.debugTab.threadFramesView is XFramesView

  override fun getOptionKey(): String = XDebugFramesAndThreadsLayoutOptions.DEFAULT_VIEW_KEY
}

class ThreadsTreeLayoutOption(
  private val options: XDebugFramesAndThreadsLayoutOptions) : FramesAndThreadsLayoutOptionBase(options) {
  override fun getDisplayName(): String = XDebuggerBundle.message("debug.threads.and.frames.threads.tree.layout.option")

  override fun createView(): XThreadsView = XThreadsView(options.session.project, options.session)

  override fun isThisOptionSelected(): Boolean = options.debugTab.threadFramesView is XThreadsView

  override fun getOptionKey(): String = XDebugFramesAndThreadsLayoutOptions.THREADS_VIEW_KEY
}

abstract class SideBySideLayoutOptionBase(private val options: XDebugFramesAndThreadsLayoutOptions, private val areThreadsVisible: Boolean) : FramesAndThreadsLayoutOptionBase(options) {

  override fun createView(): XThreadsFramesView = XThreadsFramesView(options.session.project).apply { this.setThreadsVisible(areThreadsVisible) }

  override fun isThisOptionSelected(): Boolean {
    val view = options.debugTab.threadFramesView
    if (view !is XThreadsFramesView) return false
    return view.isThreadsViewVisible() == areThreadsVisible
  }
}

class SideBySideLayoutOption(options: XDebugFramesAndThreadsLayoutOptions) : SideBySideLayoutOptionBase(options, true) {
  override fun getDisplayName(): String = XDebuggerBundle.message("debug.threads.and.frames.side.by.side.layout.option")

  override fun getOptionKey(): String = XDebugFramesAndThreadsLayoutOptions.SIDE_BY_SIDE_VIEW_KEY
}

class FramesOnlyLayoutOption(options: XDebugFramesAndThreadsLayoutOptions) : SideBySideLayoutOptionBase(options, false) {
  override fun getDisplayName(): String = XDebuggerBundle.message("debug.threads.and.frames.frames.only.layout.option")
  override fun getOptionKey(): String = XDebugFramesAndThreadsLayoutOptions.FRAMES_ONLY_VIEW_KEY
}

class XDebugThreadsFramesViewChangeCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  companion object {
    private val GROUP = EventLogGroup("debugger.frames.view", 2)

    private const val UNKNOWN_KEY = "UNKNOWN"

    private val VIEW_IDS = listOf(
      UNKNOWN_KEY,
      PersistentContentCustomLayoutOptions.HIDE_OPTION_KEY,
      XDebugFramesAndThreadsLayoutOptions.DEFAULT_VIEW_KEY,
      XDebugFramesAndThreadsLayoutOptions.THREADS_VIEW_KEY,
      XDebugFramesAndThreadsLayoutOptions.SIDE_BY_SIDE_VIEW_KEY,
      XDebugFramesAndThreadsLayoutOptions.FRAMES_ONLY_VIEW_KEY
    )

    private val VIEW_ID = EventFields.String("view_id", VIEW_IDS)
    private val VIEW_SELECTED = GROUP.registerEvent("selected", VIEW_ID)

    fun framesViewSelected(viewId: String?) {
      val verifiedTabId = if (VIEW_IDS.contains(viewId)) viewId else UNKNOWN_KEY
      VIEW_SELECTED.log(verifiedTabId)
    }
  }
}
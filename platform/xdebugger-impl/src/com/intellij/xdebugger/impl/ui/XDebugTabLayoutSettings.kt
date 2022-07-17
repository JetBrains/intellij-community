package com.intellij.xdebugger.impl.ui

import com.intellij.debugger.ui.DebuggerContentInfo
import com.intellij.execution.ui.layout.actions.CustomContentLayoutSettings
import com.intellij.execution.ui.layout.actions.RestoreViewAction
import com.intellij.execution.ui.layout.actions.ViewLayoutModeActionGroup
import com.intellij.execution.ui.layout.impl.RunnerContentUi
import com.intellij.execution.ui.layout.impl.RunnerLayoutUiImpl
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.content.Content
import com.intellij.ui.content.custom.options.ContentLayoutStateSettings
import com.intellij.ui.content.custom.options.CustomContentLayoutOption
import com.intellij.ui.content.custom.options.PersistentContentCustomLayoutOption
import com.intellij.ui.content.custom.options.PersistentContentCustomLayoutOptions
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.frame.XDebugView
import com.intellij.xdebugger.impl.frame.XVariablesView

class XDebugTabLayoutSettings(
  session: XDebugSessionImpl,
  private val content: Content,
  private val debugTab: XDebugSessionTab3) : CustomContentLayoutSettings {

  companion object {
    const val THREADS_VIEW_SETTINGS_KEY = "ThreadsFramesSelectedView"

    private const val VARIABLES_VIEW_SETTINGS_KEY = "VariablesViewMinimized"

    fun isVariablesViewVisible(): Boolean {
      return !PropertiesComponent.getInstance().getBoolean(VARIABLES_VIEW_SETTINGS_KEY)
    }
  }

  val threadsAndFramesOptions = XDebugFramesAndThreadsLayoutOptions(session, content, debugTab)
  val variablesLayoutSettings = XDebugVariablesLayoutSettings(content, debugTab)

  private var myContentUI: RunnerContentUi? = null

  override fun getActions(runnerContentUi: RunnerContentUi): MutableList<AnAction> {
    myContentUI = runnerContentUi
    return mutableListOf(
      ViewLayoutModeActionGroup(content, threadsAndFramesOptions),
      RestoreViewAction(content, variablesLayoutSettings)
    )
  }

  override fun restore() {
    threadsAndFramesOptions.restore()
    variablesLayoutSettings.restore()
  }

  private fun onThreadsSettingsChanged(isHidden: Boolean) {
    if (isHidden) {
      if (!variablesLayoutSettings.isSelected) {
        hideContent()
      }
    }
    else {
      enableTabIfNeeded()
    }
  }

  private fun onVariablesSettingsChanged(visible: Boolean) {
    if (visible) {
      enableTabIfNeeded()
    }
    else if (threadsAndFramesOptions.isHidden) {
      hideContent()
    }
  }

  private fun enableTabIfNeeded() {
    val contentUi = RunnerContentUi.KEY.getData(debugTab.ui as RunnerLayoutUiImpl)
    if (contentUi != null && !threadsAndFramesOptions.isContentVisible()) {
      contentUi.restore(content)
      contentUi.select(content, true)
    }
  }

  private fun hideContent() {
    val uiImpl = debugTab.ui as? RunnerLayoutUiImpl ?: return
    val runnerContentUi = RunnerContentUi.KEY.getData(uiImpl) ?: return
    runnerContentUi.minimize(content, null)
  }

  inner class XDebugFramesAndThreadsLayoutOptions(
    val session: XDebugSessionImpl,
    val content: Content,
    val debugTab: XDebugSessionTab3) : PersistentContentCustomLayoutOptions(content, THREADS_VIEW_SETTINGS_KEY) {

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
        debugTab.getView(DebuggerContentInfo.FRAME_CONTENT, XDebugView::class.java)?.mainComponent?.isVisible = true
      }

      onThreadsSettingsChanged(false)
    }

    override fun getDefaultOptionKey(): String = Registry.stringValue("debugger.default.selected.view.key")

    override fun getAvailableOptions() = options

    override fun onHide() {
      super.onHide()
      XDebugThreadsFramesViewChangeCollector.framesViewSelected(HIDE_OPTION_KEY)
      debugTab.getView(DebuggerContentInfo.FRAME_CONTENT, XDebugView::class.java)?.mainComponent?.isVisible = false

      onThreadsSettingsChanged(true)
    }

    override fun getDisplayName(): String = XDebuggerBundle.message("xdebugger.threads.tab.layout.settings.title")

    override fun isHideOptionVisible(): Boolean {
      if (super.isHideOptionVisible()) {
        return true
      }

      return variablesLayoutSettings.isSelected
    }
  }

  inner class XDebugVariablesLayoutSettings(
    val content: Content,
    val debugTab: XDebugSessionTab3
  ) : ContentLayoutStateSettings {

    override fun isSelected(): Boolean = debugTab.getView(DebuggerContentInfo.VARIABLES_CONTENT,
                                                          XVariablesView::class.java)?.mainComponent?.isVisible ?: false

    override fun setSelected(state: Boolean) {
      debugTab.getView(DebuggerContentInfo.VARIABLES_CONTENT, XVariablesView::class.java)?.mainComponent?.isVisible = state
      debugTab.getView(DebuggerContentInfo.WATCHES_CONTENT, XVariablesView::class.java)?.mainComponent?.isVisible = state
      PropertiesComponent.getInstance().setValue(VARIABLES_VIEW_SETTINGS_KEY, !state)

      onVariablesSettingsChanged(state)
    }

    override fun getDisplayName(): String = XDebuggerBundle.message("debugger.session.tab.variables.title")
    override fun restore() {
      isSelected = true
    }

    override fun isEnabled(): Boolean {
      return !isSelected || (content.manager?.contents?.size ?: 0) > 1 || !threadsAndFramesOptions.isHidden
    }
  }
}
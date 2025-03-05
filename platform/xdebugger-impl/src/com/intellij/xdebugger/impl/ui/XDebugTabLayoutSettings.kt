// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.xdebugger.impl.frame.XDebugView
import com.intellij.xdebugger.impl.frame.XVariablesView
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class XDebugTabLayoutSettings(
  private val content: Content,
  private val debugTab: XDebugSessionTab3) : CustomContentLayoutSettings {

  companion object {
    const val THREADS_VIEW_SETTINGS_KEY: String = "ThreadsFramesSelectedView"

    private const val VARIABLES_VIEW_SETTINGS_KEY = "VariablesViewMinimized"

    fun isVariablesViewVisible(): Boolean {
      return !PropertiesComponent.getInstance().getBoolean(VARIABLES_VIEW_SETTINGS_KEY)
    }
  }

  val threadsAndFramesOptions: XDebugFramesAndThreadsLayoutOptions = XDebugFramesAndThreadsLayoutOptions(content, debugTab)
  val variablesLayoutSettings: XDebugVariablesLayoutSettings = XDebugVariablesLayoutSettings(content)

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
    val ui = debugTab.ui as? RunnerLayoutUiImpl ?: return
    val contentUi = ui.contentUI
    if (!threadsAndFramesOptions.isContentVisible()) {
      contentUi.restore(content)
      contentUi.select(content, true)
    }
  }

  private fun hideContent() {
    val ui = debugTab.ui as? RunnerLayoutUiImpl ?: return
    val contentUi = ui.contentUI
    contentUi.minimize(content, null)
  }

  inner class XDebugFramesAndThreadsLayoutOptions(val content: Content,
                                                  val debugTab: XDebugSessionTab3
  ) : PersistentContentCustomLayoutOptions(content, THREADS_VIEW_SETTINGS_KEY) {

    private val options = arrayOf<PersistentContentCustomLayoutOption>(
      DefaultLayoutOption(this),
      ThreadsTreeLayoutOption(this),
      SideBySideLayoutOption(this),
      FramesOnlyLayoutOption(this)
    )

    override fun doSelect(option: CustomContentLayoutOption) {
      option as? FramesAndThreadsLayoutOptionBase ?: throw IllegalStateException("Unexpected option type: ${option::class.java}")
      if (!option.isSelected) {
        debugTab.mySession?.let {
          //TODO [chernyaev] passing session here make it impossible to update presentation of a tab that does not have a running debug session
          val newView = option.createView(it)
          debugTab.registerThreadsView(content, newView)
        }
        XDebugThreadsFramesViewChangeCollector.framesViewSelected(option.getOptionKey())
        debugTab.getView(DebuggerContentInfo.FRAME_CONTENT, XDebugView::class.java)?.mainComponent?.isVisible = true
      }

      onThreadsSettingsChanged(false)
    }

    override fun getDefaultOptionKey(): String =
      (debugTab.mySession?.debugProcess as? XDebugSessionTabCustomizer)?.getDefaultFramesViewKey()
      ?: Registry.stringValue("debugger.default.selected.view.key")

    override fun getAvailableOptions(): Array<PersistentContentCustomLayoutOption> = options

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

  inner class XDebugVariablesLayoutSettings(val content: Content) : ContentLayoutStateSettings {

    override fun isSelected(): Boolean = debugTab.getView(DebuggerContentInfo.VARIABLES_CONTENT,
                                                          XVariablesView::class.java)?.mainComponent?.isVisible == true

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
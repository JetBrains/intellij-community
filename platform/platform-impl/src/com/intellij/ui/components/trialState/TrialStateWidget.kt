// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components.trialState

import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.Disposer
import com.intellij.ui.GotItTooltip
import com.intellij.ui.components.trialState.TrialStateService.TrialState
import com.intellij.ui.dsl.gridLayout.GridLayout
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.ui.dsl.gridLayout.builders.RowsGridBuilder
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.launchOnShow
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.math.min

internal class TrialStateWidget : DumbAwareAction(), CustomComponentAction {

  class TrialStateWidgetUnregister : AppLifecycleListener {
    override fun appStarted() {
      if (!TrialStateService.isEnabled()) {
        ActionManagerEx.getInstanceEx().unregisterAction("TrialStateWidget")
      }
    }
  }

  private var tooltip: GotItTooltip? = null

  override fun actionPerformed(e: AnActionEvent) {
    TrialStateService.getInstance().setLastShownColorStateClicked()
    TrialStateUtils.openTrailStateTab()
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.place == ActionPlaces.MAIN_TOOLBAR &&
                                         TrialStateService.isEnabled() &&
                                         TrialStateService.isApplicable() &&
                                         TrialStateService.getInstance().state.value != null
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    val result = TrialStateButtonWrapper()

    result.launchOnShow("TrialStateButton") {
      TrialStateService.getInstance().state.collect { state ->
        updateButton(result)

        if (state?.trialStateChanged == true) {
          showUpdatedStateNotification(result, state)
        }
      }
    }

    result.button.addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent?) {
        if (e != null && SwingUtilities.isLeftMouseButton(e)) {
          ActionManager.getInstance().tryToExecute(this@TrialStateWidget, e, null, place, false)
        }
      }
    })

    return result
  }

  override fun updateCustomComponent(component: JComponent, presentation: Presentation) {
    updateButton(component as TrialStateButtonWrapper)
  }

  private fun showUpdatedStateNotification(wrapper: TrialStateButtonWrapper, state: TrialStateService.State) {
    if (!wrapper.isShowing()) {
      return
    }

    disposeTooltip()

    if (state.trialState == TrialState.GRACE_ENDED) {
      TrialStateUtils.showTrialEndedDialog()

      return
    }

    tooltip = state.getGotItTooltip()
    tooltip?.show(wrapper.button) { it, _ ->
      val width = min(it.width, (it as JComponent).visibleRect.width)
      Point(width - JBUIScale.scale(20), it.height)
    }
  }

  private fun disposeTooltip() {
    tooltip?.let {
      Disposer.dispose(it)
    }
    tooltip = null
  }

  private fun updateButton(wrapper: TrialStateButtonWrapper) {
    val state = TrialStateService.getInstance().state.value ?: return

    with(wrapper.button) {
      setColorState(state.colorState)
      text = state.getButtonText()
    }
  }
}

/**
 * Prevent button vertical stretching
 */
private class TrialStateButtonWrapper : JPanel(GridLayout()) {

  val button = TrialStateButton()

  init {
    isOpaque = false

    RowsGridBuilder(this)
      .resizableRow()
      .cell(button, verticalAlign = VerticalAlign.CENTER)
  }
}

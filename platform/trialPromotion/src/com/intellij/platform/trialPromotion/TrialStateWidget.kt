// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.trialPromotion

import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.trialPromotion.TrialStateService.TrialState
import com.intellij.ui.GotItTooltip
import com.intellij.ui.dsl.gridLayout.GridLayout
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.ui.dsl.gridLayout.builders.RowsGridBuilder
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.launchOnShow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.math.min

internal class TrialStateWidget : DumbAwareAction(), CustomComponentAction {
  companion object {
    private val logger = logger<TrialStateWidget>()
  }

  class TrialStateWidgetUnregister : AppLifecycleListener {
    override fun appStarted() {
      if (!TrialStateService.isEnabled()) {
        ActionManagerEx.getInstanceEx().unregisterAction("TrialStateWidget")
      }
    }
  }

  private var tooltip: GotItTooltip? = null

  private val contentsAsync by lazy {
    appScope.async {
      TrialTabContent.getContentMap()
    }
  }

  private suspend fun openTrialEditorTab(project: Project, dataContext: DataContext?) {
    val contentMap = contentsAsync.await()
    val state = TrialStateService.getInstance().state.value
    if (state == null) {
      logger.warn("Trial editor tab: can't be shown. Trial state is not available")
      return
    }

    val contentPageKind = TrialPageKind.fromTrialState(state.trialState)
    val content = contentMap?.get(contentPageKind)
    if (content != null && content.isAvailable()) {
      content.show(project, dataContext, state.getProgressData())
    }
    else {
      logger.warn("Trial editor tab: can't be shown. Content is not available")
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    TrialStateWidgetUsageCollector.WIDGET_CLICKED.log()
    val project = e.project
    if (project != null) {
      project.getScope().launch {
        TrialStateService.getInstance().setLastShownColorStateClicked()
        openTrialEditorTab(project, e.dataContext)
      }
    }
    else {
      logger.warn("Cannot open trial editor tab because the project is null")
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

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

@Service(Service.Level.PROJECT)
private class ScopeProvider(val scope: CoroutineScope)

private fun Project.getScope() = this.service<ScopeProvider>().scope

@Service(Service.Level.APP)
private class AppScopeProvider(val scope: CoroutineScope)

private val appScope: CoroutineScope get() = service<AppScopeProvider>().scope

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

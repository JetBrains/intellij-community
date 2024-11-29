// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.hotswap

import com.intellij.icons.AllIcons
import com.intellij.ide.HelpTooltip
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.toolbar.floating.AbstractFloatingToolbarComponent
import com.intellij.openapi.editor.toolbar.floating.FloatingToolbarComponent
import com.intellij.openapi.editor.toolbar.floating.FloatingToolbarProvider
import com.intellij.openapi.editor.toolbar.floating.isInsideMainEditor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.PopupHandler
import com.intellij.util.cancelOnDispose
import com.intellij.util.ui.JBUI
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.impl.hotswap.HotSwapStatistics
import com.intellij.xdebugger.impl.hotswap.HotSwapUiExtension
import com.intellij.xdebugger.impl.hotswap.HotSwapVisibleStatus
import com.intellij.xdebugger.impl.rpc.XDebugHotSwapCurrentSessionStatus
import icons.PlatformDebuggerImplIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Insets
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants

private val hotSwapIcon: Icon by lazy {
  HotSwapUiExtension.computeSafeIfAvailable { it.hotSwapIcon } ?: PlatformDebuggerImplIcons.Actions.DebuggerSync
}

private val addHideAction: Boolean by lazy {
  HotSwapUiExtension.computeSafeIfAvailable { it.shouldAddHideButton } != false
}

@Suppress("DialogTitleCapitalization")
private fun createHelpTooltip(): HelpTooltip =
  HotSwapUiExtension.computeSafeIfAvailable { it.createTooltip() }
  ?: HelpTooltip()
    .setTitle(XDebuggerBundle.message("xdebugger.hotswap.tooltip.apply"))
    .setDescription(XDebuggerBundle.message("xdebugger.hotswap.tooltip.description"))

private fun showFloatingToolbar(): Boolean = HotSwapUiExtension.computeSafeIfAvailable { it.showFloatingToolbar() } != false

private fun collectPopupMenuActions(): DefaultActionGroup? = HotSwapUiExtension.computeSafeIfAvailable { it.popupMenuActions() }

internal class HotSwapModifiedFilesAction : AnAction(), DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val status = getCurrentStatus(project) ?: return
    if (!status.hasChanges) return
    FrontendHotSwapManager.getInstance(project).performHotSwap(status.sessionId, HotSwapStatistics.HotSwapSource.RELOAD_MODIFIED_ACTION)
  }

  override fun update(e: AnActionEvent) {
    val project = e.project ?: return
    val status = getCurrentStatus(project)
    e.presentation.isEnabled = status?.hasChanges == true
    e.presentation.isVisible = Registry.`is`("debugger.hotswap.floating.toolbar")
    if (e.place != ActionPlaces.MAIN_MENU) {
      e.presentation.isVisible = e.presentation.isVisible && status != null
    }
    e.presentation.icon = hotSwapIcon
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT
}

private fun getCurrentStatus(project: Project) = FrontendHotSwapManager.getInstance(project).currentStatus

private enum class HotSwapButtonStatus {
  READY, IN_PROGRESS, SUCCESS
}

private class HotSwapWithRebuildAction : AnAction(), CustomComponentAction, DumbAware {
  var status = HotSwapButtonStatus.READY

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val status = getCurrentStatus(project) ?: return
    FrontendHotSwapManager.getInstance(project).performHotSwap(status.sessionId, HotSwapStatistics.HotSwapSource.RELOAD_MODIFIED_BUTTON)
  }

  override fun getActionUpdateThread() = ActionUpdateThread.EDT

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return HotSwapToolbarComponent(this, presentation, place)
  }

  override fun update(e: AnActionEvent) {
    val project = e.project ?: return
    if (e.presentation.isEnabledAndVisible) {
      updateToolbarVisibility(project)
    }
  }

  override fun updateCustomComponent(component: JComponent, presentation: Presentation) {
    (component as HotSwapToolbarComponent).update(status, presentation)
  }
}

private class HotSwapToolbarComponent(action: AnAction, presentation: Presentation, place: String)
  : JPanel(BorderLayout(0, 0)) {

  private val tooltip = createHelpTooltip()
    .setShortcut(ActionManager.getInstance().getKeyboardShortcut("XDebugger.Hotswap.Modified.Files"))
  val button = object : ActionButtonWithText(action, presentation, place, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE) {
    override fun getMargins(): Insets = JBUI.insets(4, 6)
    override fun iconTextSpace(): Int = JBUI.scale(4)
  }.apply {
    setHorizontalTextPosition(SwingConstants.LEADING)
    tooltip.installOn(this)
    installPopupMenu()
  }

  init {
    isOpaque = false
    add(button, BorderLayout.CENTER)
    tooltip.installOn(this)
    border = JBUI.Borders.emptyLeft(JBUI.scale(2))
  }

  fun update(status: HotSwapButtonStatus, presentation: Presentation) {
    presentation.isEnabled = status == HotSwapButtonStatus.READY
    val icon = when (status) {
      HotSwapButtonStatus.READY -> hotSwapIcon
      HotSwapButtonStatus.IN_PROGRESS -> AnimatedIcon.Default.INSTANCE
      HotSwapButtonStatus.SUCCESS -> AllIcons.Status.Success
    }
    presentation.icon = icon
    presentation.disabledIcon = icon
    @Suppress("DialogTitleCapitalization")
    presentation.text = XDebuggerBundle.message("xdebugger.hotswap.code.changed")
  }

}

private fun updateToolbarVisibility(project: Project) {
  val manager = FrontendHotSwapManager.getInstance(project)
  manager.coroutineScope.launch(Dispatchers.Default) {
    // Hide toolbar after setting disable
    if (showFloatingToolbar()) return@launch
    manager.notifyHidden()
  }
}

private fun JComponent.installPopupMenu() {
  addMouseListener(object : PopupHandler() {
    override fun invokePopup(comp: Component?, x: Int, y: Int) {
      val actions = collectPopupMenuActions() ?: return
      val popupMenu = ActionManager.getInstance().createActionPopupMenu("HotSwapToolbarPopup", actions)
      popupMenu.component.show(comp, x, y)
    }
  })
}

private val logger = logger<HotSwapFloatingToolbarProvider>()

internal class HotSwapFloatingToolbarProvider : FloatingToolbarProvider {
  override val autoHideable: Boolean get() = false
  private val hotSwapAction by lazy { HotSwapWithRebuildAction() }

  override val actionGroup: ActionGroup by lazy {
    val group = DefaultActionGroup(hotSwapAction)
    if (addHideAction) {
      group.add(HideAction())
    }
    group
  }

  override fun isApplicable(dataContext: DataContext): Boolean =
    isInsideMainEditor(dataContext)
    && dataContext.getData(CommonDataKeys.EDITOR)?.editorKind == EditorKind.MAIN_EDITOR

  override fun register(dataContext: DataContext, component: FloatingToolbarComponent, parentDisposable: Disposable) {
    val project = dataContext.getData(CommonDataKeys.PROJECT) ?: return

    if (component is AbstractFloatingToolbarComponent) {
      component.backgroundAlpha = 0.9f
      component.showingTime = SHOWING_TIME_MS
      component.hidingTime = HIDING_TIME_MS
    }
    if (component is JComponent) {
      component.installPopupMenu()
    }
    val manager = FrontendHotSwapManager.getInstance(project)
    manager.coroutineScope.launch {
      coroutineContext.job.cancelOnDispose(parentDisposable, disposeOnCompletion = false)
      manager.currentStatusFlow.collectLatest { status ->
        onStatusChanged(component, status?.status)
      }
    }
  }

  suspend fun onStatusChanged(component: FloatingToolbarComponent, status: HotSwapVisibleStatus?) = withContext(Dispatchers.EDT) {
    fun updateActions() {
      if (component is ActionToolbarImpl) {
        component.updateActionsAsync()
      }
    }

    if (!showFloatingToolbar()) {
      if (logger.isDebugEnabled) {
        logger.debug("Hide button because it is disabled")
      }
      component.scheduleHide()
      return@withContext
    }

    if (logger.isDebugEnabled) {
      logger.debug("Button status changed: $status")
    }
    when (status) {
      HotSwapVisibleStatus.IN_PROGRESS -> {
        hotSwapAction.status = HotSwapButtonStatus.IN_PROGRESS
        updateActions()
      }
      HotSwapVisibleStatus.SUCCESS -> {
        hotSwapAction.status = HotSwapButtonStatus.SUCCESS
        updateActions()
      }
      HotSwapVisibleStatus.NO_CHANGES -> {
        component.scheduleHide()
      }
      HotSwapVisibleStatus.CHANGES_READY -> {
        hotSwapAction.status = HotSwapButtonStatus.READY
        updateActions()
        component.scheduleShow()
      }
      HotSwapVisibleStatus.SESSION_COMPLETED, HotSwapVisibleStatus.HIDDEN, null -> {
        component.hideImmediately()
      }

    }
  }

  companion object {
    private const val SHOWING_TIME_MS = 500
    private const val HIDING_TIME_MS = 500
  }
}

private class HideAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    FrontendHotSwapManager.getInstance(project).notifyHidden()
  }

  override fun getActionUpdateThread() = ActionUpdateThread.EDT
  override fun update(e: AnActionEvent) {
    e.presentation.icon = AllIcons.Actions.Close
    e.presentation.hoveredIcon = AllIcons.Actions.CloseHovered
    e.presentation.text = XDebuggerBundle.message("action.hotswap.hide.text")
  }
}

private val XDebugHotSwapCurrentSessionStatus.hasChanges get() = status == HotSwapVisibleStatus.CHANGES_READY

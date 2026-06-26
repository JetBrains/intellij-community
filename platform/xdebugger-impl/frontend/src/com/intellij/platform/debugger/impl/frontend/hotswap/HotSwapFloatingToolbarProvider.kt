// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.hotswap

import com.intellij.icons.AllIcons
import com.intellij.ide.HelpTooltip
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.toolbar.floating.FloatingToolbarComponent
import com.intellij.openapi.editor.toolbar.floating.FloatingToolbarProvider
import com.intellij.openapi.editor.toolbar.floating.isInsideMainEditor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.debugger.impl.rpc.HotSwapSource
import com.intellij.platform.debugger.impl.rpc.HotSwapVisibleStatus
import com.intellij.platform.debugger.impl.rpc.XDebugHotSwapCurrentSessionStatus
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.PopupHandler
import com.intellij.util.ui.EDT
import com.intellij.util.ui.JBUI
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.impl.hotswap.HotSwapUiExtension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Insets
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants

private val hotSwapIcon: Icon by lazy {
  HotSwapUiExtension.computeSafeIfAvailable { it.hotSwapIcon } ?: AllIcons.Debugger.DebuggerSync
}

private val addHideAction: Boolean by lazy {
  HotSwapUiExtension.computeSafeIfAvailable { it.shouldAddHideButton } != false
}

private val addText: Boolean by lazy {
  HotSwapUiExtension.computeSafeIfAvailable { it.shouldAddText } != false
}

private fun showFloatingToolbar(project: Project): Boolean = HotSwapUiExtension.computeSafeIfAvailable { it.showFloatingToolbar(project) } != false

private fun collectPopupMenuActions(): DefaultActionGroup? = HotSwapUiExtension.computeSafeIfAvailable { it.popupMenuActions() }

internal class HotSwapModifiedFilesAction : AnAction(), DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val status = getCurrentStatus(project) ?: return
    if (!status.hasChanges) return
    // The user invokes this action explicitly, without an IDE-side HotSwap suggestion, so it only performs HotSwap.
    FrontendHotSwapManager.getInstance(project).performHotSwap(status.sessionId, HotSwapSource.RELOAD_MODIFIED_ACTION)
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

internal class HotSwapWithRebuildAction : AnAction(), CustomComponentAction, DumbAware {
  var status: HotSwapVisibleStatus = HotSwapVisibleStatus.NoChanges

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val status = getCurrentStatus(project) ?: return
    if (!status.hasChanges) return
    if (status.status is HotSwapVisibleStatus.ChangesNotHotSwappable) {
      FrontendHotSwapManager.getInstance(project).performRestart(status.sessionId)
    }
    else {
      FrontendHotSwapManager.getInstance(project).performHotSwap(status.sessionId, HotSwapSource.RELOAD_MODIFIED_BUTTON)
    }
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

  private val tooltip = HelpTooltip()
  private val button = object : ActionButtonWithText(action, presentation, place, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE) {
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

  fun update(status: HotSwapVisibleStatus, presentation: Presentation) {
    val changesNotHotSwappable = status is HotSwapVisibleStatus.ChangesNotHotSwappable
    val disableHotSwapButton = changesNotHotSwappable && Registry.`is`("debugger.hotswap.disable.button.for.incompatible.changes")
    presentation.isEnabled = status.hasChanges && !disableHotSwapButton
    val icon = when (status) {
      HotSwapVisibleStatus.ChangesReady -> hotSwapIcon
      HotSwapVisibleStatus.InProgress -> AnimatedIcon.Default.INSTANCE
      HotSwapVisibleStatus.Success -> AllIcons.Status.Success
      is HotSwapVisibleStatus.ChangesNotHotSwappable -> if (disableHotSwapButton) AllIcons.General.BalloonWarning else AllIcons.Actions.RestartDebugger
      else -> null
    }
    if (icon != null) {
      presentation.icon = icon
      presentation.disabledIcon = icon
    }
    if (addText) {
      @Suppress("DialogTitleCapitalization")
      presentation.text = XDebuggerBundle.message("xdebugger.hotswap.code.changed")
    }
    val shortcut = ActionManager.getInstance().getKeyboardShortcut("XDebugger.Hotswap.Modified.Files")
      .takeIf { !changesNotHotSwappable || disableHotSwapButton }
    tooltip.setShortcut(shortcut)
    HotSwapUiExtension.computeSafeIfAvailable { it.configureTooltip(tooltip, status) }
    button.accessibleContext.accessibleName = HotSwapUiExtension.computeSafeIfAvailable { it.hotSwapButtonAccessibleName(status) }
  }

}

private fun updateToolbarVisibility(project: Project) {
  val manager = FrontendHotSwapManager.getInstance(project)
  manager.coroutineScope.launch(Dispatchers.Default) {
    // Hide toolbar after setting disable
    if (showFloatingToolbar(project)) return@launch
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

private const val SHOWING_TIME_MS = 500
private const val HIDING_TIME_MS = 500

internal class HotSwapFloatingToolbarProvider : FloatingToolbarProvider {

  override val backgroundAlpha: Float = JBUI.CurrentTheme.FloatingToolbar.TRANSLUCENT_BACKGROUND_ALPHA

  override val showingTime: Int = SHOWING_TIME_MS

  override val hidingTime: Int = HIDING_TIME_MS

  override val autoHideable: Boolean = false

  private val hotSwapAction by lazy { HotSwapWithRebuildAction() }

  override val actionGroup: ActionGroup by lazy {
    val group = DefaultActionGroup(hotSwapAction)
    HotSwapUiExtension.computeSafeIfAvailable { it.moreAction() }?.let {
      group.add(it)
    }
    if (addHideAction) {
      group.add(HideAction())
    }
    group
  }

  override suspend fun isApplicableAsync(dataContext: DataContext): Boolean =
    isInsideMainEditor(dataContext)
    && dataContext.getData(CommonDataKeys.EDITOR)?.editorKind == EditorKind.MAIN_EDITOR

  override fun register(dataContext: DataContext, component: FloatingToolbarComponent, parentDisposable: Disposable) {
    val project = dataContext.getData(CommonDataKeys.PROJECT) ?: return
    val editorTag = dataContext.editorTag
    if (component is JComponent) {
      component.installPopupMenu()
      component.accessibleContext.accessibleName = HotSwapUiExtension.computeSafeIfAvailable { it.toolbarAccessibleName }
    }
    val manager = FrontendHotSwapManager.getInstance(project)
    val job = manager.coroutineScope.launch(Dispatchers.EDT) {
      manager.currentStatusFlow.collectLatest { state ->
        val status = state?.status ?: HotSwapVisibleStatus.Hidden
        onStatusChanged(component, status, editorTag, project)
      }
    }
    Disposer.register(parentDisposable, Disposable {
      logger.debug { "Floating toolbar disposed ($editorTag)" }
      job.cancel("disposed")
    })
  }

  private fun onStatusChanged(
    component: FloatingToolbarComponent,
    status: HotSwapVisibleStatus,
    editorTag: String?,
    project: Project,
  ) {
    fun updateActions() = (component as? ActionToolbarImpl)?.updateActionsAsync()

    EDT.assertIsEdt()

    if (!showFloatingToolbar(project)) {
      logger.debug { "Hide button because it is disabled ($editorTag)" }
      component.scheduleHide()
      return
    }

    logger.debug { "Button status changed: $status ($editorTag)" }
    HotSwapUiExtension.computeSafeIfAvailable { it.announceHotSwapStatus(project, status) }
    hotSwapAction.status = status
    when (status) {
      HotSwapVisibleStatus.InProgress, HotSwapVisibleStatus.Success -> {
        updateActions()
      }
      HotSwapVisibleStatus.NoChanges -> {
        component.scheduleHide()
      }
      HotSwapVisibleStatus.ChangesReady, is HotSwapVisibleStatus.ChangesNotHotSwappable -> {
        updateActions()
        component.scheduleShow()
      }
      HotSwapVisibleStatus.Hidden -> {
        component.hideImmediately()
      }
    }
  }

  override fun onHiddenByEsc(dataContext: DataContext) {
    val project = dataContext.getData(CommonDataKeys.PROJECT) ?: return
    logger.debug { "Button is hidden by Esc button: ${dataContext.editorTag}" }
    FrontendHotSwapManager.getInstance(project).notifyHidden()
  }
}

internal class HideAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    logger.debug { "Button is hidden by user: ${e.dataContext.editorTag}" }
    FrontendHotSwapManager.getInstance(project).notifyHidden()
  }

  override fun getActionUpdateThread() = ActionUpdateThread.EDT
  override fun update(e: AnActionEvent) {
    e.presentation.icon = AllIcons.Actions.Close
    e.presentation.hoveredIcon = AllIcons.Actions.CloseHovered
    e.presentation.text = XDebuggerBundle.message("action.hotswap.hide.text")
  }
}

private val DataContext.editorTag: String? get() = getData(PlatformCoreDataKeys.FILE_EDITOR)?.file?.path

private val XDebugHotSwapCurrentSessionStatus.hasChanges get() = status.hasChanges
private val HotSwapVisibleStatus.hasChanges get() =
  this == HotSwapVisibleStatus.ChangesReady || this is HotSwapVisibleStatus.ChangesNotHotSwappable

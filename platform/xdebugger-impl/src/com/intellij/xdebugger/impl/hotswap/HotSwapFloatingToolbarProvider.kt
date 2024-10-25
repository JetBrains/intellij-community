// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.hotswap

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
import com.intellij.util.ui.JBUI
import com.intellij.xdebugger.XDebuggerBundle
import icons.PlatformDebuggerImplIcons
import kotlinx.coroutines.*
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Insets
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants
import kotlin.time.Duration.Companion.seconds

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
    val session = findSession(project) ?: return
    if (!session.hasChanges) return
    HotSwapStatistics.logHotSwapCalled(project, HotSwapStatistics.HotSwapSource.RELOAD_MODIFIED_ACTION)
    HotSwapWithRebuildAction.performHotSwap(e.dataContext, session)
  }

  override fun update(e: AnActionEvent) {
    val session = findSession(e.project)
    e.presentation.isEnabled = session?.hasChanges == true
    e.presentation.isVisible = Registry.`is`("debugger.hotswap.floating.toolbar")
    if (e.place != ActionPlaces.MAIN_MENU) {
      e.presentation.isVisible = e.presentation.isVisible && session != null
    }
    e.presentation.icon = hotSwapIcon
  }

  private fun findSession(project: Project?): HotSwapSession<*>? {
    if (project == null) return null
    return HotSwapSessionManager.getInstance(project).currentSession
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT
}

private enum class HotSwapButtonStatus {
  READY, IN_PROGRESS, SUCCESS
}

private class HotSwapWithRebuildAction : AnAction(), CustomComponentAction, DumbAware {
  var status = HotSwapButtonStatus.READY

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val session = HotSwapSessionManager.getInstance(project).currentSession ?: return
    HotSwapStatistics.logHotSwapCalled(project, HotSwapStatistics.HotSwapSource.RELOAD_MODIFIED_BUTTON)
    performHotSwap(e.dataContext, session)
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

  companion object {
    fun <S> performHotSwap(context: DataContext, session: HotSwapSession<S>) {
      session.provider.performHotSwap(context, session)
    }
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
  HotSwapSessionManager.getInstance(project).coroutineScope.launch(Dispatchers.Default) {
    // Hide toolbar after setting disable
    if (showFloatingToolbar()) return@launch
    HotSwapSessionManager.getInstance(project).notifyUpdate()
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

  override val actionGroup: ActionGroup by lazy { DefaultActionGroup(hotSwapAction, HideAction()) }

  override fun isApplicable(dataContext: DataContext): Boolean =
    isInsideMainEditor(dataContext)
    && dataContext.getData(CommonDataKeys.EDITOR)?.editorKind == EditorKind.MAIN_EDITOR

  override fun register(dataContext: DataContext, component: FloatingToolbarComponent, parentDisposable: Disposable) {
    val project = dataContext.getData(CommonDataKeys.PROJECT) ?: return
    val instance = HotSwapSessionManager.getInstance(project)

    if (component is AbstractFloatingToolbarComponent) {
      component.backgroundAlpha = 0.9f
      component.showingTime = SHOWING_TIME_MS
      component.hidingTime = HIDING_TIME_MS
    }
    if (component is JComponent) {
      component.installPopupMenu()
    }
    instance.addListener(ChangesListener(component, project), parentDisposable)
  }

  private inner class ChangesListener(private val component: FloatingToolbarComponent, private val project: Project) : HotSwapChangesListener {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onStatusChanged(forceStatus: HotSwapVisibleStatus?) {
      val manager = HotSwapSessionManager.getInstance(project)
      // We need to hide the button even if the coroutineScope is cancelled
      manager.coroutineScope.launch(Dispatchers.EDT, start = CoroutineStart.ATOMIC) {
        if (!showFloatingToolbar()) {
          if (logger.isDebugEnabled) {
            logger.debug("Hide button because it is disabled")
          }
          component.scheduleHide()
          return@launch
        }
        val session = manager.currentSession
        val status = forceStatus ?: session?.currentStatus

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
            manager.coroutineScope.launch(Dispatchers.Default) {
              delay(NOTIFICATION_TIME_SECONDS.seconds)
              onStatusChanged(null)
            }
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
    }

    private fun updateActions() {
      if (component is ActionToolbarImpl) {
        component.updateActionsAsync()
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
    HotSwapSessionManager.getInstance(project).notifyUpdate(HotSwapVisibleStatus.HIDDEN)
  }

  override fun getActionUpdateThread() = ActionUpdateThread.EDT
  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = addHideAction
    e.presentation.icon = AllIcons.Actions.Close
    e.presentation.hoveredIcon = AllIcons.Actions.CloseHovered
    e.presentation.text = XDebuggerBundle.message("action.hotswap.hide.text")
  }
}

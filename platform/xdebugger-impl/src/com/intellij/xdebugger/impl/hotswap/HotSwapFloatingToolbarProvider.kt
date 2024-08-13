// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.hotswap

import com.intellij.icons.AllIcons
import com.intellij.ide.HelpTooltip
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.toolbar.floating.AbstractFloatingToolbarComponent
import com.intellij.openapi.editor.toolbar.floating.FloatingToolbarComponent
import com.intellij.openapi.editor.toolbar.floating.FloatingToolbarProvider
import com.intellij.openapi.editor.toolbar.floating.isInsideMainEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.putUserData
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.xdebugger.XDebuggerBundle
import icons.PlatformDebuggerImplIcons
import kotlinx.coroutines.*
import java.awt.BorderLayout
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.time.Duration.Companion.seconds

private val hotSwapIcon: Icon by lazy {
  HotSwapUiExtension.computeSafeIfAvailable { it.hotSwapIcon } ?: PlatformDebuggerImplIcons.Actions.DebuggerSync
}

private val addHideAction: Boolean by lazy {
  HotSwapUiExtension.computeSafeIfAvailable { it.shouldAddHideButton } != false
}

private fun createHelpTooltip(): HelpTooltip =
  HotSwapUiExtension.computeSafeIfAvailable { it.createTooltip() }
  ?: HelpTooltip()
    .setTitle(XDebuggerBundle.message("xdebugger.hotswap.tooltip.apply"))
    .setDescription(XDebuggerBundle.message("xdebugger.hotswap.tooltip.description"))

private fun showFloatingToolbar(): Boolean = HotSwapUiExtension.computeSafeIfAvailable { it.showFloatingToolbar() } != false

internal class HotSwapModifiedFilesAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val session = findSessionIfReady(e.project) ?: return
    HotSwapWithRebuildAction.performHotSwap(e.dataContext, session)
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isVisible = Registry.`is`("debugger.hotswap.floating.toolbar")
    e.presentation.isEnabled = findSessionIfReady(e.project) != null
    e.presentation.icon = hotSwapIcon
  }

  private fun findSessionIfReady(project: Project?): HotSwapSession<*>? {
    if (project == null) return null
    val session = HotSwapSessionManager.getInstance(project).currentSession ?: return null
    if (session.currentStatus != HotSwapVisibleStatus.CHANGES_READY) return null
    return session
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT
}

private enum class HotSwapButtonStatus {
  READY, IN_PROGRESS, SUCCESS
}

private class HotSwapWithRebuildAction : AnAction(), CustomComponentAction {
  var status = HotSwapButtonStatus.READY

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val session = HotSwapSessionManager.getInstance(project).currentSession ?: return
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
  : JPanel(BorderLayout(JBUI.scale(4), 0)) {

  private val tooltip = createHelpTooltip()
    .setShortcut(ActionManager.getInstance().getKeyboardShortcut("XDebugger.Hotswap.Modified.Files"))
  val button = ActionButton(action, presentation, place, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE).apply {
    tooltip.installOn(this)
  }

  init {
    isOpaque = false
    add(JBLabel(XDebuggerBundle.message("xdebugger.hotswap.code.changed")), BorderLayout.WEST)
    add(button, BorderLayout.CENTER)
    tooltip.installOn(this)
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
  }

}

private fun updateToolbarVisibility(project: Project) {
  HotSwapSessionManager.getInstance(project).coroutineScope.launch(Dispatchers.Default) {
    if (showFloatingToolbar()) return@launch
    HotSwapSessionManager.getInstance(project).notifyUpdate()
  }
}

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
      component.putUserData(AbstractFloatingToolbarComponent.CUSTOM_OPACITY_KEY, 0.9f)
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
          component.scheduleHide()
          return@launch
        }
        val session = manager.currentSession
        val status = forceStatus ?: session?.currentStatus

        val action = when (status) {
          HotSwapVisibleStatus.IN_PROGRESS -> {
            hotSwapAction.status = HotSwapButtonStatus.IN_PROGRESS
            updateActions()
            return@launch
          }
          HotSwapVisibleStatus.SUCCESS -> {
            hotSwapAction.status = HotSwapButtonStatus.SUCCESS
            updateActions()
            manager.coroutineScope.launch(Dispatchers.Default) {
              delay(NOTIFICATION_TIME_SECONDS.seconds)
              onStatusChanged(null)
            }
            return@launch
          }
          HotSwapVisibleStatus.NO_CHANGES, HotSwapVisibleStatus.HIDDEN -> HotSwapButtonAction.HIDE
          HotSwapVisibleStatus.CHANGES_READY -> HotSwapButtonAction.SHOW
          HotSwapVisibleStatus.SESSION_COMPLETED -> HotSwapButtonAction.HIDE_NOW
          null -> HotSwapButtonAction.HIDE_NOW
        }
        if (action == HotSwapButtonAction.SHOW) {
          hotSwapAction.status = HotSwapButtonStatus.READY
          updateActions()
        }
        when (action) {
          HotSwapButtonAction.SHOW -> component.scheduleShow()
          HotSwapButtonAction.HIDE -> component.scheduleHide()
          HotSwapButtonAction.HIDE_NOW -> component.hideImmediately()
        }
      }
    }

    private fun updateActions() {
      if (component is ActionToolbarImpl) {
        component.updateActionsAsync()
      }
    }
  }
}

private enum class HotSwapButtonAction {
  SHOW, HIDE, HIDE_NOW
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

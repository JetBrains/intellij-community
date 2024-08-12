// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.hotswap

import com.intellij.ide.HelpTooltip
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButton
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

private val hotSwapIcon: Icon by lazy {
  HotSwapUiExtension.computeSafeIfAvailable { it.hotSwapIcon } ?: PlatformDebuggerImplIcons.Actions.DebuggerSync
}

private fun createHelpTooltip(): HelpTooltip =
  HotSwapUiExtension.computeSafeIfAvailable { it.createTooltip() }
  ?: HelpTooltip()
    .setTitle(XDebuggerBundle.message("xdebugger.hotswap.tooltip.apply"))
    .setDescription(XDebuggerBundle.message("xdebugger.hotswap.tooltip.description"))

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

private class HotSwapWithRebuildAction : AnAction(), CustomComponentAction {
  var inProgress = false
  var session: HotSwapSession<*>? = null

  override fun actionPerformed(e: AnActionEvent) {
    val session = session ?: return
    performHotSwap(e.dataContext, session)
  }

  override fun getActionUpdateThread() = ActionUpdateThread.EDT

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return HotSwapToolbarComponent(this, presentation, place)
  }

  override fun updateCustomComponent(component: JComponent, presentation: Presentation) {
    (component as HotSwapToolbarComponent).update(inProgress, presentation)
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

  fun update(inProgress: Boolean, presentation: Presentation) {
    presentation.isEnabled = !inProgress
    presentation.icon = if (inProgress) AnimatedIcon.Default.INSTANCE else hotSwapIcon
    // Force animation in the disabled state
    presentation.disabledIcon = presentation.icon
  }
}

internal class HotSwapFloatingToolbarProvider : FloatingToolbarProvider {
  override val autoHideable: Boolean get() = false
  private val hotSwapAction by lazy { HotSwapWithRebuildAction() }

  override val actionGroup: ActionGroup by lazy { DefaultActionGroup(hotSwapAction) }

  override fun isApplicable(dataContext: DataContext): Boolean =
    Registry.`is`("debugger.hotswap.floating.toolbar")
    && isInsideMainEditor(dataContext)
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
    override fun onStatusChanged() {
      val manager = HotSwapSessionManager.getInstance(project)
      // We need to hide the button even if the coroutineScope is cancelled
      manager.coroutineScope.launch(Dispatchers.EDT, start = CoroutineStart.ATOMIC) {
        val session = manager.currentSession
        val status = session?.currentStatus
        if (status == HotSwapVisibleStatus.IN_PROGRESS) {
          hotSwapAction.inProgress = true
          hotSwapAction.session = null
          return@launch
        }

        val action = when (status) {
          HotSwapVisibleStatus.NO_CHANGES -> HotSwapButtonAction.HIDE
          HotSwapVisibleStatus.CHANGES_READY -> HotSwapButtonAction.SHOW
          HotSwapVisibleStatus.SESSION_COMPLETED -> HotSwapButtonAction.HIDE_NOW
          null -> HotSwapButtonAction.HIDE_NOW
          else -> error("Unexpected status $status")
        }
        if (action == HotSwapButtonAction.SHOW) {
          hotSwapAction.inProgress = false
          hotSwapAction.session = session
        }
        else {
          hotSwapAction.session = null
        }
        when (action) {
          HotSwapButtonAction.SHOW -> component.scheduleShow()
          HotSwapButtonAction.HIDE -> component.scheduleHide()
          HotSwapButtonAction.HIDE_NOW -> component.hideImmediately()
        }
      }
    }
  }
}

private enum class HotSwapButtonAction {
  SHOW, HIDE, HIDE_NOW
}

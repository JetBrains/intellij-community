// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.codeInsight.hint.HintUtil
import com.intellij.icons.AllIcons
import com.intellij.ide.HelpTooltip
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.IdeActions.ACTION_CHECKIN_PROJECT
import com.intellij.openapi.actionSystem.ex.ActionUtil.invokeAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager.Companion.COMMIT_TOOLWINDOW_ID
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi.HIDE_ID_LABEL
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.awt.RelativePoint.getSouthOf
import com.intellij.util.ui.JBUI.emptyInsets
import com.intellij.util.ui.PositionTracker
import com.intellij.util.ui.UIUtil.uiTraverser
import com.intellij.util.ui.update.Activatable
import com.intellij.util.ui.update.UiNotifyConnector
import com.intellij.vcs.commit.CommitWorkflowManager.Companion.setCommitFromLocalChanges
import javax.swing.JComponent

private class ChangesViewToolWindowFactory : VcsToolWindowFactory() {
  override fun updateState(project: Project, toolWindow: ToolWindow) {
    super.updateState(project, toolWindow)
    toolWindow.stripeTitle = project.vcsManager.allActiveVcss.singleOrNull()?.displayName ?: ChangesViewContentManager.TOOLWINDOW_ID
  }
}

private class CommitToolWindowFactory : VcsToolWindowFactory() {
  override fun init(window: ToolWindow) {
    super.init(window)

    window as ToolWindowEx
    window.setAdditionalGearActions(DefaultActionGroup(SwitchToCommitDialogAction()))
  }

  override fun shouldBeAvailable(project: Project): Boolean {
    return super.shouldBeAvailable(project) && project.isCommitToolWindow
  }

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    toolWindow.component.putClientProperty(HIDE_ID_LABEL, "true")
    super.createToolWindowContent(project, toolWindow)
  }
}

private class SwitchToCommitDialogAction : DumbAwareAction() {
  init {
    templatePresentation.text = message("action.switch.to.commit.dialog.text")
  }

  override fun actionPerformed(e: AnActionEvent) {
    setCommitFromLocalChanges(false)

    val commitAction = ActionManager.getInstance().getAction(ACTION_CHECKIN_PROJECT) ?: return
    invokeAction(commitAction, e.dataContext, e.place, e.inputEvent, null)
  }
}

internal class SwitchToCommitDialogHint(private val toolWindow: ToolWindowEx, private val toolbar: ActionToolbar) :
  ChangesViewContentManagerListener,
  Activatable {

  private val toolbarVisibilityTracker =
    UiNotifyConnector(toolbar.component, this).also { Disposer.register(toolWindow.disposable, it) }
  private var balloon: Balloon? = null

  init {
    toolWindow.project.messageBus.connect(toolbarVisibilityTracker).subscribe(ChangesViewContentManagerListener.TOPIC, this)
  }

  override fun showNotify() = showHint()
  override fun hideNotify() = hideHint(false)

  override fun toolWindowMappingChanged() = hideHint(true)

  private fun showHint() {
    balloon = createBalloon(createContent())
    balloon?.run {
      setAnimationEnabled(false)
      show(SouthPositionTracker(toolbar.getGearButton() ?: toolbar.component), Balloon.Position.below)
    }
  }

  private fun hideHint(dispose: Boolean) {
    balloon?.hide()
    balloon = null

    if (dispose) Disposer.dispose(toolbarVisibilityTracker)
  }

  private fun createContent(): JComponent =
    object : HelpTooltip() {
      init {
        setDescription(message("switch.to.commit.dialog.hint.text"))
        setLink(message("switch.to.commit.dialog.hint.got.it.text")) { hideHint(true) }
      }

      fun createComponent(): JComponent = createTipPanel()
    }.createComponent()

  private fun createBalloon(content: JComponent): Balloon =
    JBPopupFactory.getInstance().createBalloonBuilder(content)
      .setBorderInsets(emptyInsets())
      .setFillColor(content.background ?: HintUtil.getInformationColor())
      .setHideOnClickOutside(false)
      .setHideOnAction(false)
      .setHideOnFrameResize(false)
      .setHideOnKeyOutside(false)
      .setBlockClicksThroughBalloon(true)
      .setDisposable(toolbarVisibilityTracker)
      .createBalloon()

  companion object {
    fun install(project: Project) {
      val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(COMMIT_TOOLWINDOW_ID) as? ToolWindowEx ?: return
      val toolbar = toolWindow.decorator.headerToolbar ?: return

      SwitchToCommitDialogHint(toolWindow, toolbar)
    }
  }
}

private class SouthPositionTracker(private val component: JComponent) : PositionTracker<Balloon>(component) {
  override fun recalculateLocation(balloon: Balloon): RelativePoint = getSouthOf(component)
}

private fun ActionToolbar.getGearButton(): ActionButton? =
  uiTraverser(component)
    .filter(ActionButton::class.java)
    .filter { it.icon == AllIcons.General.GearPlain }
    .first()
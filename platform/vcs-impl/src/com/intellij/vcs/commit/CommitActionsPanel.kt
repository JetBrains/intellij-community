// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.commit

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ActionToolbar.NOWRAP_LAYOUT_POLICY
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.SystemInfo.isMac
import com.intellij.ui.components.JBOptionButton
import com.intellij.ui.components.JBOptionButton.Companion.getDefaultShowPopupShortcut
import com.intellij.ui.components.JBPanel
import com.intellij.util.EventDispatcher
import net.miginfocom.swing.MigLayout
import org.jetbrains.annotations.Nls
import java.awt.event.ActionEvent
import java.awt.event.InputEvent.CTRL_DOWN_MASK
import java.awt.event.InputEvent.META_DOWN_MASK
import java.awt.event.KeyEvent.VK_ENTER
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.KeyStroke.getKeyStroke

private fun JBOptionButton.getBottomInset(): Int =
  border?.getBorderInsets(this)?.bottom
  ?: (components.firstOrNull() as? JComponent)?.insets?.bottom
  ?: 0

class CommitActionsPanel : JBPanel<CommitActionsPanel>(null), CommitActionsUi {
  private val executorEventDispatcher = EventDispatcher.create(CommitExecutorListener::class.java)

  private val defaultCommitAction = object : AbstractAction() {
    override fun actionPerformed(e: ActionEvent) = fireDefaultExecutorCalled()
  }
  private val commitButton = object : JBOptionButton(defaultCommitAction, emptyArray()) {
    init {
      optionTooltipText = getDefaultTooltip()
    }

    override fun isDefaultButton(): Boolean = isCommitButtonDefault()
  }

  private val primaryActionGroup = DefaultActionGroup()
  private val primaryCommitActionsToolbar =
    ActionManager.getInstance().createActionToolbar(COMMIT_BUTTONS_TOOLBAR, primaryActionGroup, true).apply {
      component.putClientProperty(ActionToolbarImpl.IMPORTANT_TOOLBAR_KEY, true)
      setReservePlaceAutoPopupIcon(false)
      layoutPolicy = NOWRAP_LAYOUT_POLICY

      component.isOpaque = false
      component.border = null
    }

  private val commitOptionToolbar = ActionManager.getInstance().createActionToolbar(
    "ChangesView.ShowCommitOptions",
    DefaultActionGroup(ActionManager.getInstance().getAction("ChangesView.ShowCommitOptions")),
    true
  ).apply {
    component.putClientProperty(ActionToolbarImpl.IMPORTANT_TOOLBAR_KEY, true)
    setReservePlaceAutoPopupIcon(false)
    layoutPolicy = NOWRAP_LAYOUT_POLICY

    component.isOpaque = false
    component.border = null
  }

  init {
    layout = MigLayout("ins 0, fill", "[left]0[left, fill]push[pref:pref, right]", "center")
    add(commitButton)
    add(primaryCommitActionsToolbar.component)
    add(commitOptionToolbar.component)
    isOpaque = false
  }

  var isActive: Boolean = true
  var isCommitButtonDefault: () -> Boolean = { false }

  fun getBottomInset(): Int = commitButton.getBottomInset()
  fun setTargetComponent(component: JComponent) {
    primaryCommitActionsToolbar.targetComponent = component
    commitOptionToolbar.targetComponent = component
  }

  fun createActions() = listOf(DefaultCommitAction(), ShowCustomCommitActions())

  // NOTE: getter should return text with mnemonic (if any) to make mnemonics available in dialogs shown by commit handlers.
  //  See CheckinProjectPanel.getCommitActionName() usages.
  override var defaultCommitActionName: String
    get() = (defaultCommitAction.getValue(Action.NAME) as? String).orEmpty()
    set(@Nls value) {
      defaultCommitAction.putValue(Action.NAME, value)
      primaryCommitActionsToolbar.updateActionsImmediately()
    }

  override var isDefaultCommitActionEnabled: Boolean
    get() = defaultCommitAction.isEnabled
    set(value) {
      defaultCommitAction.isEnabled = value
      primaryCommitActionsToolbar.updateActionsImmediately()
    }

  override fun setPrimaryCommitActions(actions: List<AnAction>) {
    primaryActionGroup.removeAll()
    primaryActionGroup.addAll(actions)
    primaryCommitActionsToolbar.updateActionsImmediately()
  }

  override fun setCustomCommitActions(actions: List<AnAction>) = commitButton.setOptions(actions)

  override fun addExecutorListener(listener: CommitExecutorListener, parent: Disposable) =
    executorEventDispatcher.addListener(listener, parent)

  private fun isDefaultExecutorEnabled() = isActive && defaultCommitAction.isEnabled

  private fun fireDefaultExecutorCalled() = executorEventDispatcher.multicaster.executorCalled(null)

  inner class DefaultCommitAction : DumbAwareAction() {
    init {
      shortcutSet = DEFAULT_COMMIT_ACTION_SHORTCUT
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
      return ActionUpdateThread.EDT
    }

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabledAndVisible = isDefaultExecutorEnabled() && commitButton.isDefaultButton
    }

    override fun actionPerformed(e: AnActionEvent) = fireDefaultExecutorCalled()
  }

  private inner class ShowCustomCommitActions : DumbAwareAction() {
    init {
      shortcutSet = getDefaultShowPopupShortcut()
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
      return ActionUpdateThread.EDT
    }

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabledAndVisible = isActive && commitButton.isEnabled
    }

    override fun actionPerformed(e: AnActionEvent) = commitButton.showPopup()
  }

  companion object {
    private val CTRL_ENTER = KeyboardShortcut(getKeyStroke(VK_ENTER, CTRL_DOWN_MASK), null)
    private val META_ENTER = KeyboardShortcut(getKeyStroke(VK_ENTER, META_DOWN_MASK), null)

    const val COMMIT_BUTTONS_TOOLBAR = "ChangesView.CommitButtonsToolbar"

    val DEFAULT_COMMIT_ACTION_SHORTCUT: ShortcutSet =
      if (isMac) CustomShortcutSet(CTRL_ENTER, META_ENTER) else CustomShortcutSet(CTRL_ENTER)
  }
}
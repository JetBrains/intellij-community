// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.commit

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ActionToolbar.NOWRAP_LAYOUT_POLICY
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.SystemInfo.isMac
import com.intellij.ui.components.JBOptionButton
import com.intellij.ui.components.JBOptionButton.Companion.getDefaultShowPopupShortcut
import com.intellij.util.EventDispatcher
import com.intellij.util.ui.components.BorderLayoutPanel
import org.jetbrains.annotations.Nls
import java.awt.event.ActionEvent
import java.awt.event.InputEvent.CTRL_DOWN_MASK
import java.awt.event.InputEvent.META_DOWN_MASK
import java.awt.event.KeyEvent.VK_ENTER
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.KeyStroke.getKeyStroke

private fun JBOptionButton.getBottomInset(): Int =
  border?.getBorderInsets(this)?.bottom
  ?: (components.firstOrNull() as? JComponent)?.insets?.bottom
  ?: 0

class CommitActionsPanel : BorderLayoutPanel(), CommitActionsUi {
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
  private val primaryCommitActionsToolbar =
    ActionManager.getInstance().createActionToolbar(
      "ChangesView.CommitButtonsToolbar",
      ActionManager.getInstance().getAction("Vcs.Commit.PrimaryCommitActions") as ActionGroup,
      true
    ).apply {
      setReservePlaceAutoPopupIcon(false)
      layoutPolicy = NOWRAP_LAYOUT_POLICY

      component.isOpaque = false
      component.border = null
    }

  init {
    addToLeft(commitButton)
    addToCenter(primaryCommitActionsToolbar.component)
  }

  var isActive: Boolean = true
  var isCommitButtonDefault: () -> Boolean = { false }

  fun getBottomInset(): Int = commitButton.getBottomInset()
  fun setTargetComponent(component: JComponent) = primaryCommitActionsToolbar.setTargetComponent(component)

  fun setupShortcuts(component: JComponent, parentDisposable: Disposable) {
    DefaultCommitAction().registerCustomShortcutSet(DEFAULT_COMMIT_ACTION_SHORTCUT, component, parentDisposable)
    ShowCustomCommitActions().registerCustomShortcutSet(getDefaultShowPopupShortcut(), component, parentDisposable)
  }

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

  override fun setCustomCommitActions(actions: List<AnAction>) = commitButton.setOptions(actions)

  override fun addExecutorListener(listener: CommitExecutorListener, parent: Disposable) =
    executorEventDispatcher.addListener(listener, parent)

  private fun fireDefaultExecutorCalled() = executorEventDispatcher.multicaster.executorCalled(null)

  inner class DefaultCommitAction : DumbAwareAction() {
    override fun update(e: AnActionEvent) {
      e.presentation.isEnabledAndVisible = isActive && defaultCommitAction.isEnabled && commitButton.isDefaultButton
    }

    override fun actionPerformed(e: AnActionEvent) = fireDefaultExecutorCalled()
  }

  private inner class ShowCustomCommitActions : DumbAwareAction() {
    override fun update(e: AnActionEvent) {
      e.presentation.isEnabledAndVisible = isActive && commitButton.isEnabled
    }

    override fun actionPerformed(e: AnActionEvent) = commitButton.showPopup()
  }

  companion object {
    private val CTRL_ENTER = KeyboardShortcut(getKeyStroke(VK_ENTER, CTRL_DOWN_MASK), null)
    private val META_ENTER = KeyboardShortcut(getKeyStroke(VK_ENTER, META_DOWN_MASK), null)
    val DEFAULT_COMMIT_ACTION_SHORTCUT: ShortcutSet =
      if (isMac) CustomShortcutSet(CTRL_ENTER, META_ENTER) else CustomShortcutSet(CTRL_ENTER)
  }
}
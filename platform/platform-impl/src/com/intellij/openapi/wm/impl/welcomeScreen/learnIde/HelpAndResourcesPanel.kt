// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.learnIde

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.*
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.wm.impl.welcomeScreen.learnIde.coursesInProgress.createTitlePanel
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import javax.swing.*
import javax.swing.plaf.ComponentUI

class HelpAndResourcesPanel : JPanel() {
  private val helpAndResourcesHeader: JTextPane = createTitlePanel(IdeBundle.message("welcome.screen.learnIde.help.and.resources.text"))

  init {
    initPanel()
  }

  fun initPanel() {
    layout = BoxLayout(this, BoxLayout.PAGE_AXIS)
    isOpaque = false
    add(helpAndResourcesHeader)
    add(rigid(0, 1))
    addHelpActions()
  }

  private fun rigid(width: Int, height: Int): Component {
    val d = Dimension(JBUI.scale(width), JBUI.scale(height))
    return object : Box.Filler(d, d, d) {
      init {
        alignmentX = LEFT_ALIGNMENT
      }

      override fun updateUI() {
        super.updateUI()
        val newD = Dimension(JBUI.scale(width), JBUI.scale(height))
        minimumSize = newD
        preferredSize = newD
        maximumSize = newD
      }

      override fun setUI(newUI: ComponentUI?) {
        super.setUI(newUI)
      }
    }
  }

  private fun addHelpActions() {
    val whatsNewAction = WhatsNewAction()
    if (emptyWelcomeScreenEventFromAction(whatsNewAction).presentation.isEnabled) {
      add(linkLabelByAction(WhatsNewAction()))
      add(rigid(1, 16))
    }
    val helpActions = ActionManager.getInstance().getAction(IdeActions.GROUP_WELCOME_SCREEN_LEARN_IDE) as ActionGroup
    val anActionEvent = emptyWelcomeScreenEventFromAction(helpActions)
    helpActions.getChildren(anActionEvent).forEach {
      if (it is HelpActionBase && !it.isAvailable) {
        return@forEach
      }
      if (setOf<String>(HelpTopicsAction::class.java.simpleName, OnlineDocAction::class.java.simpleName,
                        JetBrainsTvAction::class.java.simpleName).any { simpleName -> simpleName == it.javaClass.simpleName }) {
        add(linkLabelByAction(it).wrapWithUrlPanel())
      }
      else {
        add(linkLabelByAction(it))

      }
      add(rigid(1, 6))
    }
  }

  private fun linkLabelByAction(it: AnAction): LinkLabel<Any> {
    return LinkLabel<Any>(it.templateText, null).apply {
      alignmentX = LEFT_ALIGNMENT
      setListener({ _, _ -> performActionOnWelcomeScreen(it) }, null)
    }
  }

  private fun performActionOnWelcomeScreen(action: AnAction) {
    val anActionEvent = AnActionEvent.createFromAnAction(action, null, ActionPlaces.WELCOME_SCREEN, DataContext.EMPTY_CONTEXT)
    ActionUtil.performActionDumbAwareWithCallbacks(action, anActionEvent)
  }

  private fun LinkLabel<Any>.wrapWithUrlPanel(): JPanel {
    return JPanel().apply {
      isOpaque = false
      layout = BoxLayout(this, BoxLayout.LINE_AXIS)
      add(this@wrapWithUrlPanel, BorderLayout.CENTER)
      add(JLabel(AllIcons.Ide.External_link_arrow), BorderLayout.EAST)
      maximumSize = this.preferredSize
      alignmentX = LEFT_ALIGNMENT
    }
  }

  private fun emptyWelcomeScreenEventFromAction(action: AnAction) =
    AnActionEvent.createFromAnAction(action, null, ActionPlaces.WELCOME_SCREEN, DataContext.EMPTY_CONTEXT)
}
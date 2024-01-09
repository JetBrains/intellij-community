// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.chooser.ui

import com.intellij.ide.startup.importSettings.data.*
import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.platform.ide.bootstrap.StartupWizardStage
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.ActionEvent
import javax.swing.Action
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

class OnboardingDialog(val cancelCallback: () -> Unit) : DialogWrapper(null, null, true, IdeModalityType.IDE,
                                                                       false) {

  private val tracker = WizardPageTracker()

  private val pane = JPanel(BorderLayout()).apply {
    border = JBUI.Borders.empty()
    preferredSize = JBDimension(640, 457)
  }

  private var currentPage: OnboardingPage = object  : OnboardingPage {
    override val content: JComponent = JPanel()
    override val stage: StartupWizardStage = StartupWizardStage.InitialStart
    override fun confirmExit(parentComponent: Component?): Boolean = true
  }

  override fun doCancelAction() {
    val shouldExit = currentPage.confirmExit(peer.contentPane)

    if (shouldExit) {
      super.doCancelAction()
      tracker.onLeave()
      cancelCallback()
    }
  }

  fun doClose(code: Int) {
    tracker.onLeave()
    close(code)
  }

  fun changePage(page: OnboardingPage) {
    overlay.clearNotifications()
    pane.remove(currentPage.content)
    Disposer.dispose(currentPage)

    tracker.onLeave()

    val content = page.content
    pane.add(content)

    currentPage = page
    tracker.onEnter(page.stage)
  }

  override fun getStyle(): DialogStyle {
    return DialogStyle.COMPACT
  }

  private val overlay = BannerOverlay(pane)

  fun initialize() {
    init()
  }

  fun showError(notification: NotificationData) {
    overlay.showError(notification)
  }

  override fun createCenterPanel(): JComponent {
    return overlay.component
  }

  fun createDefaultButton(name: @Nls String, handler: () -> Unit): JButton {
    val action = createAction(name, handler).apply {
      putValue(DEFAULT_ACTION, true)
      putValue(FOCUSED_ACTION, true)
    }

    return createJButtonForAction(action)
  }

  fun createButton(name: @Nls String, handler: () -> Unit): JButton {
    val action = createAction(name, handler)
    return createJButtonForAction(action)
  }

  private fun createAction(name: @Nls String, handler: () -> Unit): Action {
    return object : DialogWrapperAction(name) {
      override fun doAction(e: ActionEvent?) {
        handler()
      }
    }
  }
}

interface OnboardingPage: Disposable {
  val content: JComponent
  val stage: StartupWizardStage?

  override fun dispose() {}

  fun confirmExit(parentComponent: Component?): Boolean
}

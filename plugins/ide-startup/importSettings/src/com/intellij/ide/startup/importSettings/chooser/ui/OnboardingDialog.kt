// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.chooser.ui

import com.intellij.ide.startup.importSettings.data.NotificationData
import com.intellij.ide.startup.importSettings.data.StartupWizardService
import com.intellij.ide.startup.importSettings.statistics.ImportSettingsEventsCollector
import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.OnboardingBackgroundImageProvider
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.ide.bootstrap.StartupWizardStage
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.jetbrains.rd.util.lifetime.Lifetime
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Image
import java.awt.event.ActionEvent
import javax.swing.Action
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.border.Border

internal class OnboardingDialog(
  var titleGetter: (StartupWizardStage?) -> @NlsContexts.DialogTitle String?,
  val cancelCallback: () -> Unit,
) : DialogWrapper(null, null, true, IdeModalityType.IDE, false) {

  private val tracker = WizardPageTracker()

  private val pane = JPanel(BorderLayout()).apply {
    border = JBUI.Borders.empty()
    preferredSize = JBDimension(640, 467)
  }

  private var currentPage: OnboardingPage = object  : OnboardingPage {
    override val content: JComponent = JPanel()
    override val stage: StartupWizardStage = StartupWizardStage.InitialStart
    override fun confirmExit(parentComponent: Component?): Boolean = true
  }

  override fun doCancelAction() {
    val shouldExit = currentPage.confirmExit(peer.contentPane)

    if (shouldExit) {
      tracker.onLeave()
      ImportSettingsEventsCollector.dialogClosed()
      StartupWizardService.getInstance()?.onCancel()
      cancelCallback()
      super.doCancelAction()
    }
  }

  fun dialogClose() {
    if(isShowing && isVisible) {
      doClose(CANCEL_EXIT_CODE)
    }
  }

  private fun doClose(code: Int) {
    tracker.onLeave()
    close(code)
  }

  fun changePage(page: OnboardingPage) {
    overlay.clearNotifications()
    pane.removeAll()
    Disposer.dispose(currentPage)

    tracker.onLeave()
    title = titleGetter(page.stage) ?: ""

    val content = page.content
    pane.add(content)

    currentPage = page
    tracker.onEnter(page.stage)

    OnboardingBackgroundImageProvider.getInstance().setBackgroundImageToDialog(this, page.backgroundImage)
  }

  override fun createContentPaneBorder(): Border {
    return JBUI.Borders.empty()
  }


  private val overlay = BannerOverlay(pane)

  fun initialize() {
    init()
  }

  fun showOverlay(notification: NotificationData, lifetime: Lifetime) {
    overlay.showOverlay(notification, lifetime, OnboardingBackgroundImageProvider.getInstance().hasBackgroundImage(this))
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

  override fun dispose() {
    StartupWizardService.getInstance()?.onExit()
    super.dispose()
  }
}

internal interface OnboardingPage: Disposable {
  val content: JComponent
  val stage: StartupWizardStage?
  val backgroundImage: Image? get() = null

  override fun dispose() {}

  fun confirmExit(parentComponent: Component?): Boolean
}

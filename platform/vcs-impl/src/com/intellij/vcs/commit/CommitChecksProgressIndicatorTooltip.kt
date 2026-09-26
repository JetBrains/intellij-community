// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ex.ProgressIndicatorEx
import com.intellij.ui.ClickListener
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.popup.AbstractPopup
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.scale
import com.intellij.util.ui.UIUtil.getRegularPanelInsets
import java.awt.Component
import java.awt.Point
import java.awt.event.MouseEvent
import javax.swing.JComponent

internal class CommitChecksProgressIndicatorTooltip(
  private val indicatorProvider: () -> ProgressIndicatorEx?,
  private val widthProvider: () -> Int
) : ClickListener() {

  private var popup: JBPopup? = null

  fun installOn(component: JComponent, parent: Disposable) {
    installOn(component)
    Disposer.register(parent) { uninstall(component) }
  }

  override fun onClick(event: MouseEvent, clickCount: Int): Boolean {
    val original = indicatorProvider()
    if (original?.isRunning != true) return false

    closePopup()
    showPopup(event.component, original)
    return false
  }

  private fun showPopup(owner: Component, original: ProgressIndicatorEx) {
    popup = createPopup(original)
    popup?.run {
      content.putClientProperty(AbstractPopup.FIRST_TIME_SIZE, JBUI.size(widthProvider(), 0))
      show(RelativePoint(owner, Point(0, -content.preferredSize.height - scale(8))))
    }
  }

  private fun createPopup(original: ProgressIndicatorEx): JBPopup {
    val indicator = PopupCommitChecksProgressIndicator(original)
    indicator.addStateDelegate(object : AbstractProgressIndicatorExBase() {
      override fun stop() = closePopup()
    })

    val popupComponent = Wrapper(indicator.component).apply { border = JBEmptyBorder(getRegularPanelInsets()) }
    return JBPopupFactory.getInstance()
      .createComponentPopupBuilder(popupComponent, null)
      .createPopup()
  }

  private fun closePopup() {
    popup?.cancel()
    popup = null
  }
}
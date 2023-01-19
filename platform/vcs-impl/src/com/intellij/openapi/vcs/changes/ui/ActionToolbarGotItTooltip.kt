// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.internal.statistic.collectors.fus.ui.GotItUsageCollector
import com.intellij.internal.statistic.collectors.fus.ui.GotItUsageCollectorGroup
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.util.Disposer
import com.intellij.ui.GotItTooltip
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.update.Activatable
import com.intellij.util.ui.update.UiNotifyConnector
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import javax.swing.JComponent

internal class ActionToolbarGotItTooltip(@NonNls private val id: String,
                                         @Nls private val tooltipText: String,
                                         disposable: Disposable,
                                         private val toolbar: ActionToolbar,
                                         private val actionComponentSelector: (ActionToolbar) -> JComponent?) : Activatable {
  val tooltipDisposable = Disposer.newDisposable().also { Disposer.register(disposable, it) }
  private var balloon: Balloon? = null

  init {
    UiNotifyConnector(toolbar.component, this).also { Disposer.register(tooltipDisposable, it) }
  }

  override fun showNotify() = showHint()
  override fun hideNotify() = hideHint(false)

  fun showHint() {
    hideBalloon()
    val component = actionComponentSelector(toolbar) ?: return
    GotItTooltip(id, tooltipText, tooltipDisposable)
      .setOnBalloonCreated { balloon = it }
      .show(component, GotItTooltip.BOTTOM_MIDDLE)
  }

  private fun hideBalloon() {
    balloon?.hide()
    balloon = null
  }

  fun hideHint(dispose: Boolean) {
    hideBalloon()
    GotItUsageCollector.instance.logClose(id, GotItUsageCollectorGroup.CloseType.AncestorRemoved)

    if (dispose) Disposer.dispose(tooltipDisposable)
  }
}

internal fun findToolbarActionButton(toolbar: ActionToolbar, condition: (AnAction) -> Boolean): JComponent? {
  return UIUtil.uiTraverser(toolbar.component)
    .filter(ActionButton::class.java)
    .filter { condition(it.action) }
    .first()
}

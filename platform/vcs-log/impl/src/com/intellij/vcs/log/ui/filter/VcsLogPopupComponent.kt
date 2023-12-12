// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.filter

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionGroupUtil
import com.intellij.openapi.actionSystem.impl.Utils
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.util.NlsContexts
import com.intellij.util.ui.FilterComponent
import java.awt.Component
import java.util.function.Supplier

internal abstract class VcsLogPopupComponent(displayName: Supplier<@NlsContexts.Label String>) : FilterComponent(displayName) {
  init {
    setShowPopupAction(Runnable { showPopupMenu() })
  }

  fun showPopupMenu() {
    val start = System.nanoTime()
    val popup = createPopupMenu()
    Utils.showPopupElapsedMillisIfConfigured(start, popup.content)
    popup.showUnderneathOf(getTargetComponent())
  }

  open fun getTargetComponent(): Component = this

  /**
   * Create popup actions available under this filter.
   */
  protected abstract fun createActionGroup(): ActionGroup

  protected open fun createPopupMenu(): ListPopup = JBPopupFactory.getInstance().createActionGroupPopup(
    null,
    ActionGroupUtil.forceRecursiveUpdateInBackground(createActionGroup()),
    DataManager.getInstance().getDataContext(this),
    JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, false)
}
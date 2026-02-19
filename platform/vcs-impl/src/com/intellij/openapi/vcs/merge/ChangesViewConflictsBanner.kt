// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.merge

import com.intellij.openapi.vcs.changes.ui.ChangesListView
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.InlineBanner
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import org.jetbrains.annotations.Nls

internal class ChangesViewConflictsBanner(message: @Nls String, changesView: ChangesListView) : BorderLayoutPanel() {

  private val banner = InlineBanner(message, EditorNotificationPanel.Status.Warning)

  init {
    border = JBUI.Borders.empty(5, 10)
    for (action in ChangesViewConflictsBannerCustomizer.EP_NAME.getExtensions(changesView.project)) {
      if (action.isAvailable(changesView)) {
        banner.addAction(action.name, action.icon, action.createAction(changesView))
      }
    }
    addToCenter(banner)
  }

  fun showCloseButton(show: Boolean): ChangesViewConflictsBanner  {
    banner.showCloseButton(show)
    return this
  }

  fun close() {
    val parent = parent
    parent?.remove(this)
    parent?.doLayout()
    parent?.revalidate()
    parent?.repaint()
  }
}

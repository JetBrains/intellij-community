// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.layout

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.components.dialog
import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.impl.JBEditorTabs

// not easy to replicate the same LaF outside of IDEA app, so, to be sure, showcase available as an IDE action
internal class ShowcaseUiDslAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val disposable = Disposer.newDisposable()
    val tabs = object : JBEditorTabs(e.project, ActionManager.getInstance(), IdeFocusManager.getInstance(e.project), disposable) {
      override fun isAlphabeticalMode(): Boolean {
        return false
      }
    }

    tabs.addTab(TabInfo(secondColumnSmallerPanel()).setText("Second Column Smaller"))

    val dialog = dialog("UI DSL Showcase", tabs)
    Disposer.register(dialog.disposable, disposable)
    dialog.showAndGet()
  }
}

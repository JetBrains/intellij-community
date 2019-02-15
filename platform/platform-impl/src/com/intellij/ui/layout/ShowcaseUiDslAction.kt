// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.layout

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.dialog
import com.intellij.ui.tabs.JBTabsFactory
import com.intellij.ui.tabs.TabInfo

// not easy to replicate the same LaF outside of IDEA app, so, to be sure, showcase available as an IDE action
internal class ShowcaseUiDslAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val disposable = Disposer.newDisposable()
    val tabs = JBTabsFactory.createEditorTabs(e.project, disposable)
    tabs.presentation.setAlphabeticalMode(false)

    tabs.addTab(TabInfo(secondColumnSmallerPanel()).setText("Second Column Smaller"))

    val dialog = dialog("UI DSL Showcase", tabs.component)
    Disposer.register(dialog.disposable, disposable)
    dialog.showAndGet()
  }
}

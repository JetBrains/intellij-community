// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("HardCodedStringLiteral")

package com.intellij.ui.layout

import com.google.common.base.CaseFormat
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.dialog
import com.intellij.ui.tabs.JBTabsFactory
import com.intellij.ui.tabs.TabInfo
import javax.swing.JPanel
import kotlin.reflect.jvm.kotlinFunction

// not easy to replicate the same LaF outside of IDEA app, so, to be sure, showcase available as an IDE action
internal class ShowcaseUiDslAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val disposable = Disposer.newDisposable()
    val tabs = JBTabsFactory.createEditorTabs(e.project, disposable)
    tabs.presentation.setSupportsCompression(false)
    tabs.presentation.setAlphabeticalMode(true)

    val clazz = Class.forName("com.intellij.ui.layout.TestPanelsKt")
    for (declaredMethod in clazz.declaredMethods) {
      val method = declaredMethod.kotlinFunction!!
      if (method.returnType.classifier == JPanel::class && method.parameters.isEmpty()) {
        val panel = method.call() as JPanel
        val text = CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, method.name)
          .replace("_", " ").capitalize().removeSuffix(" Panel")
        tabs.addTab(TabInfo(panel).setText(text))
      }
    }

    tabs.select(tabs.tabs.first(), false)

    val dialog = dialog("UI DSL Showcase", tabs.component, resizable = true)
    Disposer.register(dialog.disposable, disposable)
    dialog.showAndGet()
  }
}

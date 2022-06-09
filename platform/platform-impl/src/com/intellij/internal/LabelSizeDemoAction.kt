// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.components.dialog
import com.intellij.ui.layout.*
import com.intellij.util.ui.JBFont

/**
 * @author Konstantin Bulenkov
 */
internal class LabelSizeDemoAction : DumbAwareAction() {

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    val text = "Lorem ipsum 1234567890"
    val panel = panel {
      row("H0") { label(text, JBFont.h0().asBold()) }
      row("H1") { label(text, JBFont.h1().asBold()) }
      row("H2") { label(text, JBFont.h2()) }
      row("H2 Bold") { label(text, JBFont.h2().asBold()) }
      row("H3") { label(text, JBFont.h3()) }
      row("H3 Bold") { label(text, JBFont.h3().asBold()) }
      row("H4")           {  label(text, JBFont.h4().asBold())  }
      row("Default")      {  label(text, JBFont.regular())  }
      row("Default Bold") {  label(text, JBFont.regular().asBold())  }
      row("Medium")       {  label(text, JBFont.medium())  }
      row("Medium Bold")  {  label(text, JBFont.medium().asBold())  }
      row("Small")        {  label(text, JBFont.small())  }
    }
    dialog("Test Label Sizes", panel).show()
  }
}
// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.sandbox.components

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import com.intellij.internal.ui.sandbox.UISandboxPanel
import com.intellij.internal.ui.sandbox.applyStateText
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.panel
import javax.swing.JButton
import javax.swing.JComponent

@Suppress("DialogTitleCapitalization")
internal class JButtonPanel : UISandboxPanel {

  override val title: String = "JButton"

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      buttonsBlock { }
      buttonsBlock("With Icon") {
        icon = AllIcons.General.GearPlain
      }
      buttonsBlock("ActionToolbar.smallVariant = true") {
        putClientProperty("ActionToolbar.smallVariant", true)
      }
      buttonsBlock("gotItButton = true") {
        putClientProperty("gotItButton", true)
      }
      buttonsBlock("styleTag = true") {
        putClientProperty("styleTag", true)
      }
      buttonsBlock("JButton.buttonType = help") {
        putClientProperty("JButton.buttonType", "help")
      }
    }
  }

  private fun Panel.buttonsBlock(@NlsContexts.BorderTitle title: String, applyToComponent: JButton.() -> Unit) {
    group(title) {
      buttonsBlock(applyToComponent)
    }
  }

  private fun Panel.buttonsBlock(applyToComponent: JButton.() -> Unit) {
    for (isEnabled in listOf(true, false)) {
      row {
        button("") {}
          .enabled(isEnabled)
          .applyToComponent(applyToComponent)
          .applyStateText()
        button("") {}
          .enabled(isEnabled)
          .applyToComponent {
            putClientProperty(DarculaButtonUI.DEFAULT_STYLE_KEY, true)
            applyToComponent()
          }.applyStateText()
      }
    }
  }
}
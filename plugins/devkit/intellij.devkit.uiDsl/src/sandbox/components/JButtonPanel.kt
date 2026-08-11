// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.sandbox.components

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.devkit.uiDsl.sandbox.UISandboxPanel
import com.intellij.devkit.uiDsl.sandbox.applyStateText
import com.intellij.icons.AllIcons
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.icons.toStrokeIcon
import com.intellij.util.ui.JBUI
import javax.swing.JButton
import javax.swing.JComponent

@NlsSafe private const val GOT_IT = "gotItButton = true"
@NlsSafe private const val STYLE_TAG = "styleTag = true"
@NlsSafe private const val BUTTON_HELP = "JButton.buttonType = help"

@Suppress("DialogTitleCapitalization")
internal class JButtonPanel : UISandboxPanel {


  override val title: String = "JButton"

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      buttonsBlock { }
      buttonsBlock(DevkitUiDslBundle.message("sandbox.border.title.with.icon")) {
        icon = AllIcons.General.GearPlain
      }
      buttonsBlock(DevkitUiDslBundle.message("sandbox.border.title.actiontoolbar.smallvariant.true")) {
        putClientProperty("ActionToolbar.smallVariant", true)
      }
      buttonsBlock(GOT_IT) {
        putClientProperty("gotItButton", true)
      }
      buttonsBlock(STYLE_TAG) {
        putClientProperty("styleTag", true)
      }
      buttonsBlock(BUTTON_HELP) {
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
            if (icon != null) {
              icon = toStrokeIcon(icon, JBUI.CurrentTheme.Button.defaultButtonForeground())
            }
          }.applyStateText()
      }
    }
  }
}
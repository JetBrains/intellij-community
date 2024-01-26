// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.sandbox.components

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import com.intellij.internal.ui.sandbox.UISandboxPanel
import com.intellij.openapi.Disposable
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.panel
import javax.swing.JButton
import javax.swing.JComponent

@Suppress("DialogTitleCapitalization")
internal class JButtonPanel : UISandboxPanel {

  override val title: String = "JButton"

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      row {
        button("Enabled") {}
        button("Enabled, default") {}.applyToComponent {
          putClientProperty(DarculaButtonUI.DEFAULT_STYLE_KEY, true)
        }
      }
      row {
        button("Enabled") {}.applyToComponent {
          icon = AllIcons.General.GearPlain
        }
        button("Enabled, default") {}.applyToComponent {
          icon = AllIcons.General.GearPlain
          putClientProperty(DarculaButtonUI.DEFAULT_STYLE_KEY, true)
        }
      }
      row {
        button("Disabled") {}.enabled(false)
        button("Disabled, default") {}.enabled(false)
          .applyToComponent {
            putClientProperty(DarculaButtonUI.DEFAULT_STYLE_KEY, true)
          }
      }
      row {
        button("Disabled") {}.applyToComponent {
          icon = AllIcons.General.GearPlain
        }.enabled(false)
        button("Disabled, default") {}.enabled(false)
          .applyToComponent {
            icon = AllIcons.General.GearPlain
            putClientProperty(DarculaButtonUI.DEFAULT_STYLE_KEY, true)
          }
      }

      stylizedButton("smallVariant") {
        putClientProperty("ActionToolbar.smallVariant", true)
      }
      stylizedButton("gotItButton") {
        putClientProperty("gotItButton", true)
      }
      stylizedButton("styleTag") {
        putClientProperty("styleTag", true)
      }
      stylizedButton("JButton.buttonType = help") {
        putClientProperty("JButton.buttonType", "help")
      }
    }
  }

  private fun Panel.stylizedButton(title: String, applyToComponent: JButton.() -> Unit) {
    group(title) {
      row {
        button("Enabled") {}.applyToComponent(applyToComponent)
        button("Enabled, default") {}.applyToComponent {
          putClientProperty(DarculaButtonUI.DEFAULT_STYLE_KEY, true)
          applyToComponent()
        }
      }
      row {
        button("Disabled") {}
          .enabled(false)
          .applyToComponent(applyToComponent)
        button("Disabled, default") {}
          .enabled(false)
          .applyToComponent {
            putClientProperty(DarculaButtonUI.DEFAULT_STYLE_KEY, true)
            applyToComponent()
          }
      }
    }
  }
}
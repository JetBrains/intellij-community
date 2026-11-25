// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.sandbox.components

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.devkit.uiDsl.sandbox.UISandboxPanel
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

internal class SimpleColoredComponentPanel : UISandboxPanel {

  override val title: String = "SimpleColoredComponent"

  override fun createContent(disposable: Disposable): JComponent {
    val name = DevkitUiDslBundle.message("sandbox.simple.colored.component.text.name")
    val location = DevkitUiDslBundle.message("sandbox.simple.colored.component.text.location")

    return panel {
      row {
        text(DevkitUiDslBundle.message("sandbox.simple.colored.component.description"))
      }

      group(DevkitUiDslBundle.message("sandbox.border.title.states.and.variants")) {
        row {
          text(DevkitUiDslBundle.message("sandbox.simple.colored.component.default.note"))
        }
        row(DevkitUiDslBundle.message("sandbox.simple.colored.component.label.default")) {
          cell(createComponent(name, location))
        }
        row(DevkitUiDslBundle.message("sandbox.simple.colored.component.label.disabled")) {
          cell(createComponent(name, location).apply { isEnabled = false })
        }
        row(DevkitUiDslBundle.message("sandbox.simple.colored.component.label.icon")) {
          cell(createComponent(name, location).apply { icon = AllIcons.FileTypes.Java })
        }
        row(DevkitUiDslBundle.message("sandbox.simple.colored.component.label.bold")) {
          cell(createComponent(name, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES))
        }
        row(DevkitUiDslBundle.message("sandbox.simple.colored.component.label.italic")) {
          cell(createComponent(name, SimpleTextAttributes.REGULAR_ITALIC_ATTRIBUTES))
        }
        row(DevkitUiDslBundle.message("sandbox.simple.colored.component.label.strikeout")) {
          cell(createComponent(name, SimpleTextAttributes(SimpleTextAttributes.STYLE_STRIKEOUT, null)))
        }
        row(DevkitUiDslBundle.message("sandbox.simple.colored.component.label.error")) {
          cell(createComponent(name, SimpleTextAttributes.ERROR_ATTRIBUTES))
        }
        row(DevkitUiDslBundle.message("sandbox.simple.colored.component.label.link")) {
          cell(createComponent(name, SimpleTextAttributes.LINK_ATTRIBUTES))
        }
      }
    }
  }

  private fun createComponent(name: String, location: String): SimpleColoredComponent {
    return SimpleColoredComponent().apply {
      append(name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
      append(" ", SimpleTextAttributes.REGULAR_ATTRIBUTES)
      append(location, SimpleTextAttributes.GRAYED_ATTRIBUTES)
    }
  }

  private fun createComponent(name: String, attributes: SimpleTextAttributes): SimpleColoredComponent {
    return SimpleColoredComponent().apply { append(name, attributes) }
  }
}

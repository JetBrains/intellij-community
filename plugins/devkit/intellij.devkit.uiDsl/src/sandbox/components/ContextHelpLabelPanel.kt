// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.sandbox.components

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.devkit.uiDsl.sandbox.UISandboxPanel
import com.intellij.openapi.Disposable
import com.intellij.ui.ContextHelpLabel
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.annotations.NonNls
import java.net.URI
import javax.swing.JComponent

private const val DOCS_URL: @NonNls String = "https://plugins.jetbrains.com/docs/intellij/context-help.html"

internal class ContextHelpLabelPanel : UISandboxPanel {

  override val title: String = "ContextHelpLabel"

  override fun createContent(disposable: Disposable): JComponent {
    val description = DevkitUiDslBundle.message("sandbox.context.help.label.tooltip.description")
    val title = DevkitUiDslBundle.message("sandbox.context.help.label.tooltip.title")

    return panel {
      row {
        text(DevkitUiDslBundle.message("sandbox.context.help.label.description"))
      }

      group(DevkitUiDslBundle.message("sandbox.border.title.variants")) {
        row {
          text(DevkitUiDslBundle.message("sandbox.context.help.label.default.note"))
        }
        row(DevkitUiDslBundle.message("sandbox.context.help.label.label.description")) {
          cell(ContextHelpLabel.create(description))
        }
        row(DevkitUiDslBundle.message("sandbox.context.help.label.label.title")) {
          cell(ContextHelpLabel.create(title, description))
        }
        row(DevkitUiDslBundle.message("sandbox.context.help.label.label.link")) {
          cell(ContextHelpLabel.createWithBrowserLink(
            title,
            description,
            DevkitUiDslBundle.message("sandbox.context.help.label.tooltip.link"),
            URI(DOCS_URL).toURL(),
          ))
        }
      }

      group(DevkitUiDslBundle.message("sandbox.context.help.label.group.ui.dsl")) {
        row {
          text(DevkitUiDslBundle.message("sandbox.context.help.label.ui.dsl.note"))
        }
        row {
          checkBox(DevkitUiDslBundle.message("sandbox.context.help.label.check.box"))
            .contextHelp(description, title)
        }
      }
    }
  }
}

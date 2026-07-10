// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.sandbox.tests.dsl

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.devkit.uiDsl.sandbox.UISandboxPanel
import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.Messages
import com.intellij.ui.dsl.builder.COLUMNS_LARGE
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.text
import javax.swing.JComponent
import javax.swing.JEditorPane

@Suppress("DialogTitleCapitalization")
internal class OthersPanel : UISandboxPanel {

  override val title: String = "Others"

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      group(DevkitUiDslBundle.message("sandbox.border.title.dsllabel.text.update")) {
        lateinit var dslText: JEditorPane

        row {
          dslText = text(DevkitUiDslBundle.message("sandbox.label.initial.text.with.href.link.link"), action = {
            Messages.showMessageDialog(DevkitUiDslBundle.message("sandbox.dialog.message.link.clicked", it.description),
                                       DevkitUiDslBundle.message("sandbox.dialog.title.message"), null)
          })
            .component
        }
        row {
          val textField = textField()
            .text("New text with <a href='another link'>another link</a><br>Second line")
            .columns(COLUMNS_LARGE)
            .component
          button(DevkitUiDslBundle.message("sandbox.button.update")) {
            @Suppress("HardCodedStringLiteral")
            dslText.text = textField.text
          }
        }
      }

      group(DevkitUiDslBundle.message("sandbox.border.title.size.groups")) {
        row {
          button(DevkitUiDslBundle.message("sandbox.button.button"), {}).widthGroup("group1")
          button(DevkitUiDslBundle.message("sandbox.button.very.long.button"), {}).widthGroup("group1")
        }.rowComment(DevkitUiDslBundle.message("sandbox.text.buttons.with.same.widthgroup"))
      }
    }
  }
}
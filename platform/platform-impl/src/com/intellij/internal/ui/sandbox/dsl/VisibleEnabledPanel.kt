// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.sandbox.dsl

import com.intellij.internal.ui.sandbox.UISandboxPanel
import com.intellij.openapi.Disposable
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.*
import javax.swing.JComponent

@Suppress("DialogTitleCapitalization")
internal class VisibleEnabledPanel : UISandboxPanel {

  override val title: String = "Visible/Enabled"

  override fun createContent(disposable: Disposable): JComponent {
    val entities = mutableMapOf<String, Any>()

    return panel {
      row {
        text("Example test cases:<br>" +
             "1. Hide Group, hide/show sub elements from Group<br>" +
             "  * they shouldn't be visible until Group becomes visible<br>" +
             "  * after Group becomes shown visible state of sub elements correspondent to checkboxes<br>" +
             "2. Similar to 1 test case but with enabled state")
      }

      row {
        panel {
          entities["Row 1"] = row("Row 1") {
            entities["textField1"] = textField()
              .text("textField1")

          }

          entities["Group"] = group("Group") {
            entities["Row 2"] = row("Row 2") {
              entities["textField2"] = textField()
                .text("textField2")
                .comment("Comment with a <a>link</a>")
            }

            entities["Row 3"] = row("Row 3") {
              entities["panel"] = panel {
                row {
                  label("Panel inside row3")
                }

                entities["Row 4"] = row("Row 4") {
                  entities["textField3"] = textField()
                    .text("textField3")
                }
              }
            }
          }
        }.align(AlignY.TOP)

        panel {
          row {
            label("Visible/Enabled")
              .bold()
          }
          for ((name, entity) in entities.toSortedMap()) {
            row(name) {
              checkBox("visible")
                .selected(true)
                .onChanged {
                  when (entity) {
                    is Cell<*> -> entity.visible(it.isSelected)
                    is Row -> entity.visible(it.isSelected)
                    is Panel -> entity.visible(it.isSelected)
                  }
                }
              checkBox("enabled")
                .selected(true)
                .onChanged {
                  when (entity) {
                    is Cell<*> -> entity.enabled(it.isSelected)
                    is Row -> entity.enabled(it.isSelected)
                    is Panel -> entity.enabled(it.isSelected)
                  }
                }
            }
          }
        }.align(AlignX.RIGHT)
      }

      group("Control visibility by visibleIf") {
        lateinit var checkBoxText: Cell<JBCheckBox>
        lateinit var checkBoxRow: Cell<JBCheckBox>

        row {
          checkBoxRow = checkBox("Row")
            .selected(true)
          checkBoxText = checkBox("textField")
            .selected(true)
        }

        row("visibleIf test row") {
          textField()
            .text("textField")
            .visibleIf(checkBoxText.selected)
          label("some label")
        }.visibleIf(checkBoxRow.selected)
      }
    }
  }
}
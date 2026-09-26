// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.sandbox.tests.dsl

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.devkit.uiDsl.sandbox.UISandboxPanel
import com.intellij.openapi.Disposable
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import com.intellij.ui.dsl.builder.text
import javax.swing.JComponent

@Suppress("DialogTitleCapitalization")
internal class VisibleEnabledPanel : UISandboxPanel {

  override val title: String = "Visible/Enabled"

  override fun createContent(disposable: Disposable): JComponent {
    val entities = mutableMapOf<String, Any>()

    return panel {
      row {
        text(DevkitUiDslBundle.message("sandbox.label.example.shown.visible.state.sub.elements"))
      }

      row {
        panel {
          entities["Row 1"] = row(DevkitUiDslBundle.message("sandbox.row.1")) {
            entities["textField1"] = textField()
              .text("textField1")

          }

          entities["Group"] = group(DevkitUiDslBundle.message("sandbox.border.title.group")) {
            entities["Row 2"] = row(DevkitUiDslBundle.message("sandbox.row.2")) {
              entities["textField2"] = textField()
                .text("textField2")
                .commentRight(DevkitUiDslBundle.message("sandbox.text.right.comment.with.link"))
                .comment(DevkitUiDslBundle.message("sandbox.text.comment.with.link"))
            }

            entities["Row 3"] = row(DevkitUiDslBundle.message("sandbox.row.3")) {
              entities["panel"] = panel {
                row {
                  label(DevkitUiDslBundle.message("sandbox.label.panel.inside.row3"))
                }

                entities["Row 4"] = row(DevkitUiDslBundle.message("sandbox.row.4")) {
                  entities["textField3"] = textField()
                    .text("textField3")
                }
              }
            }
          }
        }.align(AlignY.TOP)

        panel {
          row {
            label(DevkitUiDslBundle.message("sandbox.label.visible.enabled"))
              .bold()
          }
          for ((name, entity) in entities.toSortedMap()) {
            row(name) {
              checkBox(DevkitUiDslBundle.message("sandbox.checkbox.visible2"))
                .selected(true)
                .onChanged {
                  when (entity) {
                    is Cell<*> -> entity.visible(it.isSelected)
                    is Row -> entity.visible(it.isSelected)
                    is Panel -> entity.visible(it.isSelected)
                  }
                }
              checkBox(DevkitUiDslBundle.message("sandbox.checkbox.enabled2"))
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

      group(DevkitUiDslBundle.message("sandbox.border.title.control.visibleif.enableif")) {
        lateinit var chRowVisible: Cell<JBCheckBox>
        lateinit var chRowEnabled: Cell<JBCheckBox>
        lateinit var chTextFieldVisible: Cell<JBCheckBox>
        lateinit var chTextFieldEnabled: Cell<JBCheckBox>

        buttonsGroup(DevkitUiDslBundle.message("sandbox.label.row")) {
          row {
            chRowVisible = checkBox(DevkitUiDslBundle.message("sandbox.checkbox.visible"))
              .selected(true)
            chRowEnabled = checkBox(DevkitUiDslBundle.message("sandbox.checkbox.enabled"))
              .selected(true)
          }
        }
        buttonsGroup(DevkitUiDslBundle.message("sandbox.label.text.field")) {
          row {
            chTextFieldVisible = checkBox(DevkitUiDslBundle.message("sandbox.checkbox.visible"))
              .selected(true)
            chTextFieldEnabled = checkBox(DevkitUiDslBundle.message("sandbox.checkbox.enabled"))
              .selected(true)
          }
        }

        row(DevkitUiDslBundle.message("sandbox.visibleif.test.row")) {
          textField()
            .text("textField")
            .commentRight(DevkitUiDslBundle.message("sandbox.text.right.comment.with.link"))
            .comment(DevkitUiDslBundle.message("sandbox.text.comment.with.link"))
            .visibleIf(chTextFieldVisible.selected)
            .enabledIf(chTextFieldEnabled.selected)
          label(DevkitUiDslBundle.message("sandbox.label.some.label"))
        }.visibleIf(chRowVisible.selected)
          .enabledIf(chRowEnabled.selected)
      }
    }
  }
}
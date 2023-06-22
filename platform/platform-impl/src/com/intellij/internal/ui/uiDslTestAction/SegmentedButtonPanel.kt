// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.uiDslTestAction

import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.*
import org.jetbrains.annotations.ApiStatus
import javax.swing.JTextField

@Suppress("DialogTitleCapitalization")
@ApiStatus.Internal
internal class SegmentedButtonPanel(parentDisposable: Disposable) {

  lateinit var panel: DialogPanel

  init {
    panel = panel {
      lateinit var segmentedButton: SegmentedButton<String>
      val tfSelectedItem = JTextField()

      val segmentedButtonRow = row("Segmented Button:") {
        segmentedButton = segmentedButton(generateItems(3), { it })
          .validation {
            addApplyRule("Cannot be empty") { it.selectedItem.isNullOrEmpty() }
          }
          .whenItemSelected { tfSelectedItem.text = segmentedButton.selectedItem }
      }

      row("Property value:") {
        cell(tfSelectedItem)
          .columns(COLUMNS_SHORT)
        button("Change Property") {
          segmentedButton.selectedItem = tfSelectedItem.text
        }
      }

      row("Options count:") {
        val textField = textField()
          .applyToComponent { text = "6" }
          .component
        button("rebuild") {
          textField.text.toIntOrNull()?.let {
            segmentedButton.items(generateItems(it))
          }
        }
      }

      row {
        checkBox("Enabled")
          .selected(true)
          .actionListener { _, component -> segmentedButtonRow.enabled(component.isSelected) }
      }

      row {
        button("Validate") {
          panel.validateAll()
        }
      }

      group("Segmented button without binding") {
        row {
          segmentedButton(generateItems(5), { it })
        }
      }
    }

    panel.registerValidators(parentDisposable)
  }

  private fun generateItems(count: Int): Collection<String> {
    return (1..count).map { "Item $it" }
  }
}

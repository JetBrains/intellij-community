// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal

package com.intellij.devkit.uiDsl.sandbox.tests.dsl.listCellRenderer

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.devkit.uiDsl.sandbox.UISandboxPanel
import com.intellij.devkit.uiDsl.sandbox.intList
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.intellij.ui.layout.selected
import org.jetbrains.annotations.ApiStatus
import java.util.Vector
import javax.swing.DefaultComboBoxModel
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.ListCellRenderer

internal class LcrComboBoxTestPanel : UISandboxPanel {

  override val title: String = "ComboBox"

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      lateinit var enabled: JCheckBox
      row {
        enabled = checkBox(DevkitUiDslBundle.message("sandbox.checkbox.enabled"))
          .selected(true)
          .component
      }
      group("ComboBox") {
        val comboBoxes = mutableListOf<ComboBox<*>>()

        row {
          checkBox(DevkitUiDslBundle.message("sandbox.checkbox.swing.popup"))
            .selected(true)
            .onChanged {
              for (comboBox in comboBoxes) {
                comboBox.isSwingPopup = it.isSelected
              }
            }
        }

        row(DevkitUiDslBundle.message("sandbox.empty")) {
          comboBox(emptyList<String>(), textListCellRenderer { it })
            .addTo(comboBoxes)
        }
        row(DevkitUiDslBundle.message("sandbox.no.selection")) {
          comboBox(listOf("First", "Second", "Last"), textListCellRenderer { it })
            .applyToComponent { selectedItem = null }
            .addTo(comboBoxes)
        }
        row(DevkitUiDslBundle.message("sandbox.few.items.tooltips")) {
          comboBox(listOf("First", "Second", "Try with y", "Try with ()"), listCellRenderer("") {
            toolTipText = value
            @Suppress("HardCodedStringLiteral")
            text(value)
          }).addTo(comboBoxes)
        }
        row(DevkitUiDslBundle.message("sandbox.items.with.icon")) {
          comboBox(intList(), listCellRenderer("") {
            icon(if (value % 2 == 0) AllIcons.General.Information else AllIcons.General.Gear)
            text(DevkitUiDslBundle.message("sandbox.item.0", value))
          }).addTo(comboBoxes)

        }
        row(DevkitUiDslBundle.message("sandbox.long.items")) {
          comboBox((1..100).map { "$it " + "Item".repeat(10) }, textListCellRenderer { it })
            .addTo(comboBoxes)
        }
      }.enabledIf(enabled.selected)

      @Suppress("HardCodedStringLiteral")
      group("JComboBox") {
        row(DevkitUiDslBundle.message("sandbox.empty")) {
          jComboBox(emptyList<String>(), textListCellRenderer { it })
        }
        row(DevkitUiDslBundle.message("sandbox.no.selection")) {
          jComboBox(listOf("First", "Second", "Last"), textListCellRenderer { it })
            .applyToComponent { selectedItem = null }
        }
        row(DevkitUiDslBundle.message("sandbox.few.items")) {
          jComboBox(listOf("First", "Second", "Try with y", "Try with ()"), textListCellRenderer { it })
        }
        row(DevkitUiDslBundle.message("sandbox.items.with.icon")) {
          jComboBox(intList(), listCellRenderer("") {
            icon(if (value % 2 == 0) AllIcons.General.Information else AllIcons.General.Gear)
            text(DevkitUiDslBundle.message("sandbox.item.0", value))
          })
        }
        row(DevkitUiDslBundle.message("sandbox.long.items")) {
          jComboBox(intList(), textListCellRenderer { "$it " + "Item".repeat(10) }).component
        }
      }.enabledIf(enabled.selected)
    }
  }

  private fun <T> Row.jComboBox(items: Collection<T>, renderer: ListCellRenderer<in T?>? = null): Cell<JComboBox<T>> {
    val model = DefaultComboBoxModel<T>(Vector(items))
    return cell(JComboBox(model)).applyToComponent {
      setRenderer(renderer)
    }
  }

  private fun Cell<ComboBox<*>>.addTo(list: MutableList<ComboBox<*>>) {
    list.add(this.component)
  }
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.sandbox.tests.dsl

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.icons.AllIcons
import com.intellij.devkit.uiDsl.sandbox.UISandboxPanel
import com.intellij.devkit.uiDsl.sandbox.items
import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.COLUMNS_TINY
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.SegmentedButton
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import com.intellij.util.text.nullize
import org.jetbrains.annotations.Nls
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JTextField

@Suppress("DialogTitleCapitalization")
internal class SegmentedButtonPanel : UISandboxPanel {

  override val title: String = "Segmented Button"

  private var rendererText: @Nls String? = null
  private var rendererToolTip: @Nls String? = null
  private var rendererIcon = ItemIcon.None
  private var rendererEnabled = true

  override fun createContent(disposable: Disposable): JComponent {
    lateinit var result: DialogPanel
    lateinit var segmentedButton: SegmentedButton<String>
    val tfSelectedItem = JTextField()
    lateinit var tfText: JTextField
    lateinit var tfTooltip: JTextField
    lateinit var cbIcon: ComboBox<ItemIcon>
    lateinit var cbEnabled: JBCheckBox
    val taLogs = JBTextArea()
    val presentations = mutableMapOf<String, SegmentedButton.ItemPresentation>()

    result = panel {
      group(DevkitUiDslBundle.message("sandbox.border.title.segmented.button.test.board")) {
        val segmentedButtonRow = row(DevkitUiDslBundle.message("sandbox.segmented.button")) {
          segmentedButton = segmentedButton(items(3)) {
            text = rendererText ?: it
            toolTipText = rendererToolTip
            icon = rendererIcon.icon
            enabled = rendererEnabled

            presentations[it] = this
          }.validation {
            addApplyRule(DevkitUiDslBundle.message("sandbox.dialog.message.cannot.be.empty")) { it.selectedItem.isNullOrEmpty() }
          }.whenItemSelected {
            @Suppress("HardCodedStringLiteral")
            taLogs.append("whenItemSelected: selectedItem = ${segmentedButton.selectedItem}\n")
            tfSelectedItem.text = segmentedButton.selectedItem

            val presentation = presentations[it]!!
            tfText.text = presentation.text
            tfTooltip.text = presentation.toolTipText
            cbIcon.selectedItem = ItemIcon.entries.find { itemIcon -> itemIcon.icon == presentation.icon } ?: ItemIcon.None
            cbEnabled.isSelected = presentation.enabled
          }
        }.bottomGap(BottomGap.SMALL)

        row {
          panel {
            row(DevkitUiDslBundle.message("sandbox.selected.item")) {
              cell(tfSelectedItem)
                .columns(10)
              button(DevkitUiDslBundle.message("sandbox.button.set")) {
                segmentedButton.selectedItem = tfSelectedItem.text.nullize()
              }
            }

            row {
              text(DevkitUiDslBundle.message("sandbox.label.selected.item.props"))
            }

            indent {
              row(DevkitUiDslBundle.message("sandbox.text")) {
                tfText = textField()
                  .columns(10)
                  .component
              }
              row(DevkitUiDslBundle.message("sandbox.tooltip")) {
                tfTooltip = textField()
                  .columns(10)
                  .component
              }
              row(DevkitUiDslBundle.message("sandbox.icon")) {
                cbIcon = comboBox(ItemIcon.entries.toList())
                  .component
              }
              row {
                cbEnabled = checkBox(DevkitUiDslBundle.message("sandbox.checkbox.enabled"))
                  .component
              }
              row {
                button(DevkitUiDslBundle.message("sandbox.button.set")) {
                  @Suppress("HardCodedStringLiteral")
                  rendererText = tfText.text
                  @Suppress("HardCodedStringLiteral")
                  rendererToolTip = tfTooltip.text
                  rendererIcon = cbIcon.item
                  rendererEnabled = cbEnabled.isSelected
                  segmentedButton.update(tfSelectedItem.text)
                }
              }
            }
          }.align(AlignY.TOP)
            .gap(RightGap.COLUMNS)
          panel {
            row {
              text(DevkitUiDslBundle.message("sandbox.label.segmented.button.props"))
            }
            indent {
              row(DevkitUiDslBundle.message("sandbox.options.count")) {
                val textField = textField()
                  .columns(COLUMNS_TINY)
                  .applyToComponent { text = "6" }
                  .component
                button(DevkitUiDslBundle.message("sandbox.button.rebuild")) {
                  val oldRendererText = rendererText
                  val oldRendererToolTip = rendererToolTip
                  val oldRendererIcon = rendererIcon
                  val oldRendererEnabled = rendererEnabled
                  rendererText = null
                  rendererToolTip = null
                  rendererIcon = ItemIcon.None
                  rendererEnabled = true

                  textField.text.toIntOrNull()?.let {
                    segmentedButton.items = items(it)
                  }

                  rendererText = oldRendererText
                  rendererToolTip = oldRendererToolTip
                  rendererIcon = oldRendererIcon
                  rendererEnabled = oldRendererEnabled
                }
              }
              row {
                checkBox(DevkitUiDslBundle.message("sandbox.checkbox.enabled"))
                  .selected(true)
                  .onChanged { segmentedButtonRow.enabled(it.isSelected) }
              }

              row {
                button(DevkitUiDslBundle.message("sandbox.button.validate.not.empty")) {
                  result.validateAll()
                }
              }
            }
          }.align(AlignY.TOP)
          panel {
            row {
              text(DevkitUiDslBundle.message("sandbox.label.logs"))
            }
            row {
              scrollCell(taLogs)
                .align(Align.FILL)
            }.resizableRow()
          }.align(AlignY.FILL)
        }
      }

      group(DevkitUiDslBundle.message("sandbox.border.title.segmented.button.without.binding")) {
        row {
          segmentedButton(items(5)) { text = it }
        }
      }
    }

    result.registerValidators(disposable)
    return result
  }
}

@Suppress("unused")
private enum class ItemIcon(val icon: Icon?) {
  None(null),
  Settings(AllIcons.General.Settings),
  ExternalTools(AllIcons.General.ExternalTools),
  OpenDisk(AllIcons.General.OpenDisk),
}

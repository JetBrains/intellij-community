// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.sandbox.dsl

import com.intellij.icons.AllIcons
import com.intellij.internal.ui.sandbox.UISandboxPanel
import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.dsl.builder.*
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
      group("Segmented button test board") {
        val segmentedButtonRow = row("Segmented button:") {
          segmentedButton = segmentedButton(generateItems(3)) {
            text = rendererText ?: it
            toolTipText = rendererToolTip
            icon = rendererIcon.icon
            enabled = rendererEnabled

            presentations[it] = this
          }.validation {
            addApplyRule("Cannot be empty") { it.selectedItem.isNullOrEmpty() }
          }.whenItemSelected {
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
            row("Selected item:") {
              cell(tfSelectedItem)
                .columns(10)
              button("Set") {
                segmentedButton.selectedItem = tfSelectedItem.text.nullize()
              }
            }

            row {
              text("Selected item props")
            }

            indent {
              row("Text:") {
                tfText = textField()
                  .columns(10)
                  .component
              }
              row("Tooltip:") {
                tfTooltip = textField()
                  .columns(10)
                  .component
              }
              row("Icon:") {
                cbIcon = comboBox(ItemIcon.entries.toList())
                  .component
              }
              row {
                cbEnabled = checkBox("Enabled")
                  .component
              }
              row {
                button("Set") {
                  rendererText = tfText.text
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
              text("Segmented button props")
            }
            indent {
              row("Options count:") {
                val textField = textField()
                  .columns(COLUMNS_TINY)
                  .applyToComponent { text = "6" }
                  .component
                button("Rebuild") {
                  val oldRendererText = rendererText
                  val oldRendererToolTip = rendererToolTip
                  val oldRendererIcon = rendererIcon
                  val oldRendererEnabled = rendererEnabled
                  rendererText = null
                  rendererToolTip = null
                  rendererIcon = ItemIcon.None
                  rendererEnabled = true

                  textField.text.toIntOrNull()?.let {
                    segmentedButton.items = generateItems(it)
                  }

                  rendererText = oldRendererText
                  rendererToolTip = oldRendererToolTip
                  rendererIcon = oldRendererIcon
                  rendererEnabled = oldRendererEnabled
                }
              }
              row {
                checkBox("Enabled")
                  .selected(true)
                  .onChanged { segmentedButtonRow.enabled(it.isSelected) }
              }

              row {
                button("Validate not empty") {
                  result.validateAll()
                }
              }
            }
          }.align(AlignY.TOP)
          panel {
            row {
              text("Logs")
            }
            row {
              scrollCell(taLogs)
                .align(Align.FILL)
            }.resizableRow()
          }.align(AlignY.FILL)
        }
      }

      group("Segmented button without binding") {
        row {
          segmentedButton(generateItems(5)) { text = it }
        }
      }
    }

    result.registerValidators(disposable)
    return result
  }

  private fun generateItems(count: Int): Collection<String> {
    return (1..count).map { "Item $it" }
  }
}

@Suppress("unused")
private enum class ItemIcon(val icon: Icon?) {
  None(null),
  Settings(AllIcons.General.Settings),
  ExternalTools(AllIcons.General.ExternalTools),
  OpenDisk(AllIcons.General.OpenDisk),
}

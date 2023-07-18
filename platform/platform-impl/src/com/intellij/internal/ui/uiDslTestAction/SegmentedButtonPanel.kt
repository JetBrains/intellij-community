// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.uiDslTestAction

import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.JTextField

@Suppress("DialogTitleCapitalization")
@ApiStatus.Internal
internal class SegmentedButtonPanel(parentDisposable: Disposable) {

  lateinit var panel: DialogPanel

  private var rendererText: @Nls String? = null
  private var rendererToolTip: @Nls String? = null

  init {
    panel = panel {
      group("Segmented button test board") {
        lateinit var segmentedButton: SegmentedButton<String>
        val tfSelectedItem = JTextField()

        val segmentedButtonRow = row {
          segmentedButton = segmentedButton(generateItems(3)) {
            text = rendererText ?: it
            toolTip = rendererToolTip
          }.validation {
            addApplyRule("Cannot be empty") { it.selectedItem.isNullOrEmpty() }
          }.whenItemSelected {
            tfSelectedItem.text = segmentedButton.selectedItem
          }
        }.bottomGap(BottomGap.SMALL)

        row {
          panel {
            row("Selected item:") {
              cell(tfSelectedItem)
                .columns(10)
              button("Set") {
                segmentedButton.selectedItem = tfSelectedItem.text
              }
            }

            row {
              text("Selected item props")
            }

            indent {
              lateinit var tfText: JTextField
              lateinit var tfTooltip: JTextField

              row("Text:") {
                tfText = textField()
                  .text("Text!")
                  .columns(10)
                  .component
              }
              row("Tooltip:") {
                tfTooltip = textField()
                  .text("Tooltip!")
                  .columns(10)
                  .component
              }
              row {
                button("Set") {
                  rendererText = tfText.text
                  rendererToolTip = tfTooltip.text
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
                  rendererText = null
                  rendererToolTip = null

                  textField.text.toIntOrNull()?.let {
                    segmentedButton.items = generateItems(it)
                  }

                  rendererText = oldRendererText
                  rendererToolTip = oldRendererToolTip
                }
              }
              row {
                checkBox("Enabled")
                  .selected(true)
                  .onChanged { segmentedButtonRow.enabled(it.isSelected) }
              }

              row {
                button("Validate not empty") {
                  panel.validateAll()
                }
              }
            }
          }.align(AlignY.TOP)
        }
      }

      group("Segmented button without binding") {
        row {
          segmentedButton(generateItems(5)) { text = it }
        }
      }
    }

    panel.registerValidators(parentDisposable)
  }

  private fun generateItems(count: Int): Collection<String> {
    return (1..count).map { "Item $it" }
  }
}

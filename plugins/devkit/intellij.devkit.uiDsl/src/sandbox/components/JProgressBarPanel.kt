// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.sandbox.components

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.devkit.uiDsl.sandbox.UISandboxPanel
import com.intellij.ide.setToolTipText
import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.util.ProgressBarUtil
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.JBColor
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import com.intellij.ui.layout.selected
import javax.swing.JComponent
import javax.swing.JProgressBar
import javax.swing.JSlider

internal class JProgressBarPanel : UISandboxPanel {

  override val title: String = "JProgressBar"

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      group(DevkitUiDslBundle.message("sandbox.border.title.determinate")) {
        row {
          progressBar()
        }
        row(DevkitUiDslBundle.message("sandbox.passed")) {
          progressBar().applyToComponent {
            putClientProperty(ProgressBarUtil.STATUS_KEY, ProgressBarUtil.PASSED_VALUE)
          }
        }
        row(DevkitUiDslBundle.message("sandbox.warning")) {
          progressBar().applyToComponent {
            putClientProperty(ProgressBarUtil.STATUS_KEY, ProgressBarUtil.WARNING_VALUE)
          }
        }
        row(DevkitUiDslBundle.message("sandbox.failed")) {
          progressBar().applyToComponent {
            putClientProperty(ProgressBarUtil.STATUS_KEY, ProgressBarUtil.FAILED_VALUE)
          }
        }
      }
      group(DevkitUiDslBundle.message("sandbox.border.title.indeterminate")) {
        row {
          indProgressBar()
        }
        row(DevkitUiDslBundle.message("sandbox.passed")) {
          indProgressBar().applyToComponent {
            putClientProperty(ProgressBarUtil.STATUS_KEY, ProgressBarUtil.PASSED_VALUE)
          }
        }
        row(DevkitUiDslBundle.message("sandbox.warning")) {
          indProgressBar().applyToComponent {
            putClientProperty(ProgressBarUtil.STATUS_KEY, ProgressBarUtil.WARNING_VALUE)
          }
        }
        row(DevkitUiDslBundle.message("sandbox.failed")) {
          indProgressBar().applyToComponent {
            putClientProperty(ProgressBarUtil.STATUS_KEY, ProgressBarUtil.FAILED_VALUE)
          }
        }
      }
      group(DevkitUiDslBundle.message("sandbox.border.title.custom")) {
        lateinit var progressBar: JProgressBar
        row {
          panel {
            row(DevkitUiDslBundle.message("sandbox.value")) {
              intTextField()
                .applyToComponent { text = "30" }
                .onChanged {
                  progressBar.value = it.text.toIntOrNull() ?: 30
                }
            }
            row {
              checkBox(DevkitUiDslBundle.message("sandbox.checkbox.horizontal"))
                .selected(true)
                .onChanged {
                  progressBar.orientation = if (it.isSelected) JProgressBar.HORIZONTAL else JProgressBar.VERTICAL
                }
            }
            row {
              checkBox(DevkitUiDslBundle.message("sandbox.checkbox.indeterminate"))
                .onChanged {
                  progressBar.isIndeterminate = it.isSelected
                }
            }
            row {
              lateinit var slider: JSlider

              fun setCustomPaint() {
                val quantityAndColors = listOf(slider.value to JBColor.GREEN, (100 - slider.value) to JBColor.BLUE)
                progressBar.putClientProperty(ProgressBarUtil.PROGRESS_PAINT_KEY,
                                              ProgressBarUtil.createMultiProgressPaint(quantityAndColors))
                progressBar.repaint()
              }

              val customPaint = checkBox(DevkitUiDslBundle.message("sandbox.checkbox.custom.progress.paint"))
                .onChanged {
                  if (it.isSelected) {
                    setCustomPaint()
                  }
                  else {
                    progressBar.putClientProperty(ProgressBarUtil.PROGRESS_PAINT_KEY, null)
                    progressBar.repaint()
                  }
                }.component
              slider = slider(0, 100, 5, 10)
                .applyToComponent { paintLabels = false }
                .onChanged {
                  setCustomPaint()
                }.enabledIf(customPaint.selected)
                .component
            }
          }

          progressBar = progressBar()
            .align(Align.FILL)
            .component
        }
      }
    }
  }

  private fun Row.progressBar(value: Int = 30, horizontal: Boolean = true): Cell<JProgressBar> {
    return cell(JProgressBar(if (horizontal) JProgressBar.HORIZONTAL else JProgressBar.VERTICAL)).applyToComponent {
      addChangeListener {
        setToolTipText(HtmlChunk.text(DevkitUiDslBundle.message("sandbox.0.from.1.2.range", value, minimum, maximum)))
      }

      setValue(value)
    }
  }

  private fun Row.indProgressBar(value: Int = 30, horizontal: Boolean = true): Cell<JProgressBar> {
    return progressBar(value, horizontal).applyToComponent {
      isIndeterminate = true
    }
  }
}
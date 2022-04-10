package com.intellij.cce.dialog

import com.intellij.cce.EvaluationPluginBundle
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.layout.*
import javax.swing.JComponent

class OpenBrowserDialog(private val reportNames: List<String>) : DialogWrapper(true) {
  val reportNamesForOpening = mutableSetOf<String>()

  init {
    init()
    title = EvaluationPluginBundle.message("evaluation.completed.open.browser.title")
  }

  override fun createCenterPanel(): JComponent {
    if (reportNames.size == 1) {
      reportNamesForOpening.add(reportNames.first())
      return panel {
        row { label(EvaluationPluginBundle.message("evaluation.completed.open.browser.text")) }
      }
    } else {
      return panel {
        row { label(EvaluationPluginBundle.message("evaluation.completed.open.browser.select")) }
        for (reportName in reportNames) {
          row {
            checkBox(reportName).apply {
              component.addItemListener {
                if (component.isSelected) reportNamesForOpening.add(component.text)
                else reportNamesForOpening.remove(component.text)
              }
            }
          }
        }
      }
    }
  }
}
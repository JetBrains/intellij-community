package com.intellij.smartUpdate

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel

class SmartUpdateDialog(private val project: Project) : DialogWrapper(project) {
  init {
    title = SmartUpdateBundle.message("dialog.title.smart.update")
    init()
  }

  override fun createCenterPanel(): DialogPanel {
    val smartUpdate = project.service<SmartUpdate>()
    val options = smartUpdate.state
    return panel {
      for (step in smartUpdate.availableSteps()) {
        row {
          checkBox(step.stepName).enabled(step.isEnabled(project)).bindSelected(options.property(step.id))
        }
        val optionsPanel = step.getOptionsPanel(project)
        if (optionsPanel != null) {
          indent { row { cell(optionsPanel) } }
        }
      }
    }
  }
}
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
        val enabled = step.isEnabled(project)
        row {
          checkBox(step.stepName).enabled(enabled).bindSelected(options.property(step.id))
        }
        step.getDetailsComponent(project)?.let {
          indent { row { cell(it).enabled(enabled) } }
        }
      }
    }
  }
}
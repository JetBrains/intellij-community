package com.intellij.smartUpdate

import com.intellij.CommonBundle
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.*
import javax.swing.JSpinner
import javax.swing.SpinnerDateModel

class SmartUpdateDialog(private val project: Project) : DialogWrapper(project) {
  init {
    title = SmartUpdateBundle.message("dialog.title.smart.update")
    setOKButtonText("Update Now")
    setCancelButtonText(CommonBundle.getCloseButtonText())
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
      separator()
      lateinit var scheduled: Cell<JBCheckBox>
      row { scheduled = checkBox(SmartUpdateBundle.message("checkbox.schedule.update")).bindSelected({ options.scheduled }, { options.scheduled = it}) }
      indent {
        val spinner = JSpinner(SpinnerDateModel().apply { value = getScheduled(options.scheduledTime) })
        spinner.editor = JSpinner.DateEditor(spinner, "hh:mm")
        row {
          label(SmartUpdateBundle.message("label.every.day.at"))
          cell(spinner)
        }.enabledIf(scheduled.selected)
      }
    }
  }

  private fun getScheduled(time: LocalTime): Date {
    val instant: Instant = time.atDate(LocalDate.now()).atZone(ZoneId.systemDefault()).toInstant()
    return Date.from(instant)
  }
}
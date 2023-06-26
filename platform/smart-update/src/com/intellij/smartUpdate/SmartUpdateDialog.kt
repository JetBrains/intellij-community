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
import java.time.LocalTime
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

class SmartUpdateDialog(private val project: Project) : DialogWrapper(project) {
  init {
    title = SmartUpdateBundle.message("dialog.title.smart.update")
    setOKButtonText(SmartUpdateBundle.message("button.update.now"))
    setCancelButtonText(CommonBundle.getCloseButtonText())
    init()
  }

  override fun createCenterPanel(): DialogPanel {
    val smartUpdate = project.service<SmartUpdate>()
    val options = smartUpdate.state
    return panel {
      for (step in smartUpdate.availableSteps()) {
        lateinit var checkbox: Cell<JBCheckBox>
        row {
          checkbox = checkBox(step.stepName).bindSelected(options.property(step.id))
        }
        step.getDetailsComponent(project)?.let {
          indent { row { cell(it) } }.visibleIf(checkbox.selected)
        }
      }
      separator()
      lateinit var scheduled: Cell<JBCheckBox>
      row { scheduled = checkBox(SmartUpdateBundle.message("checkbox.schedule.update")).bindSelected({ options.scheduled }, { options.scheduled = it}) }
      indent {
        val time = LocalTime.ofSecondOfDay(options.scheduledTime.toLong())
        val hour = SpinnerNumberModel(time.hour, 0, 23, 1)
        val minute = SpinnerNumberModel(time.minute, 0, 59, 1)

        row {
          label(SmartUpdateBundle.message("label.every.day.at"))
          cell(JSpinner(hour).apply { editor = JSpinner.NumberEditor(this, "##")})
          @Suppress("DialogTitleCapitalization")
          label(SmartUpdateBundle.message("label.hours"))
          cell(JSpinner(minute).apply { editor = JSpinner.NumberEditor(this, "##")})
            .onApply { options.scheduledTime = LocalTime.of(hour.number.toInt(), minute.number.toInt()).toSecondOfDay() }
          @Suppress("DialogTitleCapitalization")
          label(SmartUpdateBundle.message("label.minutes"))
        }.enabledIf(scheduled.selected)
      }
    }
  }

  override fun doCancelAction() {
    applyFields()
    super.doCancelAction()
  }
}
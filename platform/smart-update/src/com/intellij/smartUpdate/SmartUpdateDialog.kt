package com.intellij.smartUpdate

import com.intellij.CommonBundle
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import com.intellij.ui.layout.and
import com.intellij.ui.layout.selectedValueIs
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.util.minimumWidth
import com.intellij.util.ui.UIUtil
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.swing.JFormattedTextField
import javax.swing.text.MaskFormatter

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
    val steps = smartUpdate.availableSteps()
    val groups = steps.groupBy { (it as? StepOption)?.groupName ?: it.stepName }
    return panel {
      for (group in groups) {
        lateinit var checkbox: Cell<JBCheckBox>
        lateinit var combobox: Cell<ComboBox<SmartUpdateStep>>
        row {
          checkbox = checkBox(group.key)
          combobox = comboBox(group.value, SimpleListCellRenderer.create("") { (it as? StepOption)?.optionName })
            .visible(group.value.size > 1)
        }
        for (step in group.value) {
          step.getDetailsComponent(project)?.let {
            indent { row { cell(it) } }.visibleIf(checkbox.selected.and(combobox.component.selectedValueIs(step)).and(step.detailsVisible(project)))
          }
        }
        combobox.component.selectedItem = group.value.find { options.value(it.id) } ?: group.value.first()
        checkbox.component.isSelected = options.value((combobox.component.selectedItem as SmartUpdateStep).id)
        combobox.onApply {
          group.value.forEach { options.property(it.id).set(checkbox.component.isSelected && it == combobox.component.item) }
        }
      }
      separator()
      lateinit var scheduled: Cell<JBCheckBox>
      row { scheduled = checkBox(SmartUpdateBundle.message("checkbox.schedule.update")).bindSelected({ options.scheduled }, { options.scheduled = it}) }
      indent {
        val time = LocalTime.ofSecondOfDay(options.scheduledTime.toLong())
        val formatter = DateTimeFormatter.ofPattern("HH:mm")
        row {
          label(SmartUpdateBundle.message("label.every.day.at"))
          val field = JFormattedTextField(MaskFormatter("##:##").apply { placeholderCharacter = '0'}).apply { text = time.format(formatter) }
          cell(field).onApply {
            try {
              options.scheduledTime = LocalTime.parse(field.text, formatter).toSecondOfDay()
            }
            catch (e: Exception) {
              Logger.getInstance(SmartUpdateDialog::class.java).error(e)
            }
          }
        }.enabledIf(scheduled.selected)
      }
    }.apply { minimumWidth = JBUIScale.scale(300) }
  }

  override fun doCancelAction() {
    applyFields()
    super.doCancelAction()
  }
}

fun hintLabel(@NlsContexts.Label text: String): JBLabel = JBLabel(text).apply {
  foreground = UIUtil.getContextHelpForeground()
}
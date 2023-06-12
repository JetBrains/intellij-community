package com.intellij.smartUpdate

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.selected
import com.intellij.util.ui.JBFont

class SmartUpdateDialog(private val project: Project) : DialogWrapper(project) {
  init {
    title = SmartUpdateBundle.message("dialog.title.smart.update")
    init()
  }

  override fun createCenterPanel(): DialogPanel {
    val options = project.service<SmartUpdate>().state
    val ideUpdateAvailable = IdeUpdateStep().isAvailable()
    lateinit var updateCheckBox: JBCheckBox
    return panel {
      row {
        updateCheckBox = checkBox(SmartUpdateBundle.message("checkbox.update.ide")).bindSelected({ ideUpdateAvailable && options.value(IDE_UPDATE) },
                                                                                                 { if (ideUpdateAvailable) options.property(IDE_UPDATE).set(it) }).component
        updateCheckBox.isEnabled = ideUpdateAvailable
      }
      indent {
        row {
          label(IdeUpdateStep().getDescription()).component.font = JBFont.smallOrNewUiMedium()
        }
        row {
          val restartCheckBox = checkBox(SmartUpdateBundle.message("checkbox.switch.to.updated.ide.restart.required")).bindSelected(
            { ideUpdateAvailable && options.value(IDE_UPDATE) && options.value(IDE_RESTART) },
            { if (ideUpdateAvailable && options.value(IDE_UPDATE)) options.property(IDE_RESTART).set(it) }).component
          updateCheckBox.addActionListener { restartCheckBox.isEnabled = updateCheckBox.isSelected }
        }
      }.enabledIf(updateCheckBox.selected)
      row {
        checkBox(SmartUpdateBundle.message("checkbox.update.project")).bindSelected(options.property(VCS_UPDATE))
      }
      row {
        checkBox(SmartUpdateBundle.message("checkbox.build.project")).bindSelected(options.property(BUILD_PROJECT))
      }
    }
  }
}
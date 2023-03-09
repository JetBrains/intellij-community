package com.intellij.smartUpdate

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.layout.*
import com.intellij.util.ui.JBFont

class SmartUpdateDialog(private val project: Project) : DialogWrapper(project) {
  init {
    title = SmartUpdateBundle.message("dialog.title.smart.update")
    init()
  }

  override fun createCenterPanel(): DialogPanel {
    val options = project.service<SmartUpdate>().state
    val ideUpdateAvailable = IdeUpdateStep().isAvailable()
    var updateCheckBox: JBCheckBox
    return panel {
      row {
        updateCheckBox = checkBox(SmartUpdateBundle.message("checkbox.update.ide"),
                                  { ideUpdateAvailable && options.updateIde },
                                  { if (ideUpdateAvailable) options.updateIde = it }).component
        updateCheckBox.isEnabled = ideUpdateAvailable
        row {
          label(IdeUpdateStep().getDescription(), JBFont.smallOrNewUiMedium()).enabled(ideUpdateAvailable)
        }
        row {
          val restartCheckBox = checkBox(SmartUpdateBundle.message("checkbox.switch.to.updated.ide.restart.required"),
                                     { ideUpdateAvailable && options.updateIde && options.restartIde },
                                     { if (ideUpdateAvailable && options.updateIde) options.restartIde = it }).component
          restartCheckBox.isEnabled = ideUpdateAvailable
          updateCheckBox.addActionListener { restartCheckBox.isEnabled = updateCheckBox.isSelected }
        }
      }
      row {
        checkBox(SmartUpdateBundle.message("checkbox.update.project"),
          { options.updateProject },
          { options.updateProject = it })
      }
      row {
        checkBox(SmartUpdateBundle.message("checkbox.build.project"),
          { options.buildProject },
          { options.buildProject = it })
      }
    }
  }
}
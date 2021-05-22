// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.actions.updateFromSources

import com.intellij.CommonBundle
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.TextFieldWithHistoryWithBrowseButton
import com.intellij.ui.UIBundle
import com.intellij.ui.layout.*
import com.intellij.util.io.exists
import com.intellij.util.io.isWritable
import org.jetbrains.idea.devkit.DevKitBundle
import java.awt.event.ActionEvent
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.AbstractAction
import javax.swing.Action

class UpdateFromSourcesDialog(private val project: Project,
                              private val showApplyButton: Boolean) : DialogWrapper(project, true) {
  private lateinit var panel: DialogPanel
  private lateinit var pathField: TextFieldWithHistoryWithBrowseButton
  private val state = UpdateFromSourcesSettingsState().apply {
    copyFrom(UpdateFromSourcesSettings.getState())
  }

  init {
    title = DevKitBundle.message("action.UpdateIdeFromSourcesAction.settings.title")
    setOKButtonText(DevKitBundle.message("action.UpdateIdeFromSourcesAction.settings.ok.button"))
    init()
  }

  override fun createCenterPanel(): DialogPanel {
    panel = panel {
      row(DevKitBundle.message("action.UpdateIdeFromSourcesAction.settings.row.ide.installation")) {
        pathField = textFieldWithHistoryWithBrowseButton({ state.actualIdePath },
                                                         { state.workIdePath = it },
                                                         DevKitBundle.message("action.UpdateIdeFromSourcesAction.settings.installation.choose.ide.directory.title"),
                                                         project,
                                                         FileChooserDescriptorFactory.createSingleFolderDescriptor(),
                                                         { state.workIdePathsHistory }).component
      }
      row {
        checkBox(DevKitBundle.message("action.UpdateIdeFromSourcesAction.settings.enabled.plugins.only"), { !state.buildDisabledPlugins }, { state.buildDisabledPlugins = !it })
      }
      row {
        checkBox(DevKitBundle.message("action.UpdateIdeFromSourcesAction.settings.restart.automatically"),
                 { state.restartAutomatically }, { state.restartAutomatically = it })
          .visibleIf(pathField.childComponent.textEditor.enteredTextSatisfies { FileUtil.pathsEqual(it, PathManager.getHomePath()) })
      }
      row {
        checkBox(UIBundle.message("dialog.options.do.not.show"), { !state.showSettings }, { state.showSettings = !it },
                 DevKitBundle.message("action.UpdateIdeFromSourcesAction.settings.do.not.show.description"))
      }
    }
    return panel
  }

  override fun doValidate(): ValidationInfo? {
    val outputPath = Paths.get(pathField.text)
    val existingParent = generateSequence(outputPath, Path::getParent).firstOrNull { it.exists() }
    if (existingParent == null || !existingParent.isWritable) {
      return ValidationInfo(DevKitBundle.message("action.UpdateIdeFromSourcesAction.dialog.message.directory.not.writable", outputPath), pathField.childComponent)
    }
    return null
  }

  override fun doOKAction() {
    applyChanges()
    super.doOKAction()
  }

  override fun createActions(): Array<Action> {
    if (showApplyButton) {
      val applyAction = object : AbstractAction(CommonBundle.getApplyButtonText()) {
        override fun actionPerformed(e: ActionEvent?) {
          applyChanges()
          close(NEXT_USER_EXIT_CODE)
        }
      }
      return arrayOf(okAction, applyAction, cancelAction)
    }
    return super.createActions()
  }

  private fun applyChanges() {
    panel.apply()
    service<UpdateFromSourcesSettings>().loadState(state)
  }
}
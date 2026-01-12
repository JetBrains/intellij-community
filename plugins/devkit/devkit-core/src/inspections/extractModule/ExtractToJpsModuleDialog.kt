// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.extractModule

import com.intellij.ide.JavaUiBundle
import com.intellij.ide.projectView.impl.ModuleNameValidator
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.idea.devkit.DevKitBundle
import javax.swing.JComponent

internal class ExtractToJpsModuleDialog(private val originalData: ExtractToContentModuleData) : DialogWrapper(originalData.originalModule.project, true) {
  private val project = originalData.originalModule.project
  private val validator = ModuleNameValidator(project)
  internal var moduleName = originalData.newModuleName
    private set
  private var moduleDirectoryPath = originalData.newModuleDirectoryPath
  private lateinit var panel: DialogPanel
  private lateinit var nameField: JBTextField
  private lateinit var pathField: TextFieldWithBrowseButton

  init {
    title = DevKitBundle.message("dialog.extract.to.jps.module.title")
    setOKButtonText(JavaUiBundle.message("button.text.extract.module"))
    init()
  }

  override fun createCenterPanel(): JComponent {
    panel = panel {
      row(JavaUiBundle.message("dialog.message.module.name")) {
        nameField = textField()
          .bindText(::moduleName)
          .validationOnInput { validator.getErrorText(nameField.text)?.let { error(it) } }
          .align(AlignX.FILL)
          .component
      }
      row {
        label(DevKitBundle.message("dialog.extract.to.jps.module.path.label"))
        pathField = textFieldWithBrowseButton(
          FileChooserDescriptorFactory.singleDir().withTitle(DevKitBundle.message("dialog.extract.to.jps.module.path.chooser.title")),
          project
        )
          .bindText(::moduleDirectoryPath)
          .align(AlignX.FILL)
          .component
      }
      row {
        comment(JavaUiBundle.message("dialog.comment.compile.modules"))
      }
    }
    return panel
  }

  fun showAndGetResult(): ExtractToContentModuleData? {
    if (!showAndGet()) return null
    return originalData.copy(newModuleName = moduleName, newModuleDirectoryPath = moduleDirectoryPath)
  }
}

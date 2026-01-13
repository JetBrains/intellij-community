// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.extractModule

import com.intellij.ide.JavaUiBundle
import com.intellij.ide.projectView.impl.ModuleNameValidator
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.idea.devkit.DevKitBundle
import javax.swing.JComponent

internal class ExtractToJpsModuleDialog(private val originalData: ExtractToContentModuleData) : DialogWrapper(originalData.originalModule.project, true) {
  private val project = originalData.originalModule.project
  private val validator = ModuleNameValidator(project)
  private var moduleName = originalData.newModuleName
  private var moduleDirectoryPath = originalData.newModuleDirectoryPath

  init {
    title = DevKitBundle.message("dialog.extract.to.jps.module.title")
    setOKButtonText(JavaUiBundle.message("button.text.extract.module"))
    init()
  }

  override fun createCenterPanel(): JComponent {
    return panel {
      row(JavaUiBundle.message("dialog.message.module.name")) {
        textField()
          .bindText(::moduleName)
          .validationOnInput { validator.getErrorText(it.text)?.let { errorText -> error(errorText) } }
          .align(AlignX.FILL)
          .component
      }
      row {
        label(DevKitBundle.message("dialog.extract.to.jps.module.path.label"))
        textFieldWithBrowseButton(
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
  }

  fun showAndGetResult(): ExtractToContentModuleData? {
    if (!showAndGet()) return null
    return originalData.copy(newModuleName = moduleName, newModuleDirectoryPath = moduleDirectoryPath)
  }
}

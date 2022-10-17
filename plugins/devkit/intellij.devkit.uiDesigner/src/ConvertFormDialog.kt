// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.uiDesigner

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.EditorTextFieldWithBrowseButton
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.layout.selected
import javax.swing.JComponent


class ConvertFormDialog(private val project: Project, var className: String) : DialogWrapper(project) {
  enum class FormBaseClass { None, Configurable }

  var boundInstanceType: String = ""
  var boundInstanceExpression: String = ""
  var generateDescriptors = false
  var baseClass = FormBaseClass.None

  init {
    init()
    title = DevKitUIDesignerBundle.message("convert.form.dialog.title")
  }

  override fun createCenterPanel(): JComponent {
    return panel {
      row(DevKitUIDesignerBundle.message("convert.form.dialog.label.target.class.name")) {
        textField()
          .columns(40)
          .bindText(::className)
          .focused()
      }
      row(DevKitUIDesignerBundle.message("convert.form.dialog.label.bound.instance.type")) {
        cell(EditorTextFieldWithBrowseButton(project, true))
          .bind(EditorTextFieldWithBrowseButton::getText, EditorTextFieldWithBrowseButton::setText, ::boundInstanceType.toMutableProperty())
          .align(AlignX.FILL)
      }
      row(DevKitUIDesignerBundle.message("convert.form.dialog.label.bound.instance.expression")) {
        textField()
          .bindText(::boundInstanceExpression)
          .align(AlignX.FILL)
      }
      group(DevKitUIDesignerBundle.message("convert.form.dialog.base.class.separator")) {
        buttonsGroup {
          lateinit var rbConfigure: JBRadioButton
          row { radioButton(DevKitUIDesignerBundle.message("convert.form.dialog.base.class.none"), FormBaseClass.None) }
          row {
            rbConfigure = radioButton(DevKitUIDesignerBundle.message("convert.form.dialog.base.class.configurable"), FormBaseClass.Configurable)
              .component
          }
          indent {
            row {
              checkBox(DevKitUIDesignerBundle.message("convert.form.dialog.label.checkbox.generate.descriptors.for.search.everywhere"))
                .bindSelected(::generateDescriptors)
                .enabledIf(rbConfigure.selected)
            }
          }
        }.bind(::baseClass)
      }
    }
  }
}

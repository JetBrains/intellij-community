// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.uiDesigner

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.EditorTextFieldWithBrowseButton
import com.intellij.ui.layout.*
import javax.swing.JComponent


class ConvertFormDialog(val project: Project, var className: String) : DialogWrapper(project) {
  enum class FormBaseClass { None, Configurable }

  var boundInstanceType: String = ""
  var boundInstanceExpression: String = ""
  var generateDescriptors = false
  var baseClass = FormBaseClass.None

  init {
    init()
    title = DevKitUIDesignerBundle.message("convert.form.dialog.title")
  }

  override fun createCenterPanel(): JComponent? {
    return panel {
      row(DevKitUIDesignerBundle.message("convert.form.dialog.label.target.class.name")) {
        textField(::className, columns = 40).focused()
      }
      row(DevKitUIDesignerBundle.message("convert.form.dialog.label.bound.instance.type")) {
        EditorTextFieldWithBrowseButton(project, true)()
          .withBinding(EditorTextFieldWithBrowseButton::getText, EditorTextFieldWithBrowseButton::setText,
                       ::boundInstanceType.toBinding())
      }
      row(DevKitUIDesignerBundle.message("convert.form.dialog.label.bound.instance.expression")) {
        textField(::boundInstanceExpression)
      }
      titledRow(DevKitUIDesignerBundle.message("convert.form.dialog.base.class.separator")) {
        buttonGroup(::baseClass) {
          row { radioButton(DevKitUIDesignerBundle.message("convert.form.dialog.base.class.none"), FormBaseClass.None) }
          row {
            radioButton(DevKitUIDesignerBundle.message("convert.form.dialog.base.class.configurable"), FormBaseClass.Configurable)
            row {
              checkBox(DevKitUIDesignerBundle.message("convert.form.dialog.label.checkbox.generate.descriptors.for.search.everywhere"), ::generateDescriptors)
            }
          }
        }
      }
    }
  }
}

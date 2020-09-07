// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.formConversion

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.EditorTextFieldWithBrowseButton
import com.intellij.ui.layout.*
import org.jetbrains.idea.devkit.DevKitBundle
import javax.swing.JComponent

/**
 * @author yole
 */
class ConvertFormDialog(val project: Project, var className: String) : DialogWrapper(project) {
  enum class FormBaseClass { None, Configurable }

  var boundInstanceType: String = ""
  var boundInstanceExpression: String = ""
  var generateDescriptors = false
  var baseClass = FormBaseClass.None

  init {
    init()
    title = DevKitBundle.message("convert.form.dialog.title")
  }

  override fun createCenterPanel(): JComponent? {
    return panel {
      row(DevKitBundle.message("convert.form.dialog.label.target.class.name")) {
        textField(::className, columns = 40).focused()
      }
      row(DevKitBundle.message("convert.form.dialog.label.bound.instance.type")) {
        EditorTextFieldWithBrowseButton(project, true)()
          .withBinding(EditorTextFieldWithBrowseButton::getText, EditorTextFieldWithBrowseButton::setText,
                       ::boundInstanceType.toBinding())
      }
      row(DevKitBundle.message("convert.form.dialog.label.bound.instance.expression")) {
        textField(::boundInstanceExpression)
      }
      titledRow(DevKitBundle.message("convert.form.dialog.base.class.separator")) {
        buttonGroup(::baseClass) {
          row { radioButton(DevKitBundle.message("convert.form.dialog.base.class.none"), FormBaseClass.None) }
          row {
            radioButton(DevKitBundle.message("convert.form.dialog.base.class.configurable"), FormBaseClass.Configurable)
            row {
              checkBox(DevKitBundle.message("convert.form.dialog.label.checkbox.generate.descriptors.for.search.everywhere"), ::generateDescriptors)
            }
          }
        }
      }
    }
  }
}

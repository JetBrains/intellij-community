// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.formConversion

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.EditorTextFieldWithBrowseButton
import com.intellij.ui.layout.*
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
    title = "Convert Form to UI DSL"
  }

  override fun createCenterPanel(): JComponent? {
    return panel {
      row("Target class name:") {
        textField(::className, columns = 40).focused()
      }
      row("Bound instance type:") {
        EditorTextFieldWithBrowseButton(project, true)()
          .withBinding(EditorTextFieldWithBrowseButton::getText, EditorTextFieldWithBrowseButton::setText,
                       ::boundInstanceType.toBinding())
      }
      row("Bound instance expression") {
        textField(::boundInstanceExpression)
      }
      titledRow("Base class") {
        buttonGroup(::baseClass) {
          row { radioButton("None", FormBaseClass.None) }
          row {
            radioButton("Configurable", FormBaseClass.Configurable)
            row {
              checkBox("Generate descriptors for Search Everywhere", ::generateDescriptors)
            }
          }
        }
      }
    }
  }
}

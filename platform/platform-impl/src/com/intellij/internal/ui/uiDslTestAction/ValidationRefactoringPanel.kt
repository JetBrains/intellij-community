// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DialogTitleCapitalization")

package com.intellij.internal.ui.uiDslTestAction

import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.validation.CellValidation
import com.intellij.ui.layout.ValidationInfoBuilder
import com.intellij.util.Alarm
import java.awt.BorderLayout
import java.awt.Font
import javax.swing.*
import javax.swing.event.DocumentEvent

class ValidationRefactoringPanel(parentDisposable: Disposable) {

  companion object {

    private data class Model(
      var customEditOnInput: String = "customEditOnInput",
      var editOnApply: String = "editOnApply",
      var editOnApplyNew: String = "editOnApplyNew",
      var editOnInput: String = "editOnInput",
      var editOnInputNew: String = "editOnInputNew",
    )

    private class CustomEdit : JPanel(BorderLayout()) {

      val textField = JTextField()

      init {
        add(textField, BorderLayout.CENTER)
      }
    }
  }

  lateinit var panel: DialogPanel

  private val model = Model()
  private val alarm = Alarm(parentDisposable)

  private lateinit var lbIsModified: JLabel
  private lateinit var lbValidation: JLabel
  private lateinit var lbModel: JLabel

  init {
    panel = panel {
      row {
        label("Old Validation API").bold()
        label("New Validation API").bold()
        label("Enable New Validation API").bold()
      }.layout(RowLayout.PARENT_GRID)

      row {
        val customEdit = CustomEdit()
        cell(customEdit)
          .align(AlignX.FILL)
          .validationRequestor {
            customEdit.textField.document.addDocumentListener(object : DocumentAdapter() {
              override fun textChanged(e: DocumentEvent) {
                it()
              }
            })
          }
          .validationOnInput {
            validationInfo(it.textField, "customEdit")
          }.bind({ c: CustomEdit -> c.textField.text }, { c: CustomEdit, value: String -> c.textField.text = value },
                 model::customEditOnInput.toMutableProperty())
      }.layout(RowLayout.PARENT_GRID)

      row {
        textField()
          .align(AlignX.FILL)
          .bindText(model::editOnInput)
          .validationOnInput {
            validationInfo(it, "editOnInput")
          }

        lateinit var validation: CellValidation<*>
        textField()
          .align(AlignX.FILL)
          .bindText(model::editOnInputNew)
          .cellValidation {
            validation = this
            addInputRule { validationInfo(it, "editOnInputNew") }
          }

        checkBoxValidation(validation)
      }.layout(RowLayout.PARENT_GRID)

      row {
        textField()
          .align(AlignX.FILL)
          .bindText(model::editOnApply)
          .validationOnApply { validationInfo(it, "editOnApply") }

        lateinit var validation: CellValidation<*>
        textField()
          .align(AlignX.FILL)
          .bindText(model::editOnApplyNew)
          .cellValidation {
            validation = this
            addApplyRule { validationInfo(it, "editOnApplyNew") }
          }

        checkBoxValidation(validation)
      }.layout(RowLayout.PARENT_GRID)

      group("DialogPanel Control") {
        row {
          button("Reset") {
            panel.reset()
          }
          button("Apply") {
            panel.apply()
          }
          lbIsModified = label("").component
        }
        row {
          lbValidation = label("").component
        }
        row {
          lbModel = label("").component
        }
      }
    }

    panel.registerValidators(parentDisposable)

    SwingUtilities.invokeLater {
      initValidation()
    }
  }

  private fun Row.checkBoxValidation(validation: CellValidation<*>) {
    checkBox("Enable")
      .selected(true)
      .onChanged { validation.enabled = it.isSelected }
  }

  private fun validationInfo(component: JTextField, place: String): ValidationInfo? {
    return if (component.text.isEmpty()) {
      ValidationInfoBuilder(component).error("$place cannot be empty")
    }
    else {
      null
    }
  }

  private fun initValidation() {
    fun JComponent.bold(isBold: Boolean) {
      font = font.deriveFont(if (isBold) Font.BOLD else Font.PLAIN)
    }

    alarm.addRequest(Runnable {
      val validationErrors = panel.validateCallbacks.mapNotNull { it() }
      val modified = panel.isModified()

      lbIsModified.text = "isModified: $modified"
      lbIsModified.bold(modified)
      lbValidation.text = "<html>Validation Errors: ${validationErrors.joinToString { it.message }}"
      lbValidation.bold(validationErrors.isNotEmpty())
      lbModel.text = "<html>$model"

      initValidation()
    }, 1000)
  }
}

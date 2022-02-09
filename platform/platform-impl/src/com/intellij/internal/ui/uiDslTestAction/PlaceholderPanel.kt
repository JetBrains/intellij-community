// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.ui.uiDslTestAction

import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.Disposer
import com.intellij.ui.dsl.builder.*
import com.intellij.util.Alarm
import org.jetbrains.annotations.ApiStatus
import java.awt.Font
import java.awt.event.ItemEvent
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.SwingUtilities

private enum class PlaceholderComponent {
  NONE,
  LABEL,
  CHECK_BOX,
  TEXT_FIELD,
  INT_TEXT_FIELD,

  /**
   * Custom validation is used
   */
  CUSTOM_TEXT_FIELD,

  /**
   * Used one instance. Check that
   * - all listeners are removed after the component is removed from placeholder
   * - no duplicate listeners in the instance after several installing into placeholder
   */
  INSTANCE_TEXT_FIELD,
}

@Suppress("DialogTitleCapitalization")
@ApiStatus.Internal
internal class PlaceholderPanel(parentDisposable: Disposable) {

  lateinit var panel: DialogPanel
    private set

  private val model = Model()
  private val alarm = Alarm(parentDisposable)

  private val customTextEdit = panel {
    row {
      intTextField(0..100)
        .bindIntText(model::placeholderInstanceTextField)
        .errorOnApply("Int should be positive") {
          (it.text.toIntOrNull() ?: 0) <= 0
        }
    }
  }

  private lateinit var lbIsModified: JLabel
  private lateinit var lbValidation: JLabel
  private lateinit var lbModel: JLabel

  init {
    lateinit var placeholder: Placeholder

    panel = panel {
      row {
        text("Validation of placeholder. Select component and change values. Reset, Apply and isModified should work " +
              "as expected. Check also validation for int text field")
      }.bottomGap(BottomGap.MEDIUM)

      row {
        checkBox("simpleCheckbox")
          .bindSelected(model::simpleCheckbox)
      }
      row("Select Placeholder:") {
        comboBox(PlaceholderComponent.values().toList())
          .applyToComponent {
            addItemListener {
              if (it.stateChange == ItemEvent.SELECTED) {
                val type = it?.item as? PlaceholderComponent
                if (type == null) {
                  placeholder.component = null
                }
                else {
                  placeholder.component = createPlaceholderComponent(type)
                }
              }
            }
          }
        checkBox("enabled")
          .applyToComponent {
            isSelected = true
            addItemListener { placeholder.enabled(this.isSelected) }
          }
        checkBox("visible")
          .applyToComponent {
            isSelected = true
            addItemListener { placeholder.visible(this.isSelected) }
          }
      }
      row("Placeholder:") {
        placeholder = placeholder()
      }

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

    val disposable = Disposer.newDisposable()
    panel.registerValidators(disposable)
    Disposer.register(parentDisposable, disposable)

    SwingUtilities.invokeLater {
      initValidation()
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

  private fun createPlaceholderComponent(placeholderComponent: PlaceholderComponent): JComponent? {
    return when (placeholderComponent) {
      PlaceholderComponent.NONE -> null
      PlaceholderComponent.LABEL -> JLabel("JLabel")
      PlaceholderComponent.CHECK_BOX -> panel {
        row { checkBox("placeholderCheckBox").bindSelected(model::placeholderCheckBox) }
      }
      PlaceholderComponent.TEXT_FIELD -> panel {
        row { textField().bindText(model::placeholderTextField) }
      }
      PlaceholderComponent.INT_TEXT_FIELD -> panel {
        row { intTextField(0..100).bindIntText(model::placeholderIntTextField) }
      }
      PlaceholderComponent.CUSTOM_TEXT_FIELD -> panel {
        row {
          textField()
            .bindText(model::placeholderCustomTextField)
            .errorOnApply("String should be placeholderCustomTextField") {
              it.text != "placeholderCustomTextField"
            }
        }
      }
      PlaceholderComponent.INSTANCE_TEXT_FIELD -> customTextEdit
    }
  }
}

@ApiStatus.Internal
internal data class Model(
  var simpleCheckbox: Boolean = false,
  var placeholderCheckBox: Boolean = false,
  var placeholderTextField: String = "placeholderTextField",
  var placeholderIntTextField: Int = 0,
  var placeholderCustomTextField: String = "placeholderCustomTextField",
  var placeholderInstanceTextField: Int = 0,
)

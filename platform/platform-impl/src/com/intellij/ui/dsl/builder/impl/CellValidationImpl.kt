// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.builder.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.observable.properties.whenPropertyChanged
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.getUserData
import com.intellij.openapi.ui.putUserData
import com.intellij.openapi.ui.validation.DialogValidation
import com.intellij.openapi.ui.validation.DialogValidationRequestor
import com.intellij.openapi.ui.validation.WHEN_DOCUMENT_CHANGED
import com.intellij.openapi.ui.validation.WHEN_PROPERTY_CHANGED
import com.intellij.openapi.ui.validation.WHEN_STATE_CHANGED
import com.intellij.openapi.ui.validation.WHEN_TEXT_CHANGED
import com.intellij.openapi.ui.validation.and
import com.intellij.openapi.ui.validation.transformResult
import com.intellij.openapi.util.Key
import com.intellij.ui.EditorTextField
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.validation.CellValidation
import com.intellij.ui.dsl.validation.Level
import com.intellij.ui.dsl.validation.impl.createValidationInfo
import com.intellij.ui.layout.ComponentPredicate
import org.jetbrains.annotations.ApiStatus
import java.awt.ItemSelectable
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.text.JTextComponent

@ApiStatus.Internal
internal class CellValidationImpl<out T>(
  private val dialogPanelConfig: DialogPanelConfig,
  private val cell: T,
  private val interactiveComponent: JComponent,
) : CellValidation<T> {

  override var enabled: Boolean = true

  override fun enabledIf(predicate: ComponentPredicate) {
    enabled = predicate()
    predicate.addListener { enabled = it }
  }

  override fun enabledIf(property: ObservableProperty<Boolean>) {
    enabled = property.get()
    property.whenPropertyChanged {
      enabled = it
    }
  }

  override fun addApplyRule(message: String, level: Level, condition: () -> Boolean) {
    installValidationOnApply(dialogPanelConfig, interactiveComponent, DialogValidationWrapper {
      if (condition()) createValidationInfo(interactiveComponent, message, level)
      else null
    })
  }

  override fun addApplyRule(validation: () -> ValidationInfo?) {
    installValidationOnApply(dialogPanelConfig, interactiveComponent, DialogValidationWrapper {
      validation()
    })
  }

  override fun addInputRule(message: String, level: Level, condition: () -> Boolean) {
    installValidationOnInput(dialogPanelConfig, interactiveComponent, DialogValidationWrapper {
      if (condition()) createValidationInfo(interactiveComponent, message, level)
      else null
    })
  }

  override fun addInputRule(validation: () -> ValidationInfo?) {
    installValidationOnInput(dialogPanelConfig, interactiveComponent, DialogValidationWrapper {
      validation()
    })
  }

  private inner class DialogValidationWrapper(private val validation: DialogValidation) : DialogValidation {
    override fun validate(): ValidationInfo? {
      return if (enabled) validation.validate() else null
    }
  }

  companion object {

    private val BOUND_VALUE_PROPERTY_KEY = Key.create<ObservableProperty<*>>("BOUND_VALUE_PROPERTY_KEY")

    internal fun installValidationOnApply(
      dialogPanelConfig: DialogPanelConfig,
      interactiveComponent: JComponent,
      vararg validations: DialogValidation,
    ) {
      dialogPanelConfig.validationsOnApply.list(interactiveComponent)
        .addAll(validations.map { it.forComponentIfNeeded(interactiveComponent) })

      // Fallback in case if no validation requestors are defined
      guessAndInstallDefaultValidationRequestor(dialogPanelConfig, interactiveComponent)
    }

    internal fun installValidationOnInput(
      dialogPanelConfig: DialogPanelConfig,
      interactiveComponent: JComponent,
      vararg validations: DialogValidation,
    ) {
      dialogPanelConfig.validationsOnInput.list(interactiveComponent)
        .addAll(validations.map { it.forComponentIfNeeded(interactiveComponent) })

      // Fallback in case if no validation requestors are defined
      guessAndInstallDefaultValidationRequestor(dialogPanelConfig, interactiveComponent)
    }

    private fun DialogValidation.forComponentIfNeeded(component: JComponent) =
      transformResult { if (this.component == null) forComponent(component) else this }

    private fun guessAndInstallDefaultValidationRequestor(
      dialogPanelConfig: DialogPanelConfig,
      interactiveComponent: JComponent,
    ) {
      val stackTrace = Throwable()
      val validationRequestors = dialogPanelConfig.validationRequestors.list(interactiveComponent)
      if (validationRequestors.isNotEmpty()) return

      validationRequestors.add(object : DialogValidationRequestor {
        override fun subscribe(parentDisposable: Disposable?, validate: () -> Unit) {
          if (validationRequestors.size > 1) return

          val requestor = guessValidationRequestor(interactiveComponent)
          if (requestor != null) {
            requestor.subscribe(parentDisposable, validate)
          }
          else {
            logger<Cell<*>>().warn("Please, install Cell.validationRequestor", stackTrace)
          }
        }
      })
    }

    private fun guessValidationRequestor(
      interactiveComponent: JComponent,
    ): DialogValidationRequestor? {
      val property = interactiveComponent.getUserData(BOUND_VALUE_PROPERTY_KEY)
      if (property != null) {
        return WHEN_PROPERTY_CHANGED(property)
      }
      return when (interactiveComponent) {
        is JComboBox<*> -> {
          val requestor = WHEN_STATE_CHANGED(interactiveComponent)
          val editorComponent = interactiveComponent.editor?.editorComponent as? JComponent
          val requestorForEditorComponent = editorComponent?.let(::guessValidationRequestor)
          requestorForEditorComponent?.let { requestor and it } ?: requestor
        }
        is JTextComponent -> WHEN_TEXT_CHANGED(interactiveComponent)
        is ItemSelectable -> WHEN_STATE_CHANGED(interactiveComponent)
        is EditorTextField -> WHEN_DOCUMENT_CHANGED(interactiveComponent)
        else -> null
      }
    }

    internal fun installDefaultValidationRequestor(
      interactiveComponent: JComponent,
      property: ObservableProperty<*>,
    ) {
      interactiveComponent.putUserData(BOUND_VALUE_PROPERTY_KEY, property)
    }
  }
}

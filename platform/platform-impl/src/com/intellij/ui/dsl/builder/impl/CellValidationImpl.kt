// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.builder.impl

import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.observable.properties.whenPropertyChanged
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.validation.DialogValidation
import com.intellij.ui.dsl.validation.CellValidation
import com.intellij.ui.dsl.validation.Level
import com.intellij.ui.dsl.validation.impl.createValidationInfo
import com.intellij.ui.layout.ComponentPredicate
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Internal
internal class CellValidationImpl<out T>(private val dialogPanelConfig: DialogPanelConfig, private val cell: T,
                                         private val interactiveComponent: JComponent) : CellValidation<T> {

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
    registerValidation(dialogPanelConfig.validationsOnApply, interactiveComponent,
                       DialogValidationWrapper {
                         if (condition()) createValidationInfo(interactiveComponent, message, level)
                         else null
                       })
  }

  override fun addApplyRule(validation: () -> ValidationInfo?) {
    registerValidation(dialogPanelConfig.validationsOnApply, interactiveComponent,
                       DialogValidationWrapper {
                         validation()
                       })
  }

  override fun addInputRule(message: String, level: Level, condition: () -> Boolean) {
    registerValidation(dialogPanelConfig.validationsOnInput, interactiveComponent,
                       DialogValidationWrapper {
                         if (condition()) createValidationInfo(interactiveComponent, message, level)
                         else null
                       })
  }

  override fun addInputRule(validation: () -> ValidationInfo?) {
    registerValidation(dialogPanelConfig.validationsOnInput, interactiveComponent,
                       DialogValidationWrapper {
                         validation()
                       })
  }

  private fun registerValidation(map: LinkedHashMap<JComponent, MutableList<DialogValidation>>,
                                 component: JComponent,
                                 dialogValidation: DialogValidationWrapper) {
    map.list(component).add(dialogValidation)
  }

  private inner class DialogValidationWrapper(private val validation: DialogValidation) : DialogValidation {
    override fun validate(): ValidationInfo? {
      return if (enabled) validation.validate() else null
    }
  }
}

// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.validation.impl

import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.dsl.validation.CellValidation
import com.intellij.ui.dsl.validation.Level
import com.intellij.ui.layout.ComponentPredicate
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal class CompoundCellValidation<out T>(private vararg val cellValidations: CellValidation<T>) : CellValidation<T> {

  override var enabled: Boolean
    get() = cellValidations.all { it.enabled }
    set(value) {
      for (validation in cellValidations) {
        validation.enabled = value
      }
    }

  override fun enabledIf(predicate: ComponentPredicate) {
    for (cellValidation in cellValidations) {
      cellValidation.enabledIf(predicate)
    }
  }

  override fun enabledIf(property: ObservableProperty<Boolean>) {
    for (cellValidation in cellValidations) {
      cellValidation.enabledIf(property)
    }
  }

  override fun addApplyRule(message: String, level: Level, condition: () -> Boolean) {
    for (cellValidation in cellValidations) {
      cellValidation.addApplyRule(message, level, condition)
    }
  }

  override fun addApplyRule(validation: () -> ValidationInfo?) {
    for (cellValidation in cellValidations) {
      cellValidation.addApplyRule(validation)
    }
  }

  override fun addInputRule(message: String, level: Level, condition: () -> Boolean) {
    for (cellValidation in cellValidations) {
      cellValidation.addInputRule(message, level, condition)
    }
  }

  override fun addInputRule(validation: () -> ValidationInfo?) {
    for (cellValidation in cellValidations) {
      cellValidation.addInputRule(validation)
    }
  }
}

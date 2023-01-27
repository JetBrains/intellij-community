// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.validation.impl

import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.dsl.builder.CellBase
import com.intellij.ui.dsl.validation.CellValidation
import com.intellij.ui.dsl.validation.Level
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal class CompoundCellValidation<T : CellBase<T>>(private vararg val cellValidations: CellValidation<T>) : CellValidation<T> {

  override fun addApplyRule(message: String, level: Level, condition: (T) -> Boolean) {
    for (cellValidation in cellValidations) {
      cellValidation.addApplyRule(message, level, condition)
    }
  }

  override fun addApplyRule(validation: () -> ValidationInfo?) {
    for (cellValidation in cellValidations) {
      cellValidation.addApplyRule(validation)
    }
  }
}

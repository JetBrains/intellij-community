// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.builder.impl

import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.dsl.builder.CellBase
import com.intellij.ui.dsl.validation.CellValidation
import com.intellij.ui.dsl.validation.Level
import com.intellij.ui.dsl.validation.impl.createValidationInfo
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Internal
internal class CellValidationImpl<T : CellBase<T>>(private val dialogPanelConfig: DialogPanelConfig, private val cellBase: T,
                                                   private val interactiveComponent: JComponent) : CellValidation<T> {

  override fun addApplyRule(message: String, level: Level, condition: (T) -> Boolean) {
    addApplyRule {
      if (condition(cellBase)) createValidationInfo(interactiveComponent, message, level)
      else null
    }
  }

  override fun addApplyRule(validation: () -> ValidationInfo?) {
    dialogPanelConfig.validationsOnApply.list(interactiveComponent).add(validation)
  }
}

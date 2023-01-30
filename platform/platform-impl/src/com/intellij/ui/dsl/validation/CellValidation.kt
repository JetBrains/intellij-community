// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.validation

import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.builder.CellBase
import com.intellij.ui.dsl.builder.LayoutDslMarker
import org.jetbrains.annotations.ApiStatus

@ApiStatus.NonExtendable
@LayoutDslMarker
interface CellValidation<T : CellBase<T>> {

  fun addApplyRule(@NlsContexts.DialogMessage message: String, level: Level = Level.ERROR, condition: (T) -> Boolean)

  /**
   * Use overloaded method for simple cases
   */
  fun addApplyRule(validation: () -> ValidationInfo?)

}
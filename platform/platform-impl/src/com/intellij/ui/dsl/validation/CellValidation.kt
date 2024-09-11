// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.validation

import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.builder.LayoutDslMarker
import com.intellij.ui.layout.ComponentPredicate
import org.jetbrains.annotations.ApiStatus

@ApiStatus.NonExtendable
@LayoutDslMarker
/**
 * Represents cell validation
 */
interface CellValidation<out T> {

  /**
   * Enables/disables all validations related to this [CellValidation]
   */
  var enabled: Boolean

  fun enabledIf(predicate: ComponentPredicate)

  fun enabledIf(property: ObservableProperty<Boolean>)

  /**
   * Rule applied when user clicks OK button (might be a little bit heavier than [addInputRule])
   * [condition] returns `true` in case of error
   */
  fun addApplyRule(@NlsContexts.DialogMessage message: String, level: Level = Level.ERROR, condition: () -> Boolean)

  /**
   * Use overloaded method for simple cases
   */
  fun addApplyRule(validation: () -> ValidationInfo?)

  /**
   * Rule applied on each input (must be lightweight unlike [addApplyRule])
   * [condition] returns `true` in case of error
   */
  fun addInputRule(@NlsContexts.DialogMessage message: String, level: Level = Level.ERROR, condition: () -> Boolean)

  /**
   * Use overloaded method for simple cases
   */
  fun addInputRule(validation: () -> ValidationInfo?)

}
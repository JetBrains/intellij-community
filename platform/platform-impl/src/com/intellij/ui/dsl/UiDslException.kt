// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.gridLayout

import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import javax.swing.JComponent
import kotlin.reflect.KProperty0

@ApiStatus.Experimental
class UiDslException(message: String = "Internal error", cause: Throwable? = null) : RuntimeException(message, cause)

fun checkNonNegative(property: KProperty0<Int>) {
  val value = property.getter()

  if (value < 0) {
    throw UiDslException("Value cannot be negative: ${property.name} = $value")
  }
}

fun checkPositive(property: KProperty0<Int>) {
  val value = property.getter()

  if (value <= 0) {
    throw UiDslException("Value must be positive: ${property.name} = $value")
  }
}

fun checkRange(property: KProperty0<Int>, min: Int, max: Int) {
  val value = property.getter()

  if (value < min || value > max) {
    throw UiDslException("Value must be in range $min..$max: ${property.name} = $value")
  }
}

fun checkComponent(component: Component?): JComponent {
  if (component !is JComponent) {
    throw UiDslException("Only JComponents are supported: $component")
  }

  return component
}

fun checkConstraints(constraints: Any?): JBConstraints {
  if (constraints !is JBConstraints) {
    throw UiDslException("Invalid constraints: $constraints")
  }

  return constraints
}
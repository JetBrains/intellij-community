// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl

import com.intellij.ui.dsl.gridLayout.Constraints
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import javax.swing.JComponent

@ApiStatus.Internal
class UiDslException(message: String = "Internal error", cause: Throwable? = null) : RuntimeException(message, cause)

fun checkTrue(value: Boolean) {
  if (!value) {
    throw UiDslException()
  }
}

@ApiStatus.Internal
fun checkNonNegative(name: String, value: Int) {
  if (value < 0) {
    throw UiDslException("Value cannot be negative: $name = $value")
  }
}

@ApiStatus.Internal
fun checkPositive(name: String, value: Int) {
  if (value <= 0) {
    throw UiDslException("Value must be positive: $name = $value")
  }
}

@ApiStatus.Internal
fun checkComponent(component: Component?): JComponent {
  if (component !is JComponent) {
    throw UiDslException("Only JComponents are supported: $component")
  }

  return component
}

@ApiStatus.Internal
fun checkConstraints(constraints: Any?): Constraints {
  if (constraints !is Constraints) {
    throw UiDslException("Invalid constraints: $constraints")
  }

  return constraints
}

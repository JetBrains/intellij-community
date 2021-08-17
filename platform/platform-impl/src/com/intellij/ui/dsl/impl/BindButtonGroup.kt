// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.impl

import com.intellij.ui.layout.*
import javax.swing.ButtonGroup

internal class BindButtonGroup<T>(val binding: PropertyBinding<T>, val type: Class<T>) : ButtonGroup() {

  fun set(value: Any) {
    binding.set(type.cast(value))
  }
}
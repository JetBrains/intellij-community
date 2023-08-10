// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.builder.impl

import com.intellij.ui.dsl.builder.MutableProperty
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal data class MutablePropertyImpl<T>(val getter: () -> T, val setter: (value: T) -> Unit) : MutableProperty<T> {

  override fun get(): T {
    return getter()
  }

  override fun set(value: T) {
    setter(value)
  }
}

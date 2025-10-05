// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.observable.util

import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.ui.CardLayoutPanel

fun <T, C : CardLayoutPanel<T, *, *>> C.bindSelected(property: ObservableProperty<T>): C = apply {
  select(property.get(), true)
  property.afterChange { select(it, true) }
}

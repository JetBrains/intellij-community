// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.builder

import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.observable.util.bind
import com.intellij.openapi.util.NlsContexts.Label
import com.intellij.ui.dsl.builder.impl.CellImpl.Companion.installValidationRequestor
import javax.swing.JLabel

fun <C : JLabel> Cell<C>.bindText(property: ObservableProperty<@Label String>): Cell<C> {
  installValidationRequestor(property)
  return applyToComponent { bind(property) }
}
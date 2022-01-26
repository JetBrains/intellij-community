// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.builder

import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import javax.swing.JComponent

fun Panel.validateAfterPropagation(graph: PropertyGraph): Panel = apply {
  validationRequestor(graph::afterPropagation)
}

fun <C : JComponent> Cell<C>.validateAfterChange(property: ObservableProperty<*>) = apply {
  validationRequestor { validate ->
    property.afterChange {
      validate()
    }
  }
}
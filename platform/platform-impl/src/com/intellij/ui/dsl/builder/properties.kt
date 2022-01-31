// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.builder

import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import javax.swing.JComponent

fun <C : JComponent> Cell<C>.enableIf(property: ObservableProperty<Boolean>): Cell<C> = apply {
  enabled(property.get())
  property.afterChange {
    enabled(it)
  }
}

fun <C : JComponent> Cell<C>.visibleIf(property: ObservableProperty<Boolean>): Cell<C> = apply {
  visible(property.get())
  property.afterChange {
    visible(it)
  }
}

fun Panel.validateAfterPropagation(graph: PropertyGraph): Panel = apply {
  validationRequestor(graph::afterPropagation)
}

fun <C : JComponent> Cell<C>.validateAfterPropagation(property: GraphProperty<*>): Cell<C> = apply {
  validationRequestor(property::afterPropagation)
}

fun <C : JComponent> Cell<C>.validateAfterChange(property: ObservableProperty<*>): Cell<C> = apply {
  validationRequestor { validate ->
    property.afterChange {
      validate()
    }
  }
}
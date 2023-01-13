// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.observable

import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach

abstract class PropertyTestCase {

  private lateinit var propertyGraph: PropertyGraph

  @BeforeEach
  fun setUp() {
    propertyGraph = PropertyGraph()
  }

  protected fun <T> property(initial: () -> T): GraphProperty<T> =
    propertyGraph.lazyProperty(initial)

  fun <T> ObservableMutableProperty<T>.dependsOn(property: ObservableProperty<T>, update: () -> T) {
    propertyGraph.dependsOn(this, property) { update() }
  }

  fun afterGraphPropagation(listener: () -> Unit) {
    propertyGraph.afterPropagation(listener)
  }

  protected fun <T> assertProperty(property: ObservableProperty<T>, value: T) {
    Assertions.assertEquals(value, property.get())
  }

  protected fun <T> assertProperties(properties: List<ObservableProperty<T>>, vararg values: T) {
    Assertions.assertEquals(values.toList(), properties.map { it.get() })
  }

  protected fun <E> generate(times: Int, generate: (Int) -> E) = (0 until times).map(generate)
}
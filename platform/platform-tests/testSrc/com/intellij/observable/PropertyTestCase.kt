// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.observable

import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import junit.framework.TestCase
import org.junit.Assert

abstract class PropertyTestCase : TestCase() {
  private lateinit var propertyGraph: PropertyGraph

  override fun setUp() {
    propertyGraph = PropertyGraph()
  }

  protected fun <T> property(initial: () -> T): GraphProperty<T> =
    propertyGraph.lazyProperty(initial)

  protected fun <T> assertProperty(property: GraphProperty<T>, value: T, isPropagationBlocked: Boolean) {
    Assert.assertEquals(isPropagationBlocked, propertyGraph.isPropagationBlocked(property))
    Assert.assertEquals(value, property.get())
  }

  protected fun <E> generate(times: Int, generate: (Int) -> E) = (0 until times).map(generate)
}
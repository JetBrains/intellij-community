package com.intellij.ui.layout

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ValueComponentPredicateTest {
  @Test
  fun `listener can be added while value is dispatched`() {
    val predicate = ValueComponentPredicate(false)
    val events = mutableListOf<String>()

    predicate.addListener { value ->
      events += "first:$value"
      predicate.addListener { nextValue ->
        events += "second:$nextValue"
      }
    }

    predicate.set(true)
    predicate.set(false)

    assertEquals(listOf("first:true", "first:false", "second:false"), events)
  }
}

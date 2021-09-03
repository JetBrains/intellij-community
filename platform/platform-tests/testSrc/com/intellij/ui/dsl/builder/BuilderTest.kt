// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder

import org.junit.Test
import javax.swing.JComponent
import kotlin.random.Random
import kotlin.test.assertEquals

class BuilderTest {

  @Test
  fun testEnabledVisible() {
    fun setState(entity: Any, value: Boolean) {
      when (entity) {
        is Panel -> {
          entity.enabled(value)
          entity.visible(value)
        }
        is Row -> {
          entity.enabled(value)
          entity.visible(value)
        }
        is Cell<*> -> {
          entity.enabled(value)
          entity.visible(value)
        }
      }
    }

    val iterationCount = 100
    val entities = mutableListOf<Any>()
    lateinit var cell: Cell<JComponent>

    panel {
      entities += row {
        entities += panel {
          entities += group {
            entities += row {
              cell = textField()
              entities += cell
            }
          }
        }
      }
    }

    val states = Array(entities.size) { true }

    for (i in 1..iterationCount) {
      val index = Random.nextInt(states.size)
      val state = Random.nextBoolean()
      states[index] = state
      setState(entities[index], state)
      val expectedComponentState = states.all { it }
      assertEquals(expectedComponentState, cell.component.isVisible)
      assertEquals(expectedComponentState, cell.component.isEnabled)
    }

    // Return all states to true
    for (i in states.indices) {
      states[i] = true
      setState(entities[i], true)
      val expectedComponentState = states.all { it }
      assertEquals(expectedComponentState, cell.component.isVisible)
      assertEquals(expectedComponentState, cell.component.isEnabled)
    }
  }
}

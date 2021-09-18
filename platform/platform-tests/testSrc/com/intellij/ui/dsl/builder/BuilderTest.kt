// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder

import org.junit.Test
import javax.swing.JComponent
import kotlin.random.Random
import kotlin.test.assertEquals

class BuilderTest {

  @Test
  fun testEnabled() {
    testEnabledVisible({ entity, value ->
      when (entity) {
        is Panel -> entity.enabled(value)
        is Row -> entity.enabled(value)
        is Cell<*> -> entity.enabled(value)
        else -> Unit
      }
    }, { jComponent -> jComponent.isEnabled })
  }

  @Test
  fun testVisible() {
    testEnabledVisible({ entity, value ->
      when (entity) {
        is Panel -> entity.visible(value)
        is Row -> entity.visible(value)
        is Cell<*> -> entity.visible(value)
        else -> Unit
      }
    }, { jComponent -> jComponent.isVisible })
  }

  private fun testEnabledVisible(setState: (entity: Any, value: Boolean) -> Unit, getState: (JComponent) -> Boolean) {
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
      assertEquals(expectedComponentState, getState(cell.component))
    }

    // Return all states to true
    for (i in states.indices) {
      states[i] = true
      setState(entities[i], true)
      val expectedComponentState = states.all { it }
      assertEquals(expectedComponentState, getState(cell.component))
    }
  }
}

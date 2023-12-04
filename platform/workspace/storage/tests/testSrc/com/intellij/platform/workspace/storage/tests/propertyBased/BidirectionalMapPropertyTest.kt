// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.workspace.storage.tests.propertyBased

import com.intellij.platform.workspace.storage.impl.containers.PersistentBidirectionalMap
import com.intellij.platform.workspace.storage.impl.containers.PersistentBidirectionalMapImpl
import org.jetbrains.jetCheck.Generator
import org.jetbrains.jetCheck.ImperativeCommand
import org.jetbrains.jetCheck.PropertyChecker
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.fail
import com.intellij.util.containers.BidirectionalMap as OriginalBidirectionalMap

class BidirectionalMapPropertyTest {

  @Test
  fun propertyTest() {
    PropertyChecker.customized().withSizeHint { if (it < 20) return@withSizeHint 20 else return@withSizeHint it }.checkScenarios {
      ImperativeCommand { env ->
        val optimizedMap = PersistentBidirectionalMapImpl<Int, Int>().builder()
        val originalMap = OriginalBidirectionalMap<Int, Int>()

        env.executeCommands(Generator.sampledFrom(
          AddValues(optimizedMap, originalMap),
          GetValues(optimizedMap, originalMap),
          RemoveKey(optimizedMap, originalMap),
        ))
      }
    }
  }

  private class AddValues(val optimizedMap: PersistentBidirectionalMap.Builder<Int, Int>,
                          val originalMap: OriginalBidirectionalMap<Int, Int>) : ImperativeCommand {
    override fun performCommand(env: ImperativeCommand.Environment) {
      for (i in 1..env.generateValue(Generator.integers(2, 10), null)) {
        val key = env.generateValue(Generator.integers(-20, 20), null)
        val value = env.generateValue(Generator.integers(-20, 20), null)

        optimizedMap[key] = value
        originalMap[key] = value

        env.logMessage("Put values. key: $key, values: $value")
        assertCorrect(optimizedMap, originalMap)
      }
    }
  }

  private class GetValues(val optimizedMap: PersistentBidirectionalMap.Builder<Int, Int>,
                          val originalMap: OriginalBidirectionalMap<Int, Int>) : ImperativeCommand {
    override fun performCommand(env: ImperativeCommand.Environment) {
      val key = selectElement(env, originalMap.keys.toList()) ?: return
      assertEquals(originalMap[key], optimizedMap[key])
    }
  }

  private class RemoveKey(val optimizedMap: PersistentBidirectionalMap.Builder<Int, Int>,
                          val originalMap: OriginalBidirectionalMap<Int, Int>) : ImperativeCommand {
    override fun performCommand(env: ImperativeCommand.Environment) {
      val key = selectElement(env, originalMap.keys.toList()) ?: return

      optimizedMap.remove(key)
      originalMap.remove(key)
      env.logMessage("Remove all values by key: $key")
      assertCorrect(optimizedMap, originalMap)
    }
  }
}

private fun selectElement(env: ImperativeCommand.Environment, elements: List<Int>): Int? {
  if (elements.isEmpty()) return null
  val idx = env.generateValue(Generator.integers(0, elements.lastIndex), null)
  return elements[idx]
}

private fun assertCorrect(optimizedMap: PersistentBidirectionalMap.Builder<Int, Int>, originalMap: OriginalBidirectionalMap<Int, Int>) {
  val optimizedMapCopy = optimizedMap.build().builder()
  assertEquals(optimizedMapCopy.size, originalMap.size)
  originalMap.keys.forEach { key ->
    if (!optimizedMapCopy.contains(key)) {
      fail("Missing key: $key")
    }

    val expectedValues = originalMap[key]
    assertEquals(expectedValues, optimizedMapCopy.remove(key))
  }
  if (!optimizedMapCopy.isEmpty()) fail("Extra keys")
}
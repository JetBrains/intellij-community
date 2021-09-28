// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.propertyBased

import com.intellij.workspaceModel.storage.impl.containers.BidirectionalMap
import org.jetbrains.jetCheck.Generator
import org.jetbrains.jetCheck.ImperativeCommand
import org.jetbrains.jetCheck.PropertyChecker
import org.junit.Assert
import org.junit.Test
import com.intellij.util.containers.BidirectionalMap as OriginalBidirectionalMap

class BidirectionalMapPropertyTest {

  @Test
  fun propertyTest() {
    PropertyChecker.customized().withSizeHint { if (it < 20) return@withSizeHint 20 else return@withSizeHint it }.checkScenarios {
      ImperativeCommand { env ->
        val optimizedMap = BidirectionalMap<Int, Int>()
        val originalMap = OriginalBidirectionalMap<Int, Int>()

        env.executeCommands(Generator.sampledFrom(
          AddValues(optimizedMap, originalMap),
          GetValues(optimizedMap, originalMap),
          RemoveKey(optimizedMap, originalMap),
          RemoveValue(optimizedMap, originalMap),
        ))
      }
    }
  }

  private class AddValues(val optimizedMap: BidirectionalMap<Int, Int>, val originalMap: OriginalBidirectionalMap<Int, Int>) : ImperativeCommand {
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

  private class GetValues(val optimizedMap: BidirectionalMap<Int, Int>, val originalMap: OriginalBidirectionalMap<Int, Int>) : ImperativeCommand {
    override fun performCommand(env: ImperativeCommand.Environment) {
      val key = selectElement(env, originalMap.keys.toList()) ?: return
      Assert.assertEquals(originalMap[key], optimizedMap[key])
    }
  }

  private class RemoveKey(val optimizedMap: BidirectionalMap<Int, Int>, val originalMap: OriginalBidirectionalMap<Int, Int>) : ImperativeCommand {
    override fun performCommand(env: ImperativeCommand.Environment) {
      val key = selectElement(env, originalMap.keys.toList()) ?: return

      optimizedMap.remove(key)
      originalMap.remove(key)
      env.logMessage("Remove all values by key: $key")
      assertCorrect(optimizedMap, originalMap)
    }
  }

  private class RemoveValue(val optimizedMap: BidirectionalMap<Int, Int>, val originalMap: OriginalBidirectionalMap<Int, Int>) : ImperativeCommand {
    override fun performCommand(env: ImperativeCommand.Environment) {
      val value = selectElement(env, originalMap.values.toList()) ?: return

      optimizedMap.removeValue(value)
      originalMap.removeValue(value)

      env.logMessage("Remove value: $value")
      assertCorrect(optimizedMap, originalMap)
    }
  }
}

private fun selectElement(env: ImperativeCommand.Environment, elements: List<Int>): Int? {
  if (elements.isEmpty()) return null
  val idx = env.generateValue(Generator.integers(0, elements.lastIndex), null)
  return elements[idx]
}

private fun assertCorrect(optimizedMap: BidirectionalMap<Int, Int>, originalMap: OriginalBidirectionalMap<Int, Int>) {
  val optimizedMapCopy = optimizedMap.copy()
  optimizedMapCopy.assertConsistency()
  Assert.assertEquals(optimizedMapCopy.size, originalMap.size)
  originalMap.keys.forEach { key ->
    if (!optimizedMapCopy.containsKey(key)) {
      Assert.fail("Missing key: $key")
      return
    }

    val expectedValues = originalMap[key]
    Assert.assertEquals(expectedValues, optimizedMapCopy.remove(key))
  }
  if (!optimizedMapCopy.isEmpty()) Assert.fail("Extra keys")
}
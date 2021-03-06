// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.containers

import com.intellij.util.containers.BidirectionalMap
import com.intellij.workspaceModel.storage.impl.containers.MutableNonNegativeIntIntBiMap
import com.intellij.workspaceModel.storage.impl.containers.NonNegativeIntIntBiMap
import org.jetbrains.jetCheck.Generator
import org.jetbrains.jetCheck.ImperativeCommand
import org.jetbrains.jetCheck.PropertyChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class NonNegativeIntIntBiMapPropertyTest {
  @Test
  fun propertyTest() {
    PropertyChecker.checkScenarios {
      ImperativeCommand { env ->
        val myMap = MutableNonNegativeIntIntBiMap()
        val workingMap = BidirectionalMap<Int, Int>()

        env.executeCommands(Generator.sampledFrom(
          AddValues(myMap, workingMap),
          GetValues(myMap, workingMap),
          RemoveAll(myMap, workingMap),
          RemoveKeyValue(myMap, workingMap),
          ToImmutable(myMap, workingMap)
        ))
      }
    }
  }

  class AddValues(val myMap: MutableNonNegativeIntIntBiMap, val workingMap: BidirectionalMap<Int, Int>) : ImperativeCommand {
    override fun performCommand(env: ImperativeCommand.Environment) {
      val amountOfValues = env.generateValue(Generator.integers(0, 10), null)

      val value = env.generateValue(positiveValuesGenerator, null)

      val keys = (0..amountOfValues).map {
        env.generateValue(positiveValuesGenerator, null)
      }

      myMap.putAll(keys.toIntArray(), value)
      for (key in keys) {
        workingMap.put(key, value)
      }

      env.logMessage("Put values. key: $value, values: $keys")

      assertCorrect(myMap, workingMap)
    }
  }

  class GetValues(val myMap: MutableNonNegativeIntIntBiMap, val workingMap: BidirectionalMap<Int, Int>) : ImperativeCommand {
    override fun performCommand(env: ImperativeCommand.Environment) {
      val key = selectKey(env, workingMap) ?: return

      val expectedValue = workingMap[key]!!

      val actualValue = myMap.get(key)

      assertEquals(expectedValue, actualValue)
    }
  }

  class RemoveAll(val myMap: MutableNonNegativeIntIntBiMap, val workingMap: BidirectionalMap<Int, Int>) : ImperativeCommand {
    override fun performCommand(env: ImperativeCommand.Environment) {
      val key = selectKey(env, workingMap) ?: return

      workingMap.remove(key)
      myMap.removeKey(key)

      env.logMessage("Remove all values by key: $key")

      assertCorrect(myMap, workingMap)
    }
  }

  class RemoveKeyValue(val myMap: MutableNonNegativeIntIntBiMap, val workingMap: BidirectionalMap<Int, Int>) : ImperativeCommand {
    override fun performCommand(env: ImperativeCommand.Environment) {
      val key = selectKey(env, workingMap) ?: return
      val value = workingMap.get(key)!!

      workingMap.remove(key, value)
      myMap.remove(key, value)

      env.logMessage("Remove key-value: $key, $value")

      assertCorrect(myMap, workingMap)
    }
  }

  class ToImmutable(val myMap: MutableNonNegativeIntIntBiMap, val workingMap: BidirectionalMap<Int, Int>) : ImperativeCommand {
    override fun performCommand(env: ImperativeCommand.Environment) {
      val immutable = myMap.toImmutable()

      assertCorrect(immutable, workingMap)
    }
  }
}

private val positiveValuesGenerator = Generator.from { env -> env.generate(Generator.integers(0, Int.MAX_VALUE)) }

private fun assertCorrect(myMap: NonNegativeIntIntBiMap, workingMap: BidirectionalMap<Int, Int>) {
  val workingMapCopy = workingMap.copy()
  myMap.keys.forEach { key ->
    if (!workingMapCopy.containsKey(key)) {
      fail("Missing key: $key")
      return
    }

    val actualKey = myMap.get(key)

    val expectedKey = workingMapCopy.remove(key)!!

    assertEquals(expectedKey, actualKey)
  }
  if (!workingMapCopy.isEmpty()) fail("Extra keys")
}

private fun selectKey(env: ImperativeCommand.Environment, map: BidirectionalMap<Int, Int>): Int? {
  val keys = map.keys.toList().sorted()
  if (keys.isEmpty()) return null
  val idx = env.generateValue(Generator.integers(0, keys.lastIndex), null)
  return keys[idx]
}

private fun BidirectionalMap<Int, Int>.copy(): BidirectionalMap<Int, Int> {
  val res = BidirectionalMap<Int, Int>()
  for (mutableEntry in this) {
    res[mutableEntry.key] = mutableEntry.value
  }
  return res
}

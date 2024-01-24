// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests.containers

import com.intellij.platform.workspace.storage.impl.containers.MutableNonNegativeIntIntMultiMap
import com.intellij.platform.workspace.storage.impl.containers.NonNegativeIntIntMultiMap
import com.intellij.util.containers.MultiMap
import org.jetbrains.jetCheck.Generator
import org.jetbrains.jetCheck.ImperativeCommand
import org.jetbrains.jetCheck.PropertyChecker
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class NonNegativeIntIntMultiMapPropertyTest {
  @Test
  fun propertyTest() {
    PropertyChecker.checkScenarios {
      ImperativeCommand { env ->
        val myMap = MutableNonNegativeIntIntMultiMap.ByList()
        val workingMap = MultiMap<Int, Int>()

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

  internal class AddValues(val myMap: MutableNonNegativeIntIntMultiMap, val workingMap: MultiMap<Int, Int>) : ImperativeCommand {
    override fun performCommand(env: ImperativeCommand.Environment) {
      val amountOfValues = env.generateValue(Generator.integers(0, 10), null)

      val key = env.generateValue(positiveValuesGenerator, null)

      val values = (0..amountOfValues).map {
        env.generateValue(positiveValuesGenerator, null)
      }

      myMap.addAll(key, values.toIntArray())
      workingMap.putValues(key, values)

      env.logMessage("Put values. key: $key, values: $values")

      assertCorrect(myMap, workingMap)
    }
  }

  internal class GetValues(val myMap: NonNegativeIntIntMultiMap, val workingMap: MultiMap<Int, Int>) : ImperativeCommand {
    override fun performCommand(env: ImperativeCommand.Environment) {
      val key = selectKey(env, workingMap) ?: return

      val expectedValues = workingMap.get(key).sorted()

      val actualValues = myMap.get(key).toArray().sorted()

      assertEquals(expectedValues, actualValues)
    }
  }

  internal class RemoveAll(val myMap: MutableNonNegativeIntIntMultiMap, val workingMap: MultiMap<Int, Int>) : ImperativeCommand {
    override fun performCommand(env: ImperativeCommand.Environment) {
      val key = selectKey(env, workingMap) ?: return

      workingMap.remove(key)
      myMap.remove(key)

      env.logMessage("Remove all values by key: $key")

      assertCorrect(myMap, workingMap)
    }
  }

  internal class RemoveKeyValue(val myMap: MutableNonNegativeIntIntMultiMap, val workingMap: MultiMap<Int, Int>) : ImperativeCommand {
    override fun performCommand(env: ImperativeCommand.Environment) {
      val key = selectKey(env, workingMap) ?: return
      val values = workingMap.get(key).sorted()
      val idx = env.generateValue(Generator.integers(0, values.lastIndex), null)
      val value = values[idx]

      workingMap.remove(key, value)
      myMap.remove(key, value)

      env.logMessage("Remove key-value: $key, $value")

      assertCorrect(myMap, workingMap)
    }
  }

  internal class ToImmutable(val myMap: MutableNonNegativeIntIntMultiMap, val workingMap: MultiMap<Int, Int>) : ImperativeCommand {
    override fun performCommand(env: ImperativeCommand.Environment) {
      val immutable = myMap.toImmutable()

      assertCorrect(immutable, workingMap)
    }
  }
}

private val positiveValuesGenerator = Generator.from { env -> env.generate(Generator.integers(0, Int.MAX_VALUE)) }

private fun assertCorrect(myMap: NonNegativeIntIntMultiMap, workingMap: MultiMap<Int, Int>) {
  val workingMapCopy = workingMap.copy()
  myMap.keys().forEach { key ->
    if (!workingMapCopy.containsKey(key) && !myMap[key].isEmpty()) {
      fail("Missing key: $key")
    }

    val actualKeys = myMap[key].toArray().sorted()

    val expectedKeys = workingMapCopy.remove(key)!!.sorted()

    assertEquals(expectedKeys, actualKeys)
  }
  if (!workingMapCopy.isEmpty) fail("Extra keys")
}

private fun selectKey(env: ImperativeCommand.Environment, map: MultiMap<Int, Int>): Int? {
  val keys = map.keySet().toList().sorted()
  if (keys.isEmpty()) return null
  val idx = env.generateValue(Generator.integers(0, keys.lastIndex), null)
  return keys[idx]
}

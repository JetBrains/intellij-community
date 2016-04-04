/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.vcs.log.graph.utils

import com.intellij.util.containers.MultiMap
import org.junit.Assert.assertEquals
import org.junit.Test


private class MapTester {
  val correctMap = MultiMap<Int, Int>()
  val testedMap = IntIntMultiMap()

  fun putValue(key: Int, value: Int) {
    correctMap.putValue(key, value)
    testedMap.putValue(key, value)
    compareMaps()
  }

  fun remove(key: Int, value: Int) {
    correctMap.remove(key, value)
    testedMap.remove(key, value)
    compareMaps()
  }

  fun compareMaps() {
    val keys = compareKeys()
    val correctStr = keys.joinLines {
      "$it:" + correctMap.get(it).sorted().joinToString()
    }

    val testedStr = keys.joinLines {
      "$it:" + testedMap.get(it).sorted().joinToString()
    }

    val testedArrayStr = keys.joinLines {
      "$it:" + testedMap.getAsArray(it).sortR().joinToString()
    }
    assertEquals("get and getasArray have nonequal results", testedArrayStr, testedStr)
    assertEquals("maps non equals", correctStr, testedStr)
  }

  fun compareKeys(): List<Int> {
    val correctKeys = correctMap.keySet().sorted().joinToString()
    val testedKeys = testedMap.keys().sortR().joinToString()
    assertEquals(correctKeys, testedKeys)
    return correctMap.keySet().sorted()
  }
}

fun IntArray.sortR(): IntArray {
  this.sort()
  return this
}

fun <T> Iterable<T>.joinLines(f: (T) -> String): String = map(f).joinToString(separator = "\n")

internal class IntIntMultiMapTest {

  private fun runTest(test: MapTester.() -> Unit) = MapTester().test()

  @Test fun simple() {
    runTest {
      putValue(2, 239)
      putValue(2, 17)
      putValue(2, 18)
      putValue(8, 18)
      putValue(8, 22)

      remove(2, 2)
      putValue(2, 2)
      remove(2, 17)
      remove(8, 18)
    }
  }

  @Test fun removeOneValue() {
    runTest {
      putValue(1, 2)
      remove(1, 2)
    }
  }

  @Test fun removeTwoValue() {
    runTest {
      putValue(1, 2)
      putValue(1, 3)
      remove(1, 2)
      remove(1, 3)
    }
  }

  @Test fun addDifferentKeys() {
    runTest {
      putValue(1, 1)
      putValue(2, 10)
      putValue(1, 3)
      putValue(2, 30)
      remove(1, 3)
    }
  }

  @Test fun removeForNonExistedKey() {
    runTest {
      remove(1, 3)

      putValue(2, 5)
      remove(3, 3)
    }
  }

}
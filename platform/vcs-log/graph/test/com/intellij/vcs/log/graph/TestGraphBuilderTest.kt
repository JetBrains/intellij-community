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
package com.intellij.vcs.log.graph

import org.junit.Assert.assertEquals
import org.junit.Test


internal class TestGraphBuilderTest : AbstractTestWithTextFile("testGraphBuilder") {

  fun runTest(testName: String, builder: TestGraphBuilder.() -> Unit) {
    val actual = graph(builder).asString()
    assertEquals(loadText(testName + ".txt"), actual)
  }

  @Test fun simple() = runTest("simple") {
    1(2)
    2(3)
    3()
  }

  @Test fun simpleManyNodes() = runTest("simpleManyNodes") {
    3(4, 6, 8)
    4(5, 6, 7, 8)
    7(8, 6)
    6(9, 10, 11)
    9(10, 11)
    10(11)
    5(8, 11)
    8(11)
    11()
  }

  @Test fun specialEdges() = runTest("specialEdges") {
    1(2.u, 3.dot, 100.not_load, 42.down_dot, null.down_dot, null.not_load, 0.up_dot, null.up_dot)
    2(null.up_dot)
    3(0.up_dot)
  }

  @Test fun specialNodes() = runTest("specialNodes") {
    2.U(3, 4)
    3.UNM(4.dot)
    4.U()
    100.NOT_LOAD()
  }
}
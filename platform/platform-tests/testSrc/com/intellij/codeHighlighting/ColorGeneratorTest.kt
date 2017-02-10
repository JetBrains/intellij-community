/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeHighlighting

import com.intellij.testFramework.UsefulTestCase
import com.intellij.ui.Gray
import java.awt.Color

class ColorGeneratorTest : UsefulTestCase() {
  fun testLineGeneration() {
    for (count in 0..5) {
      assertEquals(count + 2, generate(Color.BLACK, Color.WHITE, count).size)
    }

    assertOrderedEquals(generate(Gray._100, Gray._200, 0), listOf(Gray._100, Gray._200))
    assertOrderedEquals(generate(Gray._100, Gray._200, 1), listOf(Gray._100, Gray._150, Gray._200))
    assertOrderedEquals(generate(Gray._100, Gray._200, 3), listOf(Gray._100, Gray._125, Gray._150, Gray._175, Gray._200))

    assertOrderedEquals(generate(Color(0, 100, 200), Color(200, 100, 0), 1), listOf(Color(0, 100, 200), Color(100, 100, 100), Color(200, 100, 0)))
  }

  fun testChainLineGeneration() {
    for (anchorCount in 0..5) {
      val anchors = generate(Color.BLACK, Color.WHITE, anchorCount)
      for (count in 0..5) {
        val generated = generateChain(count, anchors)
        assertContainsOrdered(generated, anchors)
      }
    }

    assertOrderedEquals(generateChain(0, Gray._100, Gray._200), listOf(Gray._100, Gray._200))
    assertOrderedEquals(generateChain(1, Gray._100, Gray._200), listOf(Gray._100, Gray._150, Gray._200))
    assertOrderedEquals(generateChain(3, Gray._100, Gray._200), listOf(Gray._100, Gray._125, Gray._150, Gray._175, Gray._200))

    assertOrderedEquals(generateChain(0, Gray._0, Gray._100, Gray._200), listOf(Gray._0, Gray._100, Gray._200))
    assertOrderedEquals(generateChain(1, Gray._0, Gray._100, Gray._200), listOf(Gray._0, Gray._50, Gray._100, Gray._150, Gray._200))
    assertOrderedEquals(generateChain(3, Gray._0, Gray._100, Gray._200), listOf(Gray._0, Gray._25, Gray._50, Gray._75, Gray._100, Gray._125, Gray._150, Gray._175, Gray._200))

    assertOrderedEquals(generateChain(1, Color(0, 100, 200), Color(200, 100, 0)), listOf(Color(0, 100, 200), Color(100, 100, 100), Color(200, 100, 0)))
    assertOrderedEquals(generateChain(1, Color(0, 100, 200), Color(0, 0, 0), Color(200, 100, 0)), listOf(Color(0, 100, 200), Color(0, 50, 100), Color(0, 0, 0), Color(100, 50, 0), Color(200, 100, 0)))
  }

  fun generate(color1: Color, color2: Color, count: Int) = ColorGenerator.generateLinearColorSequence(color1, color2, count)
  fun generateChain(count: Int, vararg colors: Color) = ColorGenerator.generateLinearColorSequence(colors.asList(), count)
  fun generateChain(count: Int, colors: List<Color>) = ColorGenerator.generateLinearColorSequence(colors, count)
}

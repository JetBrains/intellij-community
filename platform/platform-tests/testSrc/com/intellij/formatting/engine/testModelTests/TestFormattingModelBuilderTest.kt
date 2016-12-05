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
package com.intellij.formatting.engine.testModelTests

import com.intellij.formatting.Block
import com.intellij.formatting.CompositeTestBlock
import com.intellij.formatting.engine.testModel.getRoot
import com.intellij.formatting.toFormattingBlock
import com.intellij.openapi.util.TextRange
import com.intellij.util.containers.TreeTraversal
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class TestFormattingModelBuilderTest {

  @Test
  fun `test model with correct ranges is built`() {
    val root = getRoot(
      """[a0]foo         [a1]goo
              []hoo
[a1]([a2]qoo             [a2]woo       [a3]([]roo []too))"""
    )

    val rootFormattingBlock = root.toFormattingBlock(0)

    val subBlocks = rootFormattingBlock.subBlocks
    assertThat(subBlocks).hasSize(4)

    assertThat(subBlocks.last().subBlocks).hasSize(3)
    assertThat(subBlocks.last().subBlocks.last().subBlocks).hasSize(2)

    assertLeafRanges(rootFormattingBlock, 0..3, 12..15, 30..33, 34..37, 50..53, 60..63, 64..67)
  }

  private fun assertLeafRanges(rootFormattingBlock: CompositeTestBlock, vararg ranges: IntRange) {
    val textRanges = ranges.map { TextRange(it.start, it.endInclusive) }
    val leafs = TreeTraversal.LEAVES_DFS.createIterator<Block>(rootFormattingBlock.subBlocks, { it.subBlocks }).toList()
    assertThat(textRanges).isEqualTo(leafs.map { it.textRange })
  }

  @Test
  fun `test alignment parsing`() {
    val root = getRoot("[a0]foo [a1]goo [a0]hoo")
    val rootBlock = root.toFormattingBlock(0)
    assertLeafRanges(rootBlock, 0..3, 4..7, 8..11)

    val children = rootBlock.subBlocks

    assertThat(children[0].alignment).isNotNull()
    assertThat(children[1].alignment).isNotNull()
    assertThat(children[0].alignment).isEqualTo(children[2].alignment)
  }

  @Test
  fun `test empty block`() {
    val root = getRoot(
"""[a0]fooooo [a1]
[a0]go [a1]boo
""").toFormattingBlock(0)

    val children = root.subBlocks

    assertThat(children).hasSize(4)
    assertThat(children[0].alignment).isEqualTo(children[2].alignment)
    assertThat(children[1].alignment).isEqualTo(children[3].alignment)

    assertThat(children[1].textRange.isEmpty).isTrue()
  }

}
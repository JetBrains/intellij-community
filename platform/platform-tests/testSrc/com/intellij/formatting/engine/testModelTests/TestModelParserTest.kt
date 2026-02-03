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

import com.intellij.formatting.engine.testModel.TestBlock
import com.intellij.formatting.engine.testModel.getRoot
import com.intellij.testFramework.assertions.Assertions.assertThat
import org.junit.Test

class TestModelParserTest {

  @Test
  fun `test simple block parsing`() {
    val children = getBlocks("[a1]block")
    assertThat(children).hasSize(1)

    val child = children[0]
    assertLeaf(child, "a1", "block")
  }

  @Test
  fun `test block with no attributes`() {
    val children = getBlocks("[]block")
    assertThat(children).hasSize(1)

    val child = children[0]
    assertLeaf(child, "", "block")
  }

  @Test
  fun `test empty block with no attributes`() {
    val children = getBlocks("[]")
    assertThat(children).hasSize(1)

    val child = children[0]
    assertLeaf(child, "", "")
  }

  @Test
  fun `test multiple blocks`() {
    val children = getRoot("[a1]foo \n\n\n  \n\n\n [a2]goo").children
    assertThat(children).hasSize(3)

    assertLeaf(children[0], "a1", "foo")
    assertSpace(children[1], " \n\n\n  \n\n\n ")
    assertLeaf(children[2], "a2", "goo")
  }

  @Test
  fun `test simple block with space`() {
    val children = getBlocks("[a1]foo \n ")
    assertThat(children).hasSize(2)

    assertLeaf(children[0], "a1", "foo")
    assertSpace(children[1], " \n ")
  }

  @Test
  fun `test composite block`() {
    var children = getBlocks("[]([]foo  \n \n \n    [a3]goo)")
    assertThat(children).hasSize(1)

    children = (children[0] as TestBlock.Composite).children
    assertThat(children).hasSize(3)
    assertLeaf(children[0], "", "foo")
    assertSpace(children[1], "  \n \n \n    ")
    assertLeaf(children[2], "a3", "goo")
  }

  @Test
  fun `test multiple composite blocks`() {
    var children = getBlocks("[]([]([]foo \n\n\n []goo) \n\n\n  []([]woo))")
    assertThat(children).hasSize(1)

    children = (children[0] as TestBlock.Composite).children
    assertThat(children).hasSize(3)

    assertThat((children[0] as TestBlock.Composite).children).hasSize(3)
    assertThat((children[2] as TestBlock.Composite).children).hasSize(1)
  }

  @Test
  fun `test nested composite blocks`() {
    var children = getBlocks("[a0]([a1]([a2]([a3]([a4]foo))))")

    for (i in 0..3) {
      assertThat(children).hasSize(1)
      val child = children[0] as TestBlock.Composite
      assertThat(child.attributes).isEqualTo("a$i")
      children = child.children
    }

    val leaf = children[0] as TestBlock.Leaf
    assertThat(leaf.attributes).isEqualTo("a4")
    assertThat(leaf.text).isEqualTo("foo")
  }

  private fun assertLeaf(testBlock: TestBlock, attrs: String, text: String) {
    val leaf = testBlock as TestBlock.Leaf
    assertThat(leaf.text).isEqualTo(text)
    assertThat(leaf.attributes).isEqualTo(attrs)
  }

  private fun assertSpace(testBlock: TestBlock, space: String) {
    val leaf = testBlock as TestBlock.Space
    assertThat(leaf.text).isEqualTo(space)
  }

  private fun getBlocks(text: String): MutableList<TestBlock> {
    val root = getRoot(text)
    val children = root.children
    return children
  }

}
// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.tree

import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.SyntaxLanguage
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

internal class SyntaxNodeLeafNavigationTest {
  /**
   * ```
   * root
   *   a
   *     a1
   *     a2
   *   b        (a composite type without children, so a leaf)
   *   c
   *     d
   *       d1
   *     c1
   * ```
   */
  private val root = node("root",
                          node("a", node("a1"), node("a2")),
                          node("b"),
                          node("c", node("d", node("d1")), node("c1")))

  private fun find(name: String): SyntaxNode = root.descendants().single { it.type.toString() == name }

  private fun names(nodes: Sequence<SyntaxNode>): List<String> = nodes.map { it.type.toString() }.toList()

  @Test
  fun `leafRight goes to the next leaf in the same parent`() {
    assertSame(find("a2"), find("a1").leafRight())
  }

  @Test
  fun `leafRight goes up and takes a next sibling without children`() {
    assertSame(find("b"), find("a2").leafRight())
  }

  @Test
  fun `leafRight goes down to the first leaf of the next sibling`() {
    assertSame(find("d1"), find("b").leafRight())
  }

  @Test
  fun `leafRight goes up more than one level`() {
    assertSame(find("c1"), find("d1").leafRight())
  }

  @Test
  fun `leafRight of a composite node skips its own leaves`() {
    assertSame(find("b"), find("a").leafRight())
  }

  @Test
  fun `leafRight of the last leaf and of the root is null`() {
    assertNull(find("c1").leafRight())
    assertNull(root.leafRight())
  }

  @Test
  fun `leafLeft walks the leaves in the reverse order`() {
    assertSame(find("d1"), find("c1").leafLeft())
    assertSame(find("b"), find("d1").leafLeft())
    assertSame(find("a2"), find("b").leafLeft())
    assertSame(find("a1"), find("a2").leafLeft())
    assertNull(find("a1").leafLeft())
  }

  @Test
  fun `leafLeft of a composite node skips its own leaves`() {
    assertSame(find("b"), find("c").leafLeft())
  }

  @Test
  fun `sequenceRight and sequenceLeft list all leaves in the file order`() {
    val leaves = listOf("a1", "a2", "b", "d1", "c1")
    assertEquals(leaves, names(find("a1").sequenceRight()))
    assertEquals(leaves.drop(1), names(find("a1").sequenceRight(excludeSelf = true)))
    assertEquals(leaves.reversed(), names(find("c1").sequenceLeft()))
  }

  @Test
  fun `firstLeaf and lastLeaf go down from a composite node`() {
    assertSame(find("a1"), root.firstLeaf())
    assertSame(find("c1"), root.lastLeaf())
    assertSame(find("d1"), find("c").firstLeaf())
    assertSame(find("d1"), find("d").lastLeaf())
  }

  @Test
  fun `firstLeaf and lastLeaf of a leaf are null`() {
    assertNull(find("a1").firstLeaf())
    assertNull(find("b").lastLeaf())
  }

  @Test
  fun `leafRight and leafLeft walk a deep tree without a stack overflow`() {
    val depth = 100_000
    var deepFirst = node("deep-leaf")
    var deepLast = node("deep-last-leaf")
    repeat(depth) {
      deepFirst = node("first-$it", deepFirst)
      deepLast = node("last-$it", deepLast)
    }
    val deepRoot = node("deep-root", node("start"), deepFirst, deepLast)
    val start = deepRoot.firstChild()!!
    val deepLeaf = start.leafRight()!!
    assertEquals("deep-leaf", deepLeaf.type.toString())
    assertEquals("deep-last-leaf", deepLeaf.leafRight()!!.type.toString())
    assertSame(start, deepLeaf.leafLeft())
  }

  private fun node(name: String, vararg children: TestNode): TestNode = TestNode(SyntaxElementType(name), children.toList())

  /** A tree node for the test. It has only the structure, because the leaf navigation reads no text and no offset. */
  private class TestNode(override val type: SyntaxElementType, val children: List<TestNode>) : SyntaxNode {
    var parent: TestNode? = null
    var index: Int = 0

    init {
      children.forEachIndexed { i, child ->
        child.parent = this
        child.index = i
      }
    }

    override val text: CharSequence get() = throw UnsupportedOperationException()
    override val startOffset: Int get() = throw UnsupportedOperationException()
    override val endOffset: Int get() = throw UnsupportedOperationException()
    override val errorMessage: String? get() = null
    override val language: SyntaxLanguage? get() = null

    override fun parent(): SyntaxNode? = parent
    override fun prevSibling(): SyntaxNode? = parent?.children?.getOrNull(index - 1)
    override fun nextSibling(): SyntaxNode? = parent?.children?.getOrNull(index + 1)
    override fun firstChild(): SyntaxNode? = children.firstOrNull()
    override fun lastChild(): SyntaxNode? = children.lastOrNull()
    override fun childByOffset(offset: Int): SyntaxNode? = throw UnsupportedOperationException()
  }
}

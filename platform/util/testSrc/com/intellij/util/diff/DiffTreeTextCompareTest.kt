// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.diff

import com.intellij.openapi.util.Ref
import com.intellij.util.ThreeState
import com.intellij.util.text.CharArrayExternalizable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Checks how much text [DiffTree] reads to compare the texts of the nodes.
 * Each node has a range of a text, so the texts of the nodes are independent of their types.
 */
class DiffTreeTextCompareTest {
  @Test
  fun `a deep tree with the same text reads each char once`() {
    val text = "abcdefghij".repeat(1_000)
    val oldText = CountingText(text)
    val newText = CountingText(text)

    val events = diff(chain(text), oldText, chain(text), newText)

    assertEquals(emptyList<String>(), events)
    assertTrue(oldText.readChars <= text.length) { "old text: ${oldText.readChars} chars read for ${text.length} chars" }
    assertTrue(newText.readChars <= text.length) { "new text: ${newText.readChars} chars read for ${text.length} chars" }
  }

  @Test
  fun `a changed leaf after the first chunk of a deep tree is replaced`() {
    val oldString = "abcdefghij".repeat(1_000)
    val newString = oldString.substring(0, 5_000) + "X" + oldString.substring(5_001)

    val events = diff(chain(oldString), CountingText(oldString), chain(newString), CountingText(newString))

    assertEquals(listOf("REPLACED: leaf2"), events)
  }
}

private const val DEPTH = 20

/**
 * Builds [DEPTH] nested nodes over all of [text], and three leaves in the innermost node.
 * The middle leaf covers the offset 5000.
 */
private fun chain(text: String): Node {
  var node = Node("inner", 0, text.length, text, listOf(
    Node("leaf1", 0, 4_000, text),
    Node("leaf2", 4_000, 9_000, text),
    Node("leaf3", 9_000, text.length, text),
  ))
  repeat(DEPTH) { node = Node("level$it", 0, text.length, text, listOf(node)) }
  return Node("root", 0, text.length, text, listOf(node))
}

private fun diff(oldRoot: Node, oldText: CountingText, newRoot: Node, newText: CountingText): List<String> {
  val events = ArrayList<String>()
  val builder = object : DiffTreeChangeBuilder<Node, Node> {
    override fun nodeReplaced(oldChild: Node, newChild: Node) {
      events.add("REPLACED: ${newChild.type}")
    }

    override fun nodeDeleted(oldParent: Node, oldNode: Node) {
      events.add("DELETED: ${oldNode.type}")
    }

    override fun nodeInserted(oldParent: Node, newNode: Node, pos: Int) {
      events.add("INSERTED: ${newNode.type}")
    }
  }
  DiffTree.diff(Structure(oldRoot, oldText), Structure(newRoot, newText), Comparator, builder, oldText)
  return events
}

/** A node over the range from [start] to [end] of [fullText]. */
private class Node(val type: String, val start: Int, val end: Int, fullText: String, val children: List<Node> = emptyList()) {
  val text: String = fullText.substring(start, end)
}

private class Structure(private val root: Node, private val text: CountingText) : FlyweightCapableTreeStructure<Node> {
  override fun getRoot(): Node = root

  override fun getParent(node: Node): Node? = null

  override fun getChildren(parent: Node, into: Ref<Array<Node>>): Int {
    into.set(parent.children.toTypedArray())
    return parent.children.size
  }

  override fun disposeChildren(nodes: Array<Node>?, count: Int) = Unit

  override fun toString(node: Node): CharSequence = if (node === root) text else node.text

  override fun getStartOffset(node: Node): Int = node.start

  override fun getEndOffset(node: Node): Int = node.end
}

/** Compares the children of each composite node, and the text of each leaf. */
private object Comparator : ShallowNodeComparator<Node, Node> {
  override fun deepEqual(oldNode: Node, newNode: Node): ThreeState = ThreeState.UNSURE

  override fun typesEqual(oldNode: Node, newNode: Node): Boolean = oldNode.type == newNode.type

  override fun hashCodesEqual(oldNode: Node, newNode: Node): Boolean = oldNode.text == newNode.text
}

/** A text that counts the chars that its reader reads, one by one or in a bulk copy. */
private class CountingText(private val text: String) : CharArrayExternalizable {
  var readChars: Int = 0
    private set

  override val length: Int get() = text.length

  override fun get(index: Int): Char {
    readChars++
    return text[index]
  }

  override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = text.subSequence(startIndex, endIndex)

  override fun getChars(start: Int, end: Int, dest: CharArray, destPos: Int) {
    readChars += end - start
    text.toCharArray(dest, destPos, start, end)
  }

  override fun toString(): String = text
}

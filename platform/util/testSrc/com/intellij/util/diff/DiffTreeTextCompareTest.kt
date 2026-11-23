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

  @Test
  fun `a flat tree with a changed child reads the text in a few bulk copies`() {
    val oldString = flatText(FLAT_CHILDREN)
    val middle = oldString.indexOf("child-${FLAT_CHILDREN / 2};")
    val newString = oldString.substring(0, middle) + "x" + oldString.substring(middle)
    val oldText = CountingText(oldString)
    val newText = CountingText(newString)

    val events = diff(flat(oldString), oldText, flat(newString), newText)

    assertEquals(listOf("REPLACED: leaf xchild-${FLAT_CHILDREN / 2};"), events)
    assertTrue(oldText.reads <= 32) { "old text: ${oldText.reads} reads for ${oldString.length} chars" }
    assertTrue(newText.reads <= 32) { "new text: ${newText.reads} reads for ${newString.length} chars" }
  }

  @Test
  fun `a flat tree with a changed child asks for few offsets`() {
    val oldString = flatText(FLAT_CHILDREN)
    val middle = oldString.indexOf("child-${FLAT_CHILDREN / 2};")
    val newString = oldString.substring(0, middle) + "x" + oldString.substring(middle)
    val oldStructure = Structure(flat(oldString), CountingText(oldString))
    val newStructure = Structure(flat(newString), CountingText(newString))

    val events = diff(oldStructure, newStructure)

    assertEquals(listOf("REPLACED: leaf xchild-${FLAT_CHILDREN / 2};"), events)
    assertTrue(oldStructure.offsetCalls <= 8) { "old tree: ${oldStructure.offsetCalls} offset calls for $FLAT_CHILDREN children" }
    assertTrue(newStructure.offsetCalls <= 8) { "new tree: ${newStructure.offsetCalls} offset calls for $FLAT_CHILDREN children" }
  }

  @Test
  fun `a flat tree with a changed child does not compare the leaf texts of the equal ranges`() {
    val oldString = flatText(FLAT_CHILDREN)
    val middle = oldString.indexOf("child-${FLAT_CHILDREN / 2};")
    val newString = oldString.substring(0, middle) + "x" + oldString.substring(middle)
    val comparator = LeafComparator()

    val events = diff(Structure(flat(oldString), CountingText(oldString)), Structure(flat(newString), CountingText(newString)), comparator)

    assertEquals(listOf("REPLACED: leaf xchild-${FLAT_CHILDREN / 2};"), events)
    assertTrue(comparator.leafTextCompares <= 4) { "${comparator.leafTextCompares} leaf text compares for $FLAT_CHILDREN children" }
  }

  @Test
  fun `a flat tree with an inserted child reports the insertion`() {
    val oldString = flatText(FLAT_CHILDREN)
    val middle = oldString.indexOf("child-${FLAT_CHILDREN / 2};")
    val newString = oldString.substring(0, middle) + "new-child;" + oldString.substring(middle)

    val events = diff(flat(oldString), CountingText(oldString), flat(newString), CountingText(newString))

    assertEquals(listOf("INSERTED: item at ${FLAT_CHILDREN / 2}"), events)
  }

  @Test
  fun `a duplicated child keeps the old node at its old offset`() {
    val oldString = "first;dup;last;"
    val newString = "first;dup;dup;last;"

    val events = diff(flat(oldString), CountingText(oldString), flat(newString), CountingText(newString))

    assertEquals(listOf("INSERTED: item at 2"), events)
  }

  @Test
  fun `a deleted duplicate child keeps the first old node at its old offset`() {
    val oldString = "first;dup;dup;last;"
    val newString = "first;dup;last;"

    val events = diff(flat(oldString), CountingText(oldString), flat(newString), CountingText(newString))

    assertEquals(listOf("DELETED: item dup; at 10"), events)
  }

  @Test
  fun `a child inserted before a node keeps the children of the shifted node`() {
    val old = classes("A" to "{x;}")
    val new = classes("B" to "{}", "A" to "{x;}")

    val events = diff(old, CountingText(old.text), new, CountingText(new.text))

    assertEquals(listOf("INSERTED: class at 0"), events)
  }

  @Test
  fun `a replaced child before a changed child of the same type keeps the changed child`() {
    val old = parts("open" to listOf("{"), "decl" to listOf("int a", "=1;"), "ws" to listOf(" "), "decl" to listOf("int b", "=a;"), "close" to listOf("}"))
    val new = parts("open" to listOf("{"), "expr" to listOf("x;"), "ws" to listOf(" "), "decl" to listOf("int b", "=;"), "close" to listOf("}"))

    val events = diff(old, CountingText(old.text), new, CountingText(new.text))

    assertEquals(listOf("REPLACED: leaf =;", "REPLACED: expr"), events)
  }

  @Test
  fun `a shifted node in the equal suffix stays equal when its children end in the equal prefix`() {
    val oldString = "aaab"
    val newString = "aaaab"
    val oldP = Node("p", 1, 4, oldString, (1 until 4).map { wrapped(it, oldString) })
    val newP = Node("p", 2, 5, newString, (2 until 5).map { wrapped(it, newString) })
    val old = Node("root", 0, 4, oldString, listOf(wrapped(0, oldString), oldP))
    val new = Node("root", 0, 5, newString, listOf(wrapped(0, newString), wrapped(1, newString), newP))
    val comparator = LeafComparator()

    val events = diff(Structure(old, CountingText(oldString)), Structure(new, CountingText(newString)), comparator)

    assertEquals(listOf("INSERTED: wrap at 1"), events)
    assertEquals(0, comparator.leafTextCompares) { "the equal suffix must keep the node p without a leaf text compare" }
  }
}

/** Builds a composite node over the char at [offset] of [text], with one leaf in it. */
private fun wrapped(offset: Int, text: String): Node =
  Node("wrap", offset, offset + 1, text, listOf(Node("leaf ${text[offset]}", offset, offset + 1, text)))

private const val FLAT_CHILDREN = 2_000

/** Returns [count] children of the form `child-N;`. */
private fun flatText(count: Int): String = (0 until count).joinToString("") { "child-$it;" }

/** Builds a root with one item for each `;`-terminated part of [text], and one leaf in each item. The type of a leaf holds its text. */
private fun flat(text: String): Node {
  val items = ArrayList<Node>()
  var start = 0
  while (start < text.length) {
    val end = text.indexOf(';', start) + 1
    items.add(Node("item", start, end, text, listOf(Node("leaf ${text.substring(start, end)}", start, end, text))))
    start = end
  }
  return Node("root", 0, text.length, text, items)
}

/** Builds a root with one child of each type in [parts], and one leaf for each text of a child. The type of a leaf holds its text. */
private fun parts(vararg parts: Pair<String, List<String>>): Node {
  val text = parts.joinToString("") { it.second.joinToString("") }
  val children = ArrayList<Node>()
  var start = 0
  for ((type, leafTexts) in parts) {
    val childStart = start
    val leaves = leafTexts.map { leafText -> Node("leaf $leafText", start, start + leafText.length, text).also { start += leafText.length } }
    children.add(Node(type, childStart, start, text, leaves))
  }
  return Node("root", 0, text.length, text, children)
}

/**
 * Builds a root with one class for each name and body in [classes], like `class A{x;}`.
 * A class has an empty modifier list, a keyword leaf, a name leaf, and a body with one leaf.
 */
private fun classes(vararg classes: Pair<String, String>): Node {
  val text = classes.joinToString("") { (name, body) -> "class $name$body" }
  var start = 0
  val children = classes.map { (name, body) ->
    val classStart = start
    val nameStart = classStart + "class ".length
    val bodyStart = nameStart + name.length
    start = bodyStart + body.length
    Node("class", classStart, start, text, listOf(
      Node("modifiers", classStart, classStart, text, emptyList()),
      Node("leaf class ", classStart, nameStart, text),
      Node("leaf $name", nameStart, bodyStart, text),
      Node("body", bodyStart, start, text, listOf(Node("leaf $body", bodyStart, start, text))),
    ))
  }
  return Node("root", 0, text.length, text, children)
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

private fun diff(oldRoot: Node, oldText: CountingText, newRoot: Node, newText: CountingText): List<String> =
  diff(Structure(oldRoot, oldText), Structure(newRoot, newText))

private fun diff(oldStructure: Structure, newStructure: Structure, comparator: ShallowNodeComparator<Node, Node> = Comparator): List<String> {
  val events = ArrayList<String>()
  val builder = object : DiffTreeChangeBuilder<Node, Node> {
    override fun nodeReplaced(oldChild: Node, newChild: Node) {
      events.add("REPLACED: ${newChild.type}")
    }

    override fun nodeDeleted(oldParent: Node, oldNode: Node) {
      events.add("DELETED: ${oldNode.type} ${oldNode.text} at ${oldNode.start}")
    }

    override fun nodeInserted(oldParent: Node, newNode: Node, pos: Int) {
      events.add("INSERTED: ${newNode.type} at $pos")
    }
  }
  DiffTree.diff(oldStructure, newStructure, comparator, builder, oldStructure.text)
  return events
}

/** A node over the range from [start] to [end] of [fullText]. */
private class Node(val type: String, val start: Int, val end: Int, fullText: String, val children: List<Node> = emptyList()) {
  val text: String = fullText.substring(start, end)
}

/** A tree of [Node]s. Like the structure of an AST, it gives a length without an offset, and it counts the offset calls. */
private class Structure(private val root: Node, val text: CountingText) : FlyweightCapableTreeStructure<Node> {
  var offsetCalls: Int = 0
    private set

  override fun getRoot(): Node = root

  override fun getParent(node: Node): Node? = null

  override fun getChildren(parent: Node, into: Ref<Array<Node>>): Int {
    into.set(parent.children.toTypedArray())
    return parent.children.size
  }

  override fun disposeChildren(nodes: Array<Node>?, count: Int) = Unit

  override fun toString(node: Node): CharSequence = if (node === root) text else node.text

  override fun getStartOffset(node: Node): Int {
    offsetCalls++
    return node.start
  }

  override fun getEndOffset(node: Node): Int {
    offsetCalls++
    return node.end
  }

  override fun getTextLength(node: Node): Int = node.end - node.start
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

  /** The number of reads: a char read and a bulk copy each count as one. */
  var reads: Int = 0
    private set

  override val length: Int get() = text.length

  override fun get(index: Int): Char {
    readChars++
    reads++
    return text[index]
  }

  override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = text.subSequence(startIndex, endIndex)

  override fun getChars(start: Int, end: Int, dest: CharArray, destPos: Int) {
    readChars += end - start
    reads++
    text.toCharArray(dest, destPos, start, end)
  }

  override fun toString(): String = text
}

/** Like a PSI builder comparator, compares the text of each leaf in [deepEqual], and counts these compares. */
private class LeafComparator : ShallowNodeComparator<Node, Node> {
  var leafTextCompares: Int = 0
    private set

  override fun deepEqual(oldNode: Node, newNode: Node): ThreeState {
    if (oldNode.children.isNotEmpty() || newNode.children.isNotEmpty()) return ThreeState.UNSURE
    leafTextCompares++
    return if (oldNode.text == newNode.text) ThreeState.YES else ThreeState.NO
  }

  override fun deepEqualWithEqualText(oldNode: Node, newNode: Node): ThreeState {
    if (oldNode.children.isNotEmpty() || newNode.children.isNotEmpty()) return ThreeState.UNSURE
    return ThreeState.YES
  }

  override fun typesEqual(oldNode: Node, newNode: Node): Boolean = oldNode.type == newNode.type

  override fun hashCodesEqual(oldNode: Node, newNode: Node): Boolean = oldNode.text == newNode.text
}

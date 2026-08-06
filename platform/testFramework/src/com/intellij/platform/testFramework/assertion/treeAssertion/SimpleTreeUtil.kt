// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.assertion.treeAssertion

import com.intellij.platform.testFramework.assertion.treeAssertion.SimpleTreeAssertion.NodeMatcher
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import java.util.StringJoiner

fun <T> SimpleTree<T>.toMutableTree(): SimpleMutableTree<T> =
  mapTree { SimpleTreeImpl.Node(it.name, it.value) }

fun <T> SimpleTree<T>.getTreeString(): String {
  val result = StringJoiner("\n")
  val stack = ArrayDeque<Pair<Int, SimpleTree.Node<T>>>()
  for (root in roots.asReversed()) {
    stack.addLast(0 to root)
  }
  while (stack.isNotEmpty()) {
    val (indent, node) = stack.removeLast()
    val indentString = " ".repeat(indent)
    val nodeMarker = if (node.children.isNotEmpty()) "-" else ""
    result.add(indentString + nodeMarker + node.name)
    for (child in node.children.asReversed()) {
      stack.addLast(indent + 1 to child)
    }
  }
  return result.toString()
}

fun <T> buildTree(configure: SimpleTreeBuilder<T>.() -> Unit): SimpleMutableTree<T> {
  val treeBuilder = SimpleTreeBuilder<T>()
  treeBuilder.configure()
  return treeBuilder.tree
}

fun <T> buildTree(roots: List<T>, nameGetter: T.() -> String, childrenGetter: T.() -> List<T>): SimpleMutableTree<T> {
  val tree = SimpleTreeImpl<T>()
  val queue = ArrayDeque<SimpleTreeImpl.Node<T>>()
  for (root in roots) {
    val node = SimpleTreeImpl.Node(root.nameGetter(), root)
    tree.roots.add(node)
    queue.add(node)
  }
  while (queue.isNotEmpty()) {
    val node = queue.removeLast()
    for (child in node.value.childrenGetter()) {
      val childNode = SimpleTreeImpl.Node(child.nameGetter(), child)
      node.children.add(childNode)
      queue.addFirst(childNode)
    }
  }
  return tree
}

fun buildTree(treeString: String): SimpleMutableTree<Nothing?> {
  val stack = ArrayDeque<Pair<Int, SimpleMutableTree.Node<Nothing?>>>()
  for ((index, nodeString) in treeString.split("\n").withIndex()) {
    val indent = nodeString.length - nodeString.trimStart().length
    val name = nodeString.trimStart().removePrefix("-")
    val node = SimpleTreeImpl.Node(name, null)
    var parentNode = stack.lastOrNull()
    while (parentNode != null && parentNode.first >= indent) {
      stack.removeLast()
      parentNode = stack.lastOrNull()
    }
    require((parentNode?.first ?: -1) == indent - 1) {
      "Incorrect tree structure at $index:\n" +
      treeString
    }
    if (parentNode?.second != null) {
      parentNode.second.children.add(node)
    }
    stack.add(indent to node)
  }
  val tree = SimpleTreeImpl<Nothing?>()
  for ((indent, node) in stack) {
    if (indent == 0) {
      tree.roots.add(node)
    }
  }
  require(treeString == tree.getTreeString()) {
    "Incorrect tree structure:\n" +
    treeString
  }
  return tree
}

fun <T, R> SimpleTree<T>.mapTreeValues(transform: (SimpleTree.Node<T>) -> R): SimpleMutableTree<R> {
  return mapTree { SimpleTreeImpl.Node(it.name, transform(it)) }
}

fun <T, R> SimpleTree<T>.mapTree(transform: (SimpleTree.Node<T>) -> SimpleMutableTree.Node<R>): SimpleMutableTree<R> {
  val tree = SimpleTreeImpl<R>()
  val queue = ArrayDeque<Pair<SimpleTree.Node<T>, SimpleMutableTree.Node<R>>>()
  for (oldRootNode in roots) {
    val newRootNode = transform(oldRootNode)
    tree.roots.add(newRootNode)
    queue.add(oldRootNode to newRootNode)
  }
  while (queue.isNotEmpty()) {
    val (oldNode, newNode) = queue.removeLast()
    for (oldChildNode in oldNode.children) {
      val newChildNode = transform(oldChildNode)
      newNode.children.add(newChildNode)
      queue.addFirst(oldChildNode to newChildNode)
    }
  }
  return tree
}

fun <T> SimpleTree<T>.node(name: String): SimpleTree.Node<T> =
  node(NodeMatcher.name(name))

fun <T> SimpleTree<T>.node(regex: Regex): SimpleTree.Node<T> =
  node(NodeMatcher.regex(regex))

fun <T> SimpleTree<T>.node(matcher: NodeMatcher<T>): SimpleTree.Node<T> {
  val matchedNodes = allNodes().filter { matcher.matches(it) }.toList()
  assertTrue(matchedNodes.isNotEmpty()) { "Cannot find node by NodeMatcher: $matcher" }
  assertEquals(1, matchedNodes.size) {
    "Cannot identify node by NodeMatcher: $matcher\n" +
    " matchedNodes.names=${matchedNodes.map { it.name }}\n" +
    " matchedNodes=$matchedNodes"
  }
  return matchedNodes.single()
}

fun <T> SimpleTree<T>.allNodes(): Sequence<SimpleTree.Node<T>> =
  Sequence {
    object : Iterator<SimpleTree.Node<T>> {
      val stack = ArrayDeque(roots)
      override fun hasNext() = stack.isNotEmpty()
      override fun next(): SimpleTree.Node<T> {
        val node = stack.removeLast()
        stack.addAll(node.children)
        return node
      }
    }
  }
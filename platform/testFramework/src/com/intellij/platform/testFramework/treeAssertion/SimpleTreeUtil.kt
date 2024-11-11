// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.treeAssertion

import java.util.*
import kotlin.collections.ArrayDeque

fun <T> SimpleTree<T>.deepCopyTree(): SimpleMutableTree<T> {
  val queue = ArrayDeque<Pair<SimpleTree.Node<T>, SimpleMutableTree.Node<T>>>()
  val tree = SimpleTreeImpl<T>()
  for (root in roots) {
    val mutableRoot = SimpleTreeImpl.Node(root.name, root.value)
    tree.roots.add(mutableRoot)
    queue.add(root to mutableRoot)
  }
  while (queue.isNotEmpty()) {
    val (node, mutableNode) = queue.removeFirst()
    for (child in node.children) {
      val mutableChild = SimpleTreeImpl.Node(child.name, child.value)
      mutableNode.children.add(mutableChild)
      queue.add(child to mutableChild)
    }
  }
  return tree
}

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
// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.util.tree

import org.junit.jupiter.api.AssertionFailureBuilder

class TreeAssertion<T>(
  actualTree: Tree<T>,
  expectedTree: MutableTree<NodeMatcher<T>>
) : AbstractNodeAssertion<T>(actualTree, emptyList()) {

  override val actualSiblings = actualTree.roots.toMutableList()
  override val expectedSiblings = expectedTree.roots

  class Node<T>(
    actualTree: Tree<T>,
    actualPath: List<String>,
    node: Tree.Node<T>?,
    expectedNode: MutableTree.Node<NodeMatcher<T>>
  ) : AbstractNodeAssertion<T>(actualTree, actualPath) {

    override val actualSiblings = node?.children?.toMutableList() ?: ArrayList()
    override val expectedSiblings = expectedNode.children

    val value: T by lazy {
      if (node == null) {
        throw AssertionError(
          "Expected node by path $actualPath in tree:\n" +
          actualTree.getTreeString()
        )
      }
      node.value
    }
  }

  companion object {

    fun <T> assertTree(actualTree: Tree<T>, assert: TreeAssertion<T>.() -> Unit) {
      val expectedTree = SimpleTree<NodeMatcher<T>>()
      val assertion = TreeAssertion(actualTree, expectedTree)
      assertion.assert()
      assertTree(expectedTree, actualTree)
    }

    private fun <T> assertTree(expectedTree: Tree<NodeMatcher<T>>, actualTree: Tree<T>) {
      val queue = ArrayDeque<Pair<List<Tree.Node<NodeMatcher<T>>>, List<Tree.Node<T>>>>()
      queue.add(expectedTree.roots to actualTree.roots)
      while (queue.isNotEmpty()) {
        val (expectedNodes, actualNodes) = queue.removeFirst()
        for ((expectedNode, actualNode) in expectedNodes.zip(actualNodes)) {
          if (!expectedNode.value.matches(actualNode)) {
            throwTreeAssertionError(expectedTree, actualTree)
          }
          if (expectedNode.children.size != actualNode.children.size) {
            throwTreeAssertionError(expectedTree, actualTree)
          }
          queue.add(expectedNode.children to actualNode.children)
        }
      }
    }

    private fun <T> throwTreeAssertionError(expectedTree: Tree<NodeMatcher<T>>, actualTree: Tree<T>) {
      throw AssertionFailureBuilder.assertionFailure()
        .expected(expectedTree.getTreeString())
        .actual(actualTree.getTreeString())
        .build()
    }
  }
}

abstract class AbstractNodeAssertion<T>(
  private val actualTree: Tree<T>,
  private val actualPath: List<String>
) {

  protected abstract val actualSiblings: MutableList<Tree.Node<T>>
  protected abstract val expectedSiblings: MutableList<MutableTree.Node<NodeMatcher<T>>>

  fun assertNode(name: String, assert: TreeAssertion.Node<T>.() -> Unit = {}) =
    assertNode(NodeMatcher.Name(name), assert)

  fun assertNode(regex: Regex, assert: TreeAssertion.Node<T>.() -> Unit = {}) =
    assertNode(NodeMatcher.NameRegex(regex), assert)

  private fun assertNode(matcher: NodeMatcher<T>, assert: TreeAssertion.Node<T>.() -> Unit) {
    val displayName = matcher.displayName
    val expectedChild = SimpleTree.Node(displayName, matcher)
    expectedSiblings.add(expectedChild)
    val index = actualSiblings.indexOfFirst(matcher::matches)
    val actualChild = when {
      index < 0 -> null
      else -> actualSiblings.removeAt(index)
    }
    val assertion = TreeAssertion.Node(actualTree, actualPath + displayName, actualChild, expectedChild)
    assertion.assert()
  }
}

sealed interface NodeMatcher<T> {

  val displayName: String

  fun matches(node: Tree.Node<T>): Boolean

  class Name<T>(
    private val name: String
  ) : NodeMatcher<T> {

    override val displayName: String = name

    override fun matches(node: Tree.Node<T>): Boolean {
      return node.name == name
    }
  }

  class NameRegex<T>(
    private val regex: Regex
  ) : NodeMatcher<T> {

    override val displayName: String = regex.toString()

    override fun matches(node: Tree.Node<T>): Boolean {
      return regex.matches(node.name)
    }
  }
}


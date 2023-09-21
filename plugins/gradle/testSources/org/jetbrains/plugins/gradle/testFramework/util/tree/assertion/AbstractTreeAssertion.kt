// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.util.tree.assertion

import org.jetbrains.plugins.gradle.testFramework.util.tree.*
import org.junit.jupiter.api.AssertionFailureBuilder

internal abstract class AbstractTreeAssertion<T> private constructor(
  private val expectedChildren: MutableList<MutableTree.Node<NodeAssertionOptions<T>>>
) : TreeAssertion<T> {

  // @formatter:off
  override fun assertNode(name: String, flattenIf: Boolean, skipIf: Boolean, isUnordered: Boolean, assert: TreeAssertion.Node<T>.() -> Unit) =
    assertNode(NodeAssertionOptions(NodeMatcher.Name(name), flattenIf, skipIf, isUnordered), assert)
  override fun assertNode(regex: Regex, flattenIf: Boolean, skipIf: Boolean, isUnordered: Boolean, assert: TreeAssertion.Node<T>.() -> Unit) =
    assertNode(NodeAssertionOptions(NodeMatcher.NameRegex(regex), flattenIf, skipIf, isUnordered), assert)
  // @formatter:on

  private fun assertNode(options: NodeAssertionOptions<T>, assert: TreeAssertion.Node<T>.() -> Unit) {
    if (options.skipIf) {
      return
    }
    if (options.flattenIf) {
      val assertion = FlattenedNodeAssertionImpl(this)
      assertion.assert()
      return
    }
    val displayName = options.matcher.displayName
    val expectedChild = SimpleTree.Node(displayName, options)
    expectedChildren.add(expectedChild)
    val assertion = NodeAssertionImpl(expectedChild)
    assertion.assert()
  }

  private class TreeAssertionImpl<T>(
    expectedTree: MutableTree<NodeAssertionOptions<T>>
  ) : AbstractTreeAssertion<T>(expectedTree.roots)

  private class NodeAssertionImpl<T>(
    private val expectedNode: MutableTree.Node<NodeAssertionOptions<T>>
  ) : AbstractTreeAssertion<T>(expectedNode.children), TreeAssertion.Node<T> {

    override fun assertValue(assert: (T) -> Unit) {
      expectedNode.value.valueAssertion = assert
    }
  }

  private class FlattenedNodeAssertionImpl<T>(
    parentAssertion: AbstractTreeAssertion<T>
  ) : AbstractTreeAssertion<T>(parentAssertion.expectedChildren), TreeAssertion.Node<T> {

    override fun assertValue(assert: (T) -> Unit) {}
  }

  private class NodeAssertionOptions<T>(
    val matcher: NodeMatcher<T>,
    val flattenIf: Boolean,
    val skipIf: Boolean,
    val isUnordered: Boolean,
    var valueAssertion: (T) -> Unit = {},
  )

  companion object {

    fun <T> assertTree(actualTree: Tree<T>, isUnordered: Boolean, assert: TreeAssertion<T>.() -> Unit) {
      val actualMutableTree = actualTree.toMutableTree()
      val expectedMutableTree = SimpleTree<NodeAssertionOptions<T>>()
      val assertion = TreeAssertionImpl(expectedMutableTree)
      assertion.assert()
      sortTree(expectedMutableTree, actualMutableTree, isUnordered)
      assertTree(expectedMutableTree, actualMutableTree)
    }

    private fun <T> assertTree(expectedTree: Tree<NodeAssertionOptions<T>>, actualTree: Tree<T>) {
      val queue = ArrayDeque<Pair<List<Tree.Node<NodeAssertionOptions<T>>>, List<Tree.Node<T>>>>()
      queue.add(expectedTree.roots to actualTree.roots)
      while (queue.isNotEmpty()) {
        val (expectedNodes, actualNodes) = queue.removeFirst()
        if (expectedNodes.size != actualNodes.size) {
          throwTreeAssertionError(expectedTree, actualTree)
        }
        for ((expectedNode, actualNode) in expectedNodes.zip(actualNodes)) {
          if (!expectedNode.value.matcher.matches(actualNode)) {
            throwTreeAssertionError(expectedTree, actualTree)
          }
          expectedNode.value.valueAssertion(actualNode.value)
          queue.add(expectedNode.children to actualNode.children)
        }
      }
    }

    private fun <T> throwTreeAssertionError(expectedTree: Tree<NodeAssertionOptions<T>>, actualTree: Tree<T>): Nothing {
      throw AssertionFailureBuilder.assertionFailure()
        .expected(expectedTree.getTreeString())
        .actual(actualTree.getTreeString())
        .build()
    }

    private fun <T> sortTree(
      expectedTree: MutableTree<NodeAssertionOptions<T>>,
      actualTree: MutableTree<T>,
      isUnordered: Boolean
    ) {
      val queue = ArrayDeque<Pair<
        MutableList<MutableTree.Node<NodeAssertionOptions<T>>>,
        MutableList<MutableTree.Node<T>>
        >>()
      queue.add(expectedTree.roots to actualTree.roots)
      while (queue.isNotEmpty()) {
        val (expectedNodes, actualNodes) = queue.removeFirst()

        // Partition expected nodes
        val (expectedUnorderedNodes, expectedOrderedNodes) =
          expectedNodes.partition { isUnordered || it.value.isUnordered }

        // Partition actual nodes in order of expected nodes
        val actualUnorderedNodes = ArrayList<MutableTree.Node<T>>()
        val actualOrderedNodes = ArrayList(actualNodes)
        for (expectedUnorderedNode in expectedUnorderedNodes) {
          val index = actualOrderedNodes.indexOfFirst { expectedUnorderedNode.value.matcher.matches(it) }
          if (index >= 0) {
            val actualUnorderedNode = actualOrderedNodes.removeAt(index)
            actualUnorderedNodes.add(actualUnorderedNode)
          }
        }

        // Sort origin expected and actual trees by ordering options from an expected tree
        expectedNodes.clear()
        expectedNodes.addAll(expectedOrderedNodes)
        expectedNodes.addAll(expectedUnorderedNodes)
        actualNodes.clear()
        actualNodes.addAll(actualOrderedNodes)
        actualNodes.addAll(actualUnorderedNodes)

        for ((expectedNode, actualNode) in expectedNodes.zip(actualNodes)) {
          queue.add(expectedNode.children to actualNode.children)
        }
      }
    }
  }
}
// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.treeAssertion

import org.assertj.core.api.Assertions.assertThat

internal class SimpleTreeAssertionImpl<T> private constructor() : SimpleTreeAssertion.Node<T> {
  private val expectedChildren = ArrayList<SimpleTree.Node<NodeAssertionOptions<T>>>()

  private var valueAssertions = ArrayList<(T) -> Unit>()

  override fun assertValue(assert: (T) -> Unit) {
    valueAssertions.add(assert)
  }

  // @formatter:off
  override fun assertNode(name: String, flattenIf: Boolean, skipIf: Boolean, isUnordered: Boolean, assert: SimpleTreeAssertion.Node<T>.() -> Unit) =
    assertNode(NodeAssertionOptions(NodeMatcher.Name(name), flattenIf, skipIf, isUnordered), assert)
  override fun assertNode(regex: Regex, flattenIf: Boolean, skipIf: Boolean, isUnordered: Boolean, assert: SimpleTreeAssertion.Node<T>.() -> Unit) =
    assertNode(NodeAssertionOptions(NodeMatcher.NameRegex(regex), flattenIf, skipIf, isUnordered), assert)
  // @formatter:on

  private fun assertNode(options: NodeAssertionOptions<T>, assert: SimpleTreeAssertion.Node<T>.() -> Unit) {
    if (options.skipIf) {
      return
    }
    if (options.flattenIf) {
      val assertion = buildTreeAssertion(assert)
      expectedChildren.addAll(assertion.expectedChildren)
      return
    }
    val displayName = options.matcher.displayName
    val expectedChild = SimpleTree.Node(displayName, options)
    val childAssertion = buildTreeAssertion(assert)
    expectedChild.children.addAll(childAssertion.expectedChildren)
    expectedChild.value.valueAssertions = childAssertion.valueAssertions
    expectedChildren.add(expectedChild)
  }

  private class NodeAssertionOptions<T>(
    val matcher: NodeMatcher<T>,
    val flattenIf: Boolean,
    val skipIf: Boolean,
    val isUnordered: Boolean,
    var valueAssertions: List<(T) -> Unit> = ArrayList()
  )

  private sealed interface NodeMatcher<T> {

    val displayName: String

    fun matches(node: SimpleTree.Node<T>): Boolean

    class Name<T>(
      private val name: String
    ) : NodeMatcher<T> {

      override val displayName: String = name

      override fun matches(node: SimpleTree.Node<T>): Boolean {
        return node.name == name
      }
    }

    class NameRegex<T>(
      private val regex: Regex
    ) : NodeMatcher<T> {

      override val displayName: String = regex.toString()

      override fun matches(node: SimpleTree.Node<T>): Boolean {
        return regex.matches(node.name)
      }
    }
  }

  companion object {

    fun <T> assertTree(actualTree: SimpleTree<T>, isUnordered: Boolean, assert: SimpleTreeAssertion<T>.() -> Unit) {
      val actualMutableTree = actualTree.deepCopyTree()
      val expectedMutableTree = SimpleTree<NodeAssertionOptions<T>>()
      val assertion = buildTreeAssertion(assert)
      expectedMutableTree.roots.addAll(assertion.expectedChildren)
      sortTree(expectedMutableTree, actualMutableTree, isUnordered)
      assertTree(expectedMutableTree, actualMutableTree)
    }

    private fun <T> buildTreeAssertion(
      assert: SimpleTreeAssertion.Node<T>.() -> Unit
    ): SimpleTreeAssertionImpl<T> {
      val assertion = SimpleTreeAssertionImpl<T>()
      assertion.assert()
      return assertion
    }

    private fun <T> assertTree(expectedTree: SimpleTree<NodeAssertionOptions<T>>, actualTree: SimpleTree<T>) {
      val queue = ArrayDeque<Pair<List<SimpleTree.Node<NodeAssertionOptions<T>>>, List<SimpleTree.Node<T>>>>()
      queue.add(expectedTree.roots to actualTree.roots)
      while (queue.isNotEmpty()) {
        val (expectedNodes, actualNodes) = queue.removeFirst()
        if (expectedNodes.size != actualNodes.size) {
          assertThat(actualTree.getTreeString()).isEqualTo(expectedTree.getTreeString())
        }
        for ((expectedNode, actualNode) in expectedNodes.zip(actualNodes)) {
          if (!expectedNode.value.matcher.matches(actualNode)) {
            assertThat(actualTree.getTreeString()).isEqualTo(expectedTree.getTreeString())
          }
          for (valueAssertion in expectedNode.value.valueAssertions) {
            valueAssertion.invoke(actualNode.value)
          }
          queue.add(expectedNode.children to actualNode.children)
        }
      }
    }

    private fun <T> sortTree(
      expectedTree: SimpleTree<NodeAssertionOptions<T>>,
      actualTree: SimpleTree<T>,
      isUnordered: Boolean
    ) {
      val queue = ArrayDeque<Pair<
        MutableList<SimpleTree.Node<NodeAssertionOptions<T>>>,
        MutableList<SimpleTree.Node<T>>
        >>()
      queue.add(expectedTree.roots to actualTree.roots)
      while (queue.isNotEmpty()) {
        val (expectedNodes, actualNodes) = queue.removeFirst()

        // Partition expected nodes
        val (expectedUnorderedNodes, expectedOrderedNodes) =
          expectedNodes.partition { isUnordered || it.value.isUnordered }

        // Partition actual nodes in order of expected nodes
        val actualUnorderedNodes = ArrayList<SimpleTree.Node<T>>()
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
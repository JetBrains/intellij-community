// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.util.tree.assertion

import org.jetbrains.plugins.gradle.testFramework.util.tree.MutableTree
import org.jetbrains.plugins.gradle.testFramework.util.tree.SimpleTree
import org.jetbrains.plugins.gradle.testFramework.util.tree.Tree
import org.jetbrains.plugins.gradle.testFramework.util.tree.getTreeString
import org.junit.jupiter.api.AssertionFailureBuilder

internal abstract class AbstractTreeAssertion<T>(
  protected val actualTree: Tree<T>,
  protected val actualPath: List<String>
) : TreeAssertion<T> {

  protected abstract val actualChildren: MutableList<Tree.Node<T>>
  protected abstract val expectedChildren: MutableList<MutableTree.Node<NodeMatcher<T>>>

  override fun assertNode(name: String, assert: TreeAssertion.Node<T>.() -> Unit) =
    assertNode(NodeMatcher.Name(name), assert)

  override fun assertNode(regex: Regex, assert: TreeAssertion.Node<T>.() -> Unit) =
    assertNode(NodeMatcher.NameRegex(regex), assert)

  protected fun assertNode(matcher: NodeMatcher<T>, assert: TreeAssertion.Node<T>.() -> Unit) {
    val displayName = matcher.displayName
    val expectedChild = SimpleTree.Node(displayName, matcher)
    expectedChildren.add(expectedChild)
    val index = actualChildren.indexOfFirst(matcher::matches)
    val actualChild = when {
      index < 0 -> null
      else -> actualChildren.removeAt(index)
    }
    val assertion = NodeAssertionImpl(actualTree, actualPath + displayName, actualChild, expectedChild)
    assertion.assert()
  }

  abstract class Node<T>(
    actualTree: Tree<T>,
    actualPath: List<String>
  ) : AbstractTreeAssertion<T>(actualTree, actualPath), TreeAssertion.Node<T> {

    override fun assertNode(name: String, flattenIf: Boolean, assert: TreeAssertion.Node<T>.() -> Unit) =
      assertNode(NodeMatcher.Name(name), flattenIf, assert)

    override fun assertNode(regex: Regex, flattenIf: Boolean, assert: TreeAssertion.Node<T>.() -> Unit) =
      assertNode(NodeMatcher.NameRegex(regex), flattenIf, assert)

    protected fun assertNode(matcher: NodeMatcher<T>, flattenIf: Boolean, assert: TreeAssertion.Node<T>.() -> Unit) {
      if (!flattenIf) {
        assertNode(matcher, assert)
        return
      }
      val assertion = SkippedNodeAssertionImpl(this)
      assertion.assert()
    }
  }

  class TreeAssertionImpl<T>(
    actualTree: Tree<T>,
    expectedTree: MutableTree<NodeMatcher<T>>
  ) : AbstractTreeAssertion<T>(actualTree, emptyList()) {

    override val actualChildren = actualTree.roots.toMutableList()
    override val expectedChildren = expectedTree.roots
  }

  private class NodeAssertionImpl<T>(
    actualTree: Tree<T>,
    actualPath: List<String>,
    private val node: Tree.Node<T>?,
    expectedNode: MutableTree.Node<NodeMatcher<T>>
  ) : Node<T>(actualTree, actualPath) {

    override val actualChildren = node?.children?.toMutableList() ?: ArrayList()
    override val expectedChildren = expectedNode.children

    override val value: T
      get() {
        if (node == null) {
          throw AssertionError(
            "Expected node by path $actualPath in tree:\n" +
            actualTree.getTreeString()
          )
        }
        return node.value
      }

    override fun assertValueIfPresent(assert: (T) -> Unit) {
      if (node != null) {
        assert(node.value)
      }
    }
  }

  private class SkippedNodeAssertionImpl<T>(
    parentNode: Node<T>
  ) : Node<T>(
    parentNode.actualTree,
    parentNode.actualPath
  ) {

    override val actualChildren = parentNode.actualChildren
    override val expectedChildren = parentNode.expectedChildren

    override val value: Nothing
      get() {
        throw AssertionError(
          "Expected node by path $actualPath in tree:\n" +
          actualTree.getTreeString()
        )
      }

    override fun assertValueIfPresent(assert: (T) -> Unit) {}
  }

  companion object {

    fun <T> assertTree(actualTree: Tree<T>, assert: TreeAssertion<T>.() -> Unit) {
      val expectedTree = SimpleTree<NodeMatcher<T>>()
      val assertion = TreeAssertionImpl(actualTree, expectedTree)
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
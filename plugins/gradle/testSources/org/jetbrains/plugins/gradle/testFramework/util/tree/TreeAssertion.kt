// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.util.tree

import org.junit.jupiter.api.AssertionFailureBuilder
import org.junit.jupiter.api.Assertions

class TreeAssertion<T>(
  actualTree: Tree<T>,
  expectedTree: MutableTree<Nothing?>
) : AbstractNodeAssertion<T>(actualTree, emptyList()) {

  override val actualSiblings = actualTree.roots.toMutableList()
  override val expectedSiblings = expectedTree.roots

  class Node<T>(
    actualTree: Tree<T>,
    actualPath: List<String>,
    node: Tree.Node<T>?,
    expectedNode: MutableTree.Node<Nothing?>
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
      val expectedTree = SimpleTree<Nothing?>()
      val assertion = TreeAssertion(actualTree, expectedTree)
      assertion.assert()
      val expectedTreeString = expectedTree.getTreeString()
      val actualTreeString = actualTree.getTreeString()
      Assertions.assertEquals(expectedTreeString, actualTreeString)
    }

    fun <T> assertMatchesTree(actualTree: Tree<T>, assert: TreeAssertion<T>.() -> Unit) {
      val expectedTree = SimpleTree<Nothing?>()
      val assertion = TreeAssertion(actualTree, expectedTree)
      assertion.assert()
      val expectedTreeString = expectedTree.getTreeString()
      val actualTreeString = actualTree.getTreeString()
      if (!actualTreeString.startsWith(expectedTreeString)) {
        throw AssertionFailureBuilder.assertionFailure()
          .message("Actual tree don't matches expected part")
          .expected(expectedTreeString)
          .actual(actualTreeString)
          .build()
      }
    }
  }
}

abstract class AbstractNodeAssertion<T>(
  private val actualTree: Tree<T>,
  private val actualPath: List<String>
) {

  protected abstract val actualSiblings: MutableList<Tree.Node<T>>
  protected abstract val expectedSiblings: MutableList<MutableTree.Node<Nothing?>>

  fun assertNode(name: String, assert: TreeAssertion.Node<T>.() -> Unit = {}) {
    val expectedChild = SimpleTree.Node(name, null)
    expectedSiblings.add(expectedChild)
    val index = actualSiblings.indexOfFirst { it.name == name }
    val actualChild = when {
      index < 0 -> null
      else -> actualSiblings.removeAt(index)
    }
    val assertion = TreeAssertion.Node(actualTree, actualPath + name, actualChild, expectedChild)
    assertion.assert()
  }
}


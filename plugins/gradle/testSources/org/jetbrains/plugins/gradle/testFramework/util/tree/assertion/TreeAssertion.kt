// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.util.tree.assertion

import org.jetbrains.plugins.gradle.testFramework.util.tree.Tree

interface TreeAssertion<T> {

  /**
   * Defines assertion for next child node.
   *
   * @param name is node name matcher on equals
   * @param assert builds a list of child assertions
   * @param flattenIf skips this node assertion (name and value),
   * but it applies child assertions as if they are on the same level with this node assertion.
   * @param skipIf skips this node assertion with them child assertions.
   * @param isUnordered finds node for assertion out of sibling definition order.
   */
  fun assertNode(
    name: String,
    flattenIf: Boolean = false,
    skipIf: Boolean = false,
    isUnordered: Boolean = false,
    assert: Node<T>.() -> Unit = {}
  )

  /**
   * Defines assertion for next child node.
   *
   * @param regex is node name matcher
   * @param assert builds a list of child assertions
   * @param flattenIf skips this node assertion (name and value),
   * but it applies child assertions as if they are on the same level with this node assertion.
   * @param skipIf skips this node assertion with them child assertions.
   * @param isUnordered finds node for assertion out of sibling definition order.
   */
  fun assertNode(
    regex: Regex,
    flattenIf: Boolean = false,
    skipIf: Boolean = false,
    isUnordered: Boolean = false,
    assert: Node<T>.() -> Unit = {}
  )

  interface Node<T> : TreeAssertion<T> {

    /**
     * Postpones value assertion after assertion of tree structure.
     * Assertion will be skipped if the structure is incorrect or the current node is flattened.
     */
    fun assertValue(assert: (T) -> Unit)
  }

  companion object {

    fun <T> assertTree(actualTree: Tree<T>, isUnordered: Boolean = false, assert: TreeAssertion<T>.() -> Unit) =
      TreeAssertionImpl.assertTree(actualTree, isUnordered, assert)
  }
}
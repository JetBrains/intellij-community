// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.util.tree.assertion

import org.jetbrains.plugins.gradle.testFramework.util.tree.Tree

interface TreeAssertion<T> {

  fun assertNode(name: String, assert: Node<T>.() -> Unit = {})

  fun assertNode(regex: Regex, assert: Node<T>.() -> Unit = {})

  interface Node<T> : TreeAssertion<T> {

    /**
     * Postpones value assertion after assertion of tree structure.
     * Assertion will be skipped if the structure is incorrect or the current is flattened.
     */
    fun assertValueIfPresent(assert: (T) -> Unit)

    fun assertNode(name: String, flattenIf: Boolean, assert: Node<T>.() -> Unit = {})

    fun assertNode(regex: Regex, flattenIf: Boolean, assert: Node<T>.() -> Unit = {})
  }

  companion object {

    fun <T> assertTree(actualTree: Tree<T>, isUnordered: Boolean = false, assert: TreeAssertion<T>.() -> Unit) =
      AbstractTreeAssertion.assertTree(actualTree, isUnordered, assert)
  }
}
// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.treeAssertion

class SimpleTreeBuilder<T> {

  val tree = SimpleTreeImpl<T>()

  fun root(name: String, value: T, configure: Node<T>.() -> Unit = {}) {
    val nodeBuilder = Node(name, value)
    nodeBuilder.configure()
    tree.roots.add(nodeBuilder.node)
  }

  class Node<T>(name: String, value: T) {

    val node = SimpleTreeImpl.Node(name, value)

    fun node(name: String, value: T, configure: Node<T>.() -> Unit = {}) {
      val nodeBuilder = Node(name, value)
      nodeBuilder.configure()
      node.children.add(nodeBuilder.node)
    }
  }
}
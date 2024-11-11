// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.treeAssertion

interface SimpleMutableTree<T>: SimpleTree<T> {

  override val roots: MutableList<Node<T>>

  interface Node<T>: SimpleTree.Node<T> {

    override var name: String

    override var value: T

    override val children: MutableList<Node<T>>
  }
}
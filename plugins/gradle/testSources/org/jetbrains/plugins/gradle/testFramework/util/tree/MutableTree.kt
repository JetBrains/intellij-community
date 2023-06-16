// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.util.tree

interface MutableTree<T> : Tree<T> {

  override val roots: MutableList<Node<T>>

  interface Node<T> : Tree.Node<T> {

    override var name: String

    override var value: T

    override val children: MutableList<Node<T>>
  }
}
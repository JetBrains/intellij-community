// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.util.tree

class SimpleTree<T> : MutableTree<T> {

  override val roots: MutableList<MutableTree.Node<T>> = ArrayList()

  class Node<T>(
    override var name: String,
    override var value: T
  ) : MutableTree.Node<T> {

    override val children: MutableList<MutableTree.Node<T>> = ArrayList()
  }
}
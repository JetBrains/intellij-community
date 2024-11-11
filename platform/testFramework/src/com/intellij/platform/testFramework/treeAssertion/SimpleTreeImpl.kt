// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.treeAssertion

data class SimpleTreeImpl<T>(
  override val roots: MutableList<SimpleMutableTree.Node<T>> = ArrayList(),
) : SimpleMutableTree<T> {

  data class Node<T>(
    override var name: String,
    override var value: T,
    override val children: MutableList<SimpleMutableTree.Node<T>> = ArrayList(),
  ) : SimpleMutableTree.Node<T>
}
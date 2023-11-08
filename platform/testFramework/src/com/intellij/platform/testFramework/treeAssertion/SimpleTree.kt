// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.treeAssertion

class SimpleTree<T> {

  val roots: MutableList<Node<T>> = ArrayList()

  class Node<T>(
    var name: String,
    var value: T
  ) {

    val children: MutableList<Node<T>> = ArrayList()
  }
}
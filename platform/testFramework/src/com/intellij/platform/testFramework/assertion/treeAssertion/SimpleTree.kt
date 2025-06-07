// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.assertion.treeAssertion

interface SimpleTree<T> {

  val roots: List<Node<T>>

  interface Node<T> {

    val name: String

    val value: T

    val children: List<Node<T>>
  }
}
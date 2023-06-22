// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.util.tree.assertion

import org.jetbrains.plugins.gradle.testFramework.util.tree.Tree

internal sealed interface NodeMatcher<T> {

  val displayName: String

  fun matches(node: Tree.Node<T>): Boolean

  class Name<T>(
    private val name: String
  ) : NodeMatcher<T> {

    override val displayName: String = name

    override fun matches(node: Tree.Node<T>): Boolean {
      return node.name == name
    }
  }

  class NameRegex<T>(
    private val regex: Regex
  ) : NodeMatcher<T> {

    override val displayName: String = regex.toString()

    override fun matches(node: Tree.Node<T>): Boolean {
      return regex.matches(node.name)
    }
  }
}
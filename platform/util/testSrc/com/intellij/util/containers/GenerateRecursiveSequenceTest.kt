// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers

import com.intellij.testFramework.UsefulTestCase

class GenerateRecursiveSequenceTest : UsefulTestCase() {
  fun testEmpty() {
    assertTrue(generateRecursiveSequence<String>(emptySequence()) { s -> s.lineSequence() }.toList().isEmpty())

    generateRecursiveSequence<String>(emptySequence()) {
      fail("No children. So this should not be called")
      emptySequence()
    }.firstOrNull()
  }

  fun testGraph() {
    class Node(val name: String) {
      val children = mutableListOf<Node>()
    }

    val n1 = Node("n1")
    val n2 = Node("n2")
    val n3 = Node("n3")
    val n4 = Node("n4")
    val n5 = Node("n5")

    n1.children += n2

    n1.children += n3

    n2.children += n1
    n2.children += n3

    n3.children += n4
    n3.children += n2
    n3.children += n5

    val names = generateRecursiveSequence(sequenceOf(n1)) { n -> n.children.asSequence() }.map { it.name }.toList()
    assertOrderedEquals(names, "n1", "n2", "n3", "n4", "n5")

    generateRecursiveSequence(sequenceOf(n1)) { n ->
      assertTrue(n != n3)
      n.children.asSequence()
    }.map { it.name }.find { it == "n2" }

    val visitedNodes = mutableListOf<Node>()
    generateRecursiveSequence(sequenceOf(n1)) { n ->
      visitedNodes += n
      n.children.asSequence()
    }.last()
    assertTrue(visitedNodes.size == 5 && visitedNodes.distinct().size == 5)

  }
}
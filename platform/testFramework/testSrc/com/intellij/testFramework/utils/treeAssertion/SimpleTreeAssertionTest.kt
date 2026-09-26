// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.utils.treeAssertion

import com.intellij.platform.testFramework.assertion.treeAssertion.SimpleTreeAssertion
import com.intellij.platform.testFramework.assertion.treeAssertion.SimpleTreeAssertion.NodeMatcher
import com.intellij.platform.testFramework.assertion.treeAssertion.SimpleTreeAssertion.NodeMatcher.Companion.or
import com.intellij.platform.testFramework.assertion.treeAssertion.buildTree
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class SimpleTreeAssertionTest {

  @Test
  fun `test SimpleTreeAssertion#assertTreeEquals for names`() {
    val tree = buildTree {
      root("1", 1) {
        node("1.1", 2) {
          node("1.1.1", 3)
          node("1.1.2", 4) {
            node("1.1.2.1", 5)
            node("1.1.2.2", 6)
            node("1.1.2.3", 7)
            node("1.1.2.4", 8)
          }
        }
        node("1.2", 9) {
          node("1.2.1", 10)
          node("1.2.2", 11) {
            node("1.2.2.1", 12)
          }
          node("1.2.3", 13)
        }
      }
    }
    val tree1 = buildTree {
      root("1", 1) {
        node("1.1", 2) {
          node("1.1.1", 3)
          node("1.1.2", 4) {
            node("1.1.2.1", 5)
            node("1.1.2.2", 6)
            node("1.1.2.3", 7)
            node("1.1.2.4", 8)
          }
        }
        node("1.2", 9) {
          node("1.2.1", 10)
          node("1.2.2", 11) {
            node("1.2.2.1", 12)
          }
          node("1.2.3", 13)
        }
      }
    }
    val tree2 = buildTree {
      root("1", 1) {
        node("1.1", 2) {
          node("1.1.1", 3)
          node("1.1.2", 4) {
            node("1.1.2.1", 5)
            node("1.1.2.2", 6)
            node("1.1.2.3", 7)
            node("1.1.2.4", 8)
          }
        }
      }
    }

    SimpleTreeAssertion.assertTreeEquals(tree, tree)
    SimpleTreeAssertion.assertTreeEquals(tree1, tree1)
    SimpleTreeAssertion.assertTreeEquals(tree2, tree2)

    SimpleTreeAssertion.assertTreeEquals(tree, tree1)
    Assertions.assertThrows(AssertionError::class.java) {
      SimpleTreeAssertion.assertTreeEquals(tree, tree2)
    }
    Assertions.assertThrows(AssertionError::class.java) {
      SimpleTreeAssertion.assertTreeEquals(tree1, tree2)
    }
  }

  @Test
  fun `test SimpleTreeAssertion#assertTreeEquals for values`() {
    val tree = buildTree {
      root("1", 1) {
        node("1.1", 2) {
          node("1.1.1", 3)
          node("1.1.2", 4) {
            node("1.1.2.1", 5)
            node("1.1.2.2", 6)
            node("1.1.2.3", 7)
            node("1.1.2.4", 8)
          }
        }
        node("1.2", 9) {
          node("1.2.1", 10)
          node("1.2.2", 11) {
            node("1.2.2.1", 12)
          }
          node("1.2.3", 13)
        }
      }
    }
    val tree1 = buildTree {
      root("1", 1) {
        node("1.1", 2) {
          node("1.1.1", 3)
          node("1.1.2", 4) {
            node("1.1.2.1", 5)
            node("1.1.2.2", 6)
            node("1.1.2.3", 7)
            node("1.1.2.4", 8)
          }
        }
        node("1.2", 9) {
          node("1.2.1", 10)
          node("1.2.2", 11) {
            node("1.2.2.1", 12)
          }
          node("1.2.3", 13)
        }
      }
    }
    val tree2 = buildTree {
      root("1", 1) {
        node("1.1", 2) {
          node("1.1.1", 3)
          node("1.1.2", 400000000) {
            node("1.1.2.1", 5)
            node("1.1.2.2", 6)
            node("1.1.2.3", 7)
            node("1.1.2.4", 8)
          }
        }
        node("1.2", 9) {
          node("1.2.1", 10)
          node("1.2.2", 11) {
            node("1.2.2.1", 12)
          }
          node("1.2.3", 13)
        }
      }
    }

    SimpleTreeAssertion.assertTreeEquals(tree, tree)
    SimpleTreeAssertion.assertTreeEquals(tree1, tree1)
    SimpleTreeAssertion.assertTreeEquals(tree2, tree2)

    SimpleTreeAssertion.assertTreeEquals(tree, tree1)
    Assertions.assertThrows(AssertionError::class.java) {
      SimpleTreeAssertion.assertTreeEquals(tree, tree2)
    }
    Assertions.assertThrows(AssertionError::class.java) {
      SimpleTreeAssertion.assertTreeEquals(tree1, tree2)
    }
  }

  @Test
  fun `test SimpleTreeAssertion#assertUnorderedTreeEquals`() {
    val tree = buildTree {
      root("1", 1) {
        node("1.1", 2) {
          node("1.1.1", 3)
          node("1.1.2", 4) {
            node("1.1.2.1", 5)
            node("1.1.2.2", 6)
            node("1.1.2.3", 7)
            node("1.1.2.4", 8)
          }
        }
        node("1.2", 9) {
          node("1.2.1", 10)
          node("1.2.2", 11) {
            node("1.2.2.1", 12)
          }
          node("1.2.3", 13)
        }
      }
    }
    val tree1 = buildTree {
      root("1", 1) {
        node("1.2", 9) {
          node("1.2.1", 10)
          node("1.2.2", 11) {
            node("1.2.2.1", 12)
          }
          node("1.2.3", 13)
        }
        node("1.1", 2) {
          node("1.1.1", 3)
          node("1.1.2", 4) {
            node("1.1.2.1", 5)
            node("1.1.2.2", 6)
            node("1.1.2.3", 7)
            node("1.1.2.4", 8)
          }
        }
      }
    }
    val tree2 = buildTree {
      root("1", 1) {
        node("1.1", 2) {
          node("1.1.1", 3)
          node("1.1.2", 4) {
            node("1.1.2.1", 5)
            node("1.1.2.2", 6)
            node("1.1.2.3", 7)
            node("1.1.2.4", 8)
          }
        }
      }
    }

    SimpleTreeAssertion.assertUnorderedTreeEquals(tree, tree)
    SimpleTreeAssertion.assertUnorderedTreeEquals(tree1, tree1)
    SimpleTreeAssertion.assertUnorderedTreeEquals(tree2, tree2)

    SimpleTreeAssertion.assertUnorderedTreeEquals(tree, tree1)
    Assertions.assertThrows(AssertionError::class.java) {
      SimpleTreeAssertion.assertTreeEquals(tree1, tree2)
    }
    Assertions.assertThrows(AssertionError::class.java) {
      SimpleTreeAssertion.assertTreeEquals(tree, tree2)
    }
  }

  @Nested
  inner class NodeMatcherTest {

    @Test
    fun `test name matcher`() {
      val tree = buildTree { root("root", 1) }
      SimpleTreeAssertion.assertTree(tree) {
        assertNode(NodeMatcher.name("root"))
      }
      Assertions.assertThrows(AssertionError::class.java) {
        SimpleTreeAssertion.assertTree(tree) {
          assertNode(NodeMatcher.name("other"))
        }
      }
    }

    @Test
    fun `test regex matcher`() {
      val tree = buildTree { root("root", 1) }
      SimpleTreeAssertion.assertTree(tree) {
        assertNode(NodeMatcher.regex("ro+t".toRegex()))
      }
      Assertions.assertThrows(AssertionError::class.java) {
        SimpleTreeAssertion.assertTree(tree) {
          assertNode(NodeMatcher.regex("roo".toRegex()))
        }
      }
    }

    @Test
    fun `test string or string matcher`() {
      val tree = buildTree { root("root", 1) }
      SimpleTreeAssertion.assertTree(tree) {
        assertNode("other" or "root")
      }
      Assertions.assertThrows(AssertionError::class.java) {
        SimpleTreeAssertion.assertTree(tree) {
          assertNode("other" or "fallback")
        }
      }
    }

    @Test
    fun `test matcher or string matcher`() {
      val tree = buildTree { root("root", 1) }
      SimpleTreeAssertion.assertTree(tree) {
        assertNode(NodeMatcher.name("other") or "root")
      }
      Assertions.assertThrows(AssertionError::class.java) {
        SimpleTreeAssertion.assertTree(tree) {
          assertNode(NodeMatcher.name("other") or "fallback")
        }
      }
    }

    @Test
    fun `test string or matcher matcher`() {
      val tree = buildTree { root("root", 1) }
      SimpleTreeAssertion.assertTree(tree) {
        assertNode("other" or NodeMatcher.name("root"))
      }
      Assertions.assertThrows(AssertionError::class.java) {
        SimpleTreeAssertion.assertTree(tree) {
          assertNode("other" or NodeMatcher.name("fallback"))
        }
      }
    }

    @Test
    fun `test matcher or matcher matcher`() {
      val tree = buildTree { root("root", 1) }
      SimpleTreeAssertion.assertTree(tree) {
        assertNode(NodeMatcher.name("other") or NodeMatcher.regex("ro+t".toRegex()))
      }
      Assertions.assertThrows(AssertionError::class.java) {
        SimpleTreeAssertion.assertTree(tree) {
          assertNode(NodeMatcher.name("other") or NodeMatcher.regex("fallback".toRegex()))
        }
      }
    }
  }

  @Nested
  inner class FlattenIfTest {

    @Test
    fun `skips node and promotes children to parent level`() {
      val tree = buildTree {
        root("root", 0) {
          node("B", 1)
          node("C", 2)
        }
      }

      SimpleTreeAssertion.assertTree(tree) {
        assertNode("root") {
          assertNode("A", flattenIf = true) {
            assertNode("B")
            assertNode("C")
          }
        }
      }
    }

    @Test
    fun `preserves grandchildren structure`() {
      val tree = buildTree {
        root("root", 0) {
          node("B", 1) {
            node("D", 3)
          }
          node("C", 2)
        }
      }

      SimpleTreeAssertion.assertTree(tree) {
        assertNode("root") {
          assertNode("A", flattenIf = true) {
            assertNode("B") {
              assertNode("D")
            }
            assertNode("C")
          }
        }
      }
    }

    @Test
    fun `with no children adds nothing`() {
      val tree = buildTree {
        root("root", 0)
      }

      SimpleTreeAssertion.assertTree(tree) {
        assertNode("root") {
          assertNode("A", flattenIf = true)
        }
      }
    }
  }
}
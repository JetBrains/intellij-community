// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.utils.treeAssertion

import com.intellij.platform.testFramework.treeAssertion.SimpleTreeAssertion
import com.intellij.platform.testFramework.treeAssertion.buildTree
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class SimpleTreeAssertionTest {

  @Test
  fun `test SimpleTreeAssertion#assertTreeEquals for names`() {
    val tree = buildTree<Int> {
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
    val tree1 = buildTree<Int> {
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
    val tree2 = buildTree<Int> {
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
    val tree = buildTree<Int> {
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
    val tree1 = buildTree<Int> {
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
    val tree2 = buildTree<Int> {
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
    val tree = buildTree<Int> {
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
    val tree1 = buildTree<Int> {
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
    val tree2 = buildTree<Int> {
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
}
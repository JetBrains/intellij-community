// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers.prefixTree

import com.intellij.openapi.util.io.CanonicalPathPrefixTree
import com.intellij.util.containers.prefixTree.set.asMutableSet
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class CanonicalPathPrefixTreeTest {

  @Test
  fun `test trailing slash`() {
    val tree = CanonicalPathPrefixTree.asMutableSet(
      "/a/b/c/d/1",
      "/a/b/c/d/2",
      "/a/b/c/d/3/",
      "/a/b/c/d/4/",
      "/a/b/c/e/1",
      "/a/b/c/e/2",
      "/a/b/c/e/3/",
      "/a/b/c/e/4/",
      "/a/b/c",
      "/a/b"
    )
    Assertions.assertTrue("/a/b/c/d/1/" in tree)
    Assertions.assertTrue("/a/b/c/d/2/" in tree)
    Assertions.assertTrue("/a/b/c/d/3/" in tree)
    Assertions.assertTrue("/a/b/c/d/4/" in tree)
    tree.remove("/a/b/c/d/3/")
    tree.remove("/a/b/c/d/4/")
    Assertions.assertTrue("/a/b/c/d/1/" in tree)
    Assertions.assertTrue("/a/b/c/d/2/" in tree)
    Assertions.assertTrue("/a/b/c/d/3/" !in tree)
    Assertions.assertTrue("/a/b/c/d/4/" !in tree)

    Assertions.assertEquals(setOf("/a/b/c", "/a/b"), tree.getAncestors("/a/b/c/d/e/"))

    Assertions.assertEquals(
      setOf(
        "/a/b/c/d/1",
        "/a/b/c/d/2",
        "/a/b/c/e/1",
        "/a/b/c/e/2",
        "/a/b/c/e/3/",
        "/a/b/c/e/4/",
        "/a/b/c",
      ),
      tree.getDescendants("/a/b/c/")
    )

    Assertions.assertEquals(setOf("/a/b"), tree.getRoots())
  }

  @Test
  fun `test file protocol`() {
    val tree = CanonicalPathPrefixTree.asMutableSet(
      "rd://a/b",
      "rd://a/b/c",
      "rd://a/b/c/d",
      "fsd://a/b",
      "fsd://a/b/c",
      "fsd://a/b/c/d",
    )

    Assertions.assertEquals(setOf("rd://a/b", "fsd://a/b"), tree.getRoots())

    Assertions.assertEquals(
      setOf(
        "rd://a/b",
        "rd://a/b/c",
        "rd://a/b/c/d"
      ),
      tree.getDescendants("rd:")
    )
    Assertions.assertEquals(
      setOf(
        "rd://a/b",
        "rd://a/b/c",
        "rd://a/b/c/d"
      ),
      tree.getDescendants("rd:/")
    )
    Assertions.assertEquals(
      setOf(
        "rd://a/b",
        "rd://a/b/c",
        "rd://a/b/c/d"
      ),
      tree.getDescendants("rd://")
    )

    Assertions.assertEquals(
      setOf(
        "fsd://a/b",
        "fsd://a/b/c",
        "fsd://a/b/c/d"
      ),
      tree.getAncestors("fsd://a/b/c/d")
    )
    Assertions.assertEquals(
      setOf(
        "fsd://a/b",
        "fsd://a/b/c"
      ),
      tree.getAncestors("fsd://a/b/c")
    )
  }
}
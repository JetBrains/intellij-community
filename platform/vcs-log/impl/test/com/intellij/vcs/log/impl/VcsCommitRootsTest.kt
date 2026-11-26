// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class VcsCommitRootsTest {
  @Test
  fun `prefers a nested root to its parent root`() {
    val roots = listOf("outer" to "/work/repo", "inner" to "/work/repo/lib")
    assertThat(keepNearestRoots(roots) { it.second }.map { it.first }).containsExactly("inner")
  }

  @Test
  fun `keeps all items with the nearest root in their order`() {
    val roots = listOf("b" to "/work/repo", "a" to "/work/repo", "c" to "/work")
    assertThat(keepNearestRoots(roots) { it.second }.map { it.first }).containsExactly("b", "a")
  }

  @Test
  fun `finds no root in an empty list`() {
    assertThat(keepNearestRoots(emptyList<String>()) { it }).isEmpty()
  }
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

import com.intellij.platform.settings.v2.impl.collapseNestedRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Pins [collapseNestedRoots], which reduces the content roots of a project to the topmost ones. A module content root
 * can sit inside another one, and a settings context anchors on the topmost, so the chain reaches every directory
 * below it.
 */
class SettingsRootCollapsingTest {
  @Test
  fun `a nested root is dropped`() {
    assertEquals(listOf("file:///p"), collapseNestedRoots(listOf("file:///p", "file:///p/module/src/main")))
  }

  @Test
  fun `a chain of nested roots leaves the topmost`() {
    val roots = listOf("file:///p/a/b/c", "file:///p/a", "file:///p/a/b")
    assertEquals(listOf("file:///p/a"), collapseNestedRoots(roots))
  }

  @Test
  fun `siblings all survive, sorted`() {
    val roots = listOf("file:///p/b", "file:///p/a", "file:///p/c")
    assertEquals(listOf("file:///p/a", "file:///p/b", "file:///p/c"), collapseNestedRoots(roots))
  }

  @Test
  fun `a root that only shares a name prefix survives`() {
    // "file:///p/bc" is not under "file:///p/b": a root ends at a path boundary.
    val roots = listOf("file:///p/b", "file:///p/bc")
    assertEquals(listOf("file:///p/b", "file:///p/bc"), collapseNestedRoots(roots))
  }

  @Test
  fun `a nested root is dropped when a name-prefix sibling sorts between it and its ancestor`() {
    // In sorted order "file:///p-x" comes between "file:///p" and "file:///p/q".
    val roots = listOf("file:///p/q", "file:///p-x", "file:///p")
    assertEquals(listOf("file:///p", "file:///p-x"), collapseNestedRoots(roots))
  }

  @Test
  fun `a repeated root appears once`() {
    val roots = listOf("file:///p/a", "file:///p/a")
    assertEquals(listOf("file:///p/a"), collapseNestedRoots(roots))
  }

  @Test
  fun `repeated nested roots and name-prefix siblings collapse to the topmost`() {
    val roots = listOf("file:///p/b/src", "file:///p/bc", "file:///p/b", "file:///p/b.x/src", "file:///p/b/src", "file:///p/b")
    assertEquals(listOf("file:///p/b", "file:///p/b.x/src", "file:///p/bc"), collapseNestedRoots(roots))
  }

  @Test
  fun `several trees collapse independently`() {
    val roots = listOf("file:///p/a/src", "file:///p/a", "file:///q", "file:///q/b/src", "file:///r/x")
    assertEquals(listOf("file:///p/a", "file:///q", "file:///r/x"), collapseNestedRoots(roots))
  }

  @Test
  fun `no roots give no roots`() {
    assertEquals(emptyList<String>(), collapseNestedRoots(emptyList()))
  }
}

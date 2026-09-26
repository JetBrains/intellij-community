// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.util.paths

import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.LocalFilePath
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RecursiveFilePathSetTest {
  @Test
  fun `hasAncestor finds a path that the set holds`() {
    val set = caseSensitiveSet("/project/out")

    assertTrue(set.hasAncestor(path("/project/out")))
  }

  @Test
  fun `hasAncestor finds every descendant of a path that the set holds`() {
    val set = caseSensitiveSet("/project/out")

    assertTrue(set.hasAncestor(path("/project/out/a.log")))
    assertTrue(set.hasAncestor(path("/project/out/deep/nested/a.log")))
  }

  @Test
  fun `hasAncestor rejects a path outside the set`() {
    val set = caseSensitiveSet("/project/out")

    assertFalse(set.hasAncestor(path("/project")))
    assertFalse(set.hasAncestor(path("/project/src/a.md")))
    assertFalse(set.hasAncestor(path("/other/out/a.log")))
  }

  @Test
  fun `a shared name prefix is not a match`() {
    val set = caseSensitiveSet("/project/out")

    assertFalse(set.hasAncestor(path("/project/outer")))
    assertFalse(set.hasAncestor(path("/project/outer/a.log")))
  }

  @Test
  fun `a nested path does not hide a parent path`() {
    val set = caseSensitiveSet("/project", "/project/out")

    assertTrue(set.hasAncestor(path("/project/src/a.md")))
    assertTrue(set.hasAncestor(path("/project/out/a.log")))
  }

  @Test
  fun `the file system root matches every path`() {
    val set = caseSensitiveSet("/")

    assertTrue(set.hasAncestor(path("/project/out/a.log")))
  }

  @Test
  fun `a trailing slash is ignored`() {
    val set = caseSensitiveSet("/project/out/")

    assertTrue(set.hasAncestor(path("/project/out")))
    assertTrue(set.hasAncestor(path("/project/out/a.log")))
  }

  @Test
  fun `a case sensitive set tells the case apart`() {
    val set = caseSensitiveSet("/project/Out")

    assertTrue(set.hasAncestor(path("/project/Out/a.log")))
    assertFalse(set.hasAncestor(path("/project/out/a.log")))
  }

  @Test
  fun `a case insensitive set ignores the case`() {
    val set = RecursiveFilePathSet(false)
    set.add(path("/project/Out"))

    assertTrue(set.hasAncestor(path("/project/Out/a.log")))
    assertTrue(set.hasAncestor(path("/project/out/a.log")))
    assertTrue(set.hasAncestor(path("/PROJECT/OUT/a.log")))
  }

  @Test
  fun `a windows path has no leading separator`() {
    val set = caseSensitiveSet("C:/project/out")

    assertTrue(set.hasAncestor(path("C:/project/out/a.log")))
    assertFalse(set.hasAncestor(path("C:/project/src/a.md")))
    assertFalse(set.hasAncestor(path("D:/project/out/a.log")))
  }

  @Test
  fun `an empty set matches nothing`() {
    val set = RecursiveFilePathSet(true)

    assertTrue(set.isEmpty)
    assertFalse(set.hasAncestor(path("/project/out/a.log")))
    assertFalse(set.hasAncestor(path("/")))
  }

  /** The set keys on the path alone. It holds no file or directory flag, so a file matches its descendants too. */
  @Test
  fun `the set ignores the directory flag`() {
    val set = RecursiveFilePathSet(true)
    set.add(LocalFilePath("/project/a.md", false))

    assertTrue(set.hasAncestor(LocalFilePath("/project/a.md", true)))
    assertTrue(set.hasAncestor(path("/project/a.md/child")))
    assertTrue(set.containsExplicitly(LocalFilePath("/project/a.md", true)))
  }

  @Test
  fun `containsExplicitly rejects a descendant`() {
    val set = caseSensitiveSet("/project/out")

    assertTrue(set.containsExplicitly(path("/project/out")))
    assertFalse(set.containsExplicitly(path("/project/out/a.log")))
  }

  @Test
  fun `addAll adds every path`() {
    val set = RecursiveFilePathSet(true)
    set.addAll(listOf(path("/project/out"), path("/project/build")))

    assertTrue(set.hasAncestor(path("/project/out/a.log")))
    assertTrue(set.hasAncestor(path("/project/build/a.log")))
  }

  @Test
  fun `remove drops the path and its descendants`() {
    val set = caseSensitiveSet("/project/out", "/project/build")

    set.remove(path("/project/out"))

    assertFalse(set.hasAncestor(path("/project/out")))
    assertFalse(set.hasAncestor(path("/project/out/a.log")))
    assertTrue(set.hasAncestor(path("/project/build/a.log")))
  }

  @Test
  fun `clear empties the set`() {
    val set = caseSensitiveSet("/project/out")

    set.clear()

    assertTrue(set.isEmpty)
    assertFalse(set.hasAncestor(path("/project/out/a.log")))
  }

  @Test
  fun `filePaths reports every path that the set holds`() {
    val set = caseSensitiveSet("/project/out", "/project/a.md")

    assertFalse(set.isEmpty)
    assertEquals(setOf("/project/out", "/project/a.md"), set.filePaths().map { it.path }.toSet())
  }

  private fun path(path: String): FilePath = LocalFilePath(path, true)

  private fun caseSensitiveSet(vararg paths: String): RecursiveFilePathSet {
    val set = RecursiveFilePathSet(true)
    for (path in paths) {
      set.add(path(path))
    }
    return set
  }
}

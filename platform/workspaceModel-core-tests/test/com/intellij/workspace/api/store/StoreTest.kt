// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.store

import org.junit.Assert.*
import org.junit.Test

class StoreTest {
  @Test
  fun `test 1`() {
    val store = Store()
    store.add("/a/b/a.txt", 1)
    store.add("/a/b.txt", 2)
    store.add("/c", 3)
    store.add("/a/b/d.txt", 4)
    assertEquals(store.toString(), """
      # /
      # |-  a
      # |    |-  b
      # |    |    |-  a.txt => [1]
      # |    |    '-  d.txt => [4]
      # |    '-  b.txt => [2]
      # '-  c => [3]
      #""".trimMargin("#"))
  }

  @Test
  fun `test 2`() {
    val store = Store()
    store.add("/a/b/a.txt", 1)
    store.add("/a/b/a.txt", 2)
    store.add("/a/b/a.txt", 3)
    store.add("/a/b/a.txt", 3)
    assertEquals(store.toString(), """
      # /
      # '-  a
      #      '-  b
      #           '-  a.txt => [1, 2, 3]
      #""".trimMargin("#"))
  }

  @Test
  fun `test 3`() {
    val store = Store()
    store.add("/a/b/a.txt", 1)
    store.remove("/a/b/a.txt", 1)
    assertEquals(store.toString(), "null")
  }

  @Test
  fun `test 4`() {
    val store = Store()
    store.add("/a/b/a.txt", 1)
    store.add("/a/c/a.txt", 2)
    store.remove("/a/b/a.txt", 1)
    assertEquals(store.toString(), """
      # /
      # '-  a
      #      '-  c
      #           '-  a.txt => [2]
      #""".trimMargin("#"))
  }

  @Test
  fun `test 5`() {
    val store = Store()
    store.add("/", 1)
    store.add("/a", 2)
    store.remove("/a", 1)
    assertEquals(store.toString(), """
      # / => [1]
      # '-  a => [2]
      #""".trimMargin("#"))
  }

  @Test
  fun `test 6`() {
    val store = Store()
    store.add("/a/b/a.txt", 1)
    assertEquals(store.toString(), """
      # /
      # '-  a
      #      '-  b
      #           '-  a.txt => [1]
      #""".trimMargin("#"))
    store.update("/a/b/a.txt", "/a/b/d.txt", 1)
    assertEquals(store.toString(), """
      # /
      # '-  a
      #      '-  b
      #           '-  d.txt => [1]
      #""".trimMargin("#"))
  }

  @Test
  fun `test 7`() {
    val store = Store()
    store.add("/a/b/c.txt", 1)
    store.add("/a/c/d.txt", 2)
    assertEquals(store.toString(), """
      # /
      # '-  a
      #      |-  b
      #      |    '-  c.txt => [1]
      #      '-  c
      #           '-  d.txt => [2]
      #""".trimMargin("#"))
    store.update("/a/b/c.txt", "/a/c/d.txt", 1)
    assertEquals(store.toString(), """
      # /
      # '-  a
      #      '-  c
      #           '-  d.txt => [2, 1]
      #""".trimMargin("#"))
  }

  @Test
  fun `test 8`() {
    val store = Store()
    store.add("/a/b/c.txt", 1)
    assertEquals(store.toString(), """
      # /
      # '-  a
      #      '-  b
      #           '-  c.txt => [1]
      #""".trimMargin("#"))
    store.update("/a/b/k.txt", "/a/c/d.txt", 1)
    assertEquals(store.toString(), """
      # /
      # '-  a
      #      '-  b
      #           '-  c.txt => [1]
      #""".trimMargin("#"))
  }

  @Test
  fun `test 9`() {
    val store = Store()
    store.add("/a/b/c.txt", 1)
    assertEquals(store.toString(), """
      # /
      # '-  a
      #      '-  b
      #           '-  c.txt => [1]
      #""".trimMargin("#"))
    store.update("/a/b/c.txt", "/k.txt", 1)
    assertEquals(store.toString(), """
      # /
      # '-  k.txt => [1]
      #""".trimMargin("#"))
  }
}
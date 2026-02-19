// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers.prefixTree

import com.google.common.jimfs.Configuration
import com.intellij.openapi.util.io.PathPrefixTree
import com.intellij.platform.testFramework.junit5.jimfs.jimFsFixture
import com.intellij.testFramework.junit5.fixture.TestFixtures
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

@TestFixtures
class PathPrefixTreeTest {

  private val unixFs by jimFsFixture(Configuration.unix())
  private val windowsFs by jimFsFixture(Configuration.windows())

  @Test
  fun `test case sensitive file system`() {
    val pathLow = unixFs.getPath("/root/path")
    val pathHigh = unixFs.getPath("/root/PATH")

    val tree = PathPrefixTree.createMap<Int>()

    Assertions.assertThat(tree).doesNotContainKey(pathLow)
    Assertions.assertThat(tree).doesNotContainKey(pathHigh)

    Assertions.assertThat(tree.put(pathLow, 10)).isEqualTo(null as Int?)

    Assertions.assertThat(tree).containsKey(pathLow)
    Assertions.assertThat(tree).doesNotContainKey(pathHigh)

    Assertions.assertThat(tree.put(pathHigh, 20)).isEqualTo(null as Int?)

    Assertions.assertThat(tree).containsKey(pathLow)
    Assertions.assertThat(tree).containsKey(pathHigh)
  }

  @Test
  fun `test case insensitive file system`() {
    val pathLow = windowsFs.getPath("C:/root/path")
    val pathHigh = windowsFs.getPath("C:/root/PATH")

    val tree = PathPrefixTree.createMap<Int>()

    Assertions.assertThat(tree).doesNotContainKey(pathLow)
    Assertions.assertThat(tree).doesNotContainKey(pathHigh)

    Assertions.assertThat(tree.put(pathLow, 10)).isEqualTo(null as Int?)

    Assertions.assertThat(tree).containsKey(pathLow)
    Assertions.assertThat(tree).containsKey(pathHigh)

    Assertions.assertThat(tree.put(pathHigh, 20)).isEqualTo(10)

    Assertions.assertThat(tree).containsKey(pathLow)
    Assertions.assertThat(tree).containsKey(pathHigh)
  }
}
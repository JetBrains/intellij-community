// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers.prefixTree

import com.google.common.jimfs.Configuration
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFilePrefixTree
import com.intellij.platform.testFramework.junit5.jimfs.jimFsFixture
import com.intellij.testFramework.junit5.fixture.TestFixtures
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.file.Path

@TestFixtures
class VirtualFilePrefixTreeTest {

  private val unixFs by jimFsFixture(Configuration.unix())
  private val windowsFs by jimFsFixture(Configuration.windows())

  private fun virtualFile(path: Path): VirtualFile {
    val virtualFile = mock<VirtualFile>()
    whenever(virtualFile.toNioPath()).thenReturn(path)
    whenever(virtualFile.path).thenReturn(path.toCanonicalPath())
    whenever(virtualFile.toString()).thenReturn(path.toString())
    return virtualFile
  }

  @Test
  fun `test case sensitive file system`() {
    val fileLow = virtualFile(unixFs.getPath("/root/path"))
    val fileHigh = virtualFile(unixFs.getPath("/root/PATH"))

    val tree = VirtualFilePrefixTree.createMap<Int>()

    Assertions.assertThat(tree).doesNotContainKey(fileLow)
    Assertions.assertThat(tree).doesNotContainKey(fileHigh)

    Assertions.assertThat(tree.put(fileLow, 10)).isEqualTo(null as Int?)

    Assertions.assertThat(tree).containsKey(fileLow)
    Assertions.assertThat(tree).doesNotContainKey(fileHigh)

    Assertions.assertThat(tree.put(fileHigh, 20)).isEqualTo(null as Int?)

    Assertions.assertThat(tree).containsKey(fileLow)
    Assertions.assertThat(tree).containsKey(fileHigh)
  }

  @Test
  fun `test case insensitive file system`() {
    val fileLow = virtualFile(windowsFs.getPath("C:/root/path"))
    val fileHigh = virtualFile(windowsFs.getPath("C:/root/PATH"))

    val tree = VirtualFilePrefixTree.createMap<Int>()

    Assertions.assertThat(tree).doesNotContainKey(fileLow)
    Assertions.assertThat(tree).doesNotContainKey(fileHigh)

    Assertions.assertThat(tree.put(fileLow, 10)).isEqualTo(null as Int?)

    Assertions.assertThat(tree).containsKey(fileLow)
    Assertions.assertThat(tree).containsKey(fileHigh)

    Assertions.assertThat(tree.put(fileHigh, 20)).isEqualTo(10)

    Assertions.assertThat(tree).containsKey(fileLow)
    Assertions.assertThat(tree).containsKey(fileHigh)
  }
}
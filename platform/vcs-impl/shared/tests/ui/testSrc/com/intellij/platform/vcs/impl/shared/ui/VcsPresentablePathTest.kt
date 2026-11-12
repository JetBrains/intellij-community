// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared.ui

import com.intellij.openapi.vcs.LocalFilePath
import com.intellij.platform.vcs.impl.shared.VcsMappingsHolder
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.jupiter.api.assertNull
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

internal class VcsPresentablePathTest {
  @Test
  fun `test single root - file at root`() {
    val vcsMappingsHolder = mock<VcsMappingsHolder>()
    val projectBaseDir = LocalFilePath("/project", true)

    `when`(vcsMappingsHolder.getRootFor(projectBaseDir)).thenReturn(projectBaseDir)
    `when`(vcsMappingsHolder.getAllRoots()).thenReturn(listOf(projectBaseDir))

    val resultAcceptingEmpty = VcsPresentablePath.getRelativePathToSingleVcsRootOrProjectDir(vcsMappingsHolder,
                                                                                             projectBaseDir,
                                                                                             projectBaseDir,
                                                                                             acceptEmptyPath = true)
    assertEquals("", resultAcceptingEmpty)

    val result = VcsPresentablePath.getRelativePathToSingleVcsRootOrProjectDir(vcsMappingsHolder,
                                                                               projectBaseDir,
                                                                               projectBaseDir,
                                                                               acceptEmptyPath = false)
    assertEquals("project", result)
  }

  @Test
  fun `test single root - file under root`() {
    val vcsMappingsHolder = mock<VcsMappingsHolder>()
    val projectBaseDir = LocalFilePath("/project", true)
    val filePath = LocalFilePath("/project/src/Main.kt", false)

    `when`(vcsMappingsHolder.getRootFor(filePath)).thenReturn(projectBaseDir)
    `when`(vcsMappingsHolder.getAllRoots()).thenReturn(listOf(projectBaseDir))

    val result =
      VcsPresentablePath.getRelativePathToSingleVcsRootOrProjectDir(vcsMappingsHolder, projectBaseDir, filePath, acceptEmptyPath = true)
    assertEquals("src/Main.kt", result)
  }

  @Test
  fun `test multiple roots - file at project base dir`() {
    val vcsMappingsHolder = mock<VcsMappingsHolder>()
    val projectBaseDir = LocalFilePath("/project", true)
    val root2 = LocalFilePath("/project/submodule", true)

    `when`(vcsMappingsHolder.getRootFor(projectBaseDir)).thenReturn(projectBaseDir)
    `when`(vcsMappingsHolder.getAllRoots()).thenReturn(listOf(projectBaseDir, root2))

    val result = VcsPresentablePath.getRelativePathToSingleVcsRootOrProjectDir(vcsMappingsHolder,
                                                                               projectBaseDir,
                                                                               projectBaseDir,
                                                                               acceptEmptyPath = true)
    assertEquals("project", result)
  }

  @Test
  fun `test multiple roots - file under project root`() {
    val vcsMappingsHolder = mock<VcsMappingsHolder>()
    val projectBaseDir = LocalFilePath("/project", true)
    val root2 = LocalFilePath("/project/submodule", true)
    val filePath = LocalFilePath("/project/src/Main.kt", false)

    `when`(vcsMappingsHolder.getRootFor(filePath)).thenReturn(projectBaseDir)
    `when`(vcsMappingsHolder.getAllRoots()).thenReturn(listOf(projectBaseDir, root2))

    val result =
      VcsPresentablePath.getRelativePathToSingleVcsRootOrProjectDir(vcsMappingsHolder, projectBaseDir, filePath, acceptEmptyPath = true)
    assertEquals("project/src/Main.kt", result)
  }

  @Test
  fun `test multiple roots - nested root`() {
    val vcsMappingsHolder = mock<VcsMappingsHolder>()
    val projectBaseDir = LocalFilePath("/project", true)
    val root2 = LocalFilePath("/project/submodule", true)
    val filePath = LocalFilePath("/project/submodule/src/File.kt", false)

    `when`(vcsMappingsHolder.getRootFor(filePath)).thenReturn(root2)
    `when`(vcsMappingsHolder.getAllRoots()).thenReturn(listOf(projectBaseDir, root2))

    val result =
      VcsPresentablePath.getRelativePathToSingleVcsRootOrProjectDir(vcsMappingsHolder, projectBaseDir, filePath, acceptEmptyPath = true)
    assertEquals("submodule/src/File.kt", result)
  }

  @Test
  fun `test multiple roots - root outside project dir`() {
    val vcsMappingsHolder = mock<VcsMappingsHolder>()
    val projectBaseDir = LocalFilePath("/project", true)
    val root2 = LocalFilePath("/external/lib", true)
    val filePath = LocalFilePath("/external/lib/src/Lib.kt", false)

    `when`(vcsMappingsHolder.getRootFor(filePath)).thenReturn(root2)
    `when`(vcsMappingsHolder.getAllRoots()).thenReturn(listOf(projectBaseDir, root2))

    val result =
      VcsPresentablePath.getRelativePathToSingleVcsRootOrProjectDir(vcsMappingsHolder, projectBaseDir, filePath, acceptEmptyPath = true)
    assertNull(result)
  }

  @Test
  fun `test no root found`() {
    val vcsMappingsHolder = mock<VcsMappingsHolder>()
    val projectBaseDir = LocalFilePath("/project", true)
    val filePath = LocalFilePath("/project/src/Main.kt", false)

    `when`(vcsMappingsHolder.getRootFor(filePath)).thenReturn(null)

    val result =
      VcsPresentablePath.getRelativePathToSingleVcsRootOrProjectDir(vcsMappingsHolder, projectBaseDir, filePath, acceptEmptyPath = true)
    assertEquals("<Project>/src/Main.kt", result)
  }
}

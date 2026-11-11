// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide

import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.openapi.vfs.pointers.VirtualFilePointer
import com.intellij.platform.backend.workspace.impl.VirtualFileUrlWithVirtualFile
import com.intellij.platform.backend.workspace.storeAndGet
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.workspaceModel.ide.impl.IdeVirtualFileUrlManagerImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
internal class IdeVirtualFileUrlManagerTest {
  @Test
  fun `a file without an ID is not cached`() {
    val manager = IdeVirtualFileUrlManagerImpl()
    val file = LightVirtualFile("test.txt")

    val url = manager.storeAndGet(file) as VirtualFileUrlWithVirtualFile

    assertThat(url.getCachedVirtualFile()).isNull()
  }

  @Test
  fun `a file without an ID is not cached after URL lookup`() {
    val manager = IdeVirtualFileUrlManagerImpl()
    val url = manager.storeAndGet("http://example.invalid/test.txt")
    val pointer = url as VirtualFilePointer
    val cache = url as VirtualFileUrlWithVirtualFile

    assertThat(pointer.file).isNotNull().isNotInstanceOf(VirtualFileWithId::class.java)
    assertThat(cache.getCachedVirtualFile()).isNull()

    assertThat(pointer.isValid).isTrue()
    assertThat(cache.getCachedVirtualFile()).isNull()
  }

  @Test
  fun `a file with an ID is cached`() {
    val manager = IdeVirtualFileUrlManagerImpl()
    val file = object : LightVirtualFile("test.txt"), VirtualFileWithId {
      override fun getId(): Int = 1
    }

    val url = manager.storeAndGet(file) as VirtualFileUrlWithVirtualFile

    assertThat(url.getCachedVirtualFile()).isSameAs(file)
  }
}

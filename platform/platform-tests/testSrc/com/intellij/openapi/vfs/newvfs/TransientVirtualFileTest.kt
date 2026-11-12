// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.io.FileAttributes
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.TransientVirtualFileVfsRefreshUtils.createTransientChild
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.replaceService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.`when`

@TestApplication
internal class TransientVirtualFileTest {
  private val fileSystem = mock(NewVirtualFileSystem::class.java)
  private val parent = LightVirtualFile("parent")

  @ParameterizedTest
  @ValueSource(booleans = [true, false])
  fun `the supplied directory flag avoids file system calls`(isDirectory: Boolean) {
    val child = child(isDirectory = isDirectory)

    repeat(2) { assertEquals(isDirectory, child.isDirectory) }
    verifyNoInteractions(fileSystem)
  }

  @ParameterizedTest
  @ValueSource(booleans = [true, false])
  fun `cached attributes avoid file system calls`(isDirectory: Boolean) {
    val child = child(attributes(isDirectory))

    assertEquals(isDirectory, child.isDirectory)
    verifyNoInteractions(fileSystem)
  }

  @ParameterizedTest
  @CsvSource("true, true", "true, false", "false, true", "false, false")
  fun `attributes and a directory flag cannot both be supplied`(attributesDirectory: Boolean, isDirectory: Boolean) {
    assertThrows<IllegalArgumentException> { child(attributes(attributesDirectory), isDirectory) }
    verifyNoInteractions(fileSystem)
  }

  @ParameterizedTest
  @ValueSource(booleans = [true, false])
  fun `loaded attributes override the supplied directory flag`(isDirectory: Boolean) {
    val child = child(isDirectory = !isDirectory)
    `when`(fileSystem.getAttributes(child)).thenReturn(attributes(isDirectory))

    assertEquals(!isDirectory, child.isDirectory)
    assertEquals(42L, child.timeStamp)
    assertEquals(isDirectory, child.isDirectory)
    verify(fileSystem).getAttributes(child)
    verifyNoMoreInteractions(fileSystem)
  }

  @ParameterizedTest
  @ValueSource(booleans = [true, false])
  fun `directory checks load attributes once when neither cache is set`(isDirectory: Boolean) {
    val child = child()
    `when`(fileSystem.getAttributes(child)).thenReturn(attributes(isDirectory))

    repeat(2) { assertEquals(isDirectory, child.isDirectory) }
    verify(fileSystem).getAttributes(child)
    verifyNoMoreInteractions(fileSystem)
  }

  @ParameterizedTest
  @ValueSource(booleans = [true, false])
  fun `unavailable attributes preserve the supplied directory flag`(isDirectory: Boolean) {
    val child = child(isDirectory = isDirectory)

    assertNull((child as VirtualFileWithAttributes).attributes)
    assertEquals(isDirectory, child.isDirectory)
    verify(fileSystem).getAttributes(child)
    verifyNoMoreInteractions(fileSystem)
  }

  @ParameterizedTest
  @ValueSource(booleans = [true, false])
  fun `unavailable attributes use the file system directory check`(isDirectory: Boolean) {
    val child = child()
    `when`(fileSystem.isDirectory(child)).thenReturn(isDirectory)

    assertEquals(isDirectory, child.isDirectory)
    verify(fileSystem).getAttributes(child)
    verify(fileSystem).isDirectory(child)
    verifyNoMoreInteractions(fileSystem)
  }

  @ParameterizedTest
  @CsvSource("true, true", "true, false", "false, true", "false, false")
  fun `refresh clears either cache`(isDirectory: Boolean, withAttributes: Boolean, @TestDisposable disposable: Disposable) {
    ApplicationManager.getApplication().replaceService(RefreshQueue::class.java, mock(RefreshQueue::class.java), disposable)
    val child = if (withAttributes) child(attributes(isDirectory)) else child(isDirectory = isDirectory)
    `when`(fileSystem.getAttributes(child)).thenReturn(attributes(!isDirectory))

    assertEquals(isDirectory, child.isDirectory)
    child.refresh(false, false)
    assertEquals(!isDirectory, child.isDirectory)
    verify(fileSystem).getAttributes(child)
    verifyNoMoreInteractions(fileSystem)
  }

  private fun child(attributes: FileAttributes? = null, isDirectory: Boolean? = null): VirtualFile =
    createTransientChild(parent, "child", fileSystem, attributes, isDirectory)

  private fun attributes(isDirectory: Boolean): FileAttributes =
    FileAttributes(isDirectory, false, false, false, 42, 42, true)
}

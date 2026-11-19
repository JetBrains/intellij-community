// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.settings.v2.impl.SettingsContentRootsProvider
import com.intellij.platform.settings.v2.impl.VirtualFileSettingsContext
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Path

/**
 * Checks the file contexts of a product mode with no [SettingsContentRootsProvider], such as a client light session.
 * The base directory of the project is then the only root: a file under it anchors on it, and a file outside it
 * gets the project context.
 */
@TestApplication
class SettingsNoRootsProviderTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  @TestDisposable
  private lateinit var disposable: Disposable

  private val project get() = projectFixture.get()

  @BeforeEach
  fun maskRootsProviders() {
    ExtensionTestUtil.maskExtensions(SettingsContentRootsProvider.EP_NAME, emptyList(), disposable)
  }

  @Test
  fun `a file under the base directory anchors on the base directory`(): Unit = runBlocking {
    val baseDir = baseDir()
    val file = VfsTestUtil.createFile(baseDir, "light/Sample.txt")

    val context = readAction { SettingsContext.file(file, project) } as VirtualFileSettingsContext

    assertEquals(baseDir.url, context.rootUrl)
    assertEquals("light/Sample.txt", context.relativePath)
  }

  @Test
  fun `a file outside the base directory resolves at the project`(): Unit = runBlocking {
    val baseDir = baseDir()
    val outside = VfsTestUtil.createFile(readAction { baseDir.parent }, "${baseDir.name}-outside.txt")

    val context = readAction { SettingsContext.file(outside, project) }

    assertEquals(SettingsContext.project(project).identity, context.identity)
  }

  private fun baseDir(): VirtualFile =
    VirtualFileManager.getInstance().refreshAndFindFileByNioPath(Path.of(project.basePath!!))!!
}

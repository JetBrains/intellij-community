// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.settings.v2.impl.SettingsContentRootsProvider
import com.intellij.platform.settings.v2.impl.FileSettingsContext
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Path

/**
 * Checks how a file context matches a file against the roots of a [SettingsContentRootsProvider]. The file matches
 * a root by path, not by url, and only the first provider answers.
 */
@TestApplication
class SettingsRootsProviderTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  @TestDisposable
  private lateinit var disposable: Disposable

  private val project get() = projectFixture.get()

  @Test
  fun `a root url of another protocol matches a file by path and the context keeps that url`(): Unit = runBlocking {
    val baseDir = baseDir()
    val file = VfsTestUtil.createFile(baseDir, "protocol/Sample.txt")
    val rootUrl = "other://${baseDir.path}"
    maskProviders(rootsProvider(rootUrl))

    val context = readAction { SettingsContext.file(file, project) }
    val chain = generateSequence(context) { it.parentContext }.map { it.identity }.toList()

    val projectId = project.locationHash
    assertEquals(listOf("file|$projectId|$rootUrl|protocol/Sample.txt",
                        "file|$projectId|$rootUrl|protocol",
                        "file|$projectId|$rootUrl|",
                        "project|$projectId",
                        "app"), chain)
  }

  @Test
  fun `only the first provider answers`(): Unit = runBlocking {
    val baseDir = baseDir()
    val file = VfsTestUtil.createFile(baseDir, "first/Sample.txt")
    val firstRoot = readAction { file.parent }
    maskProviders(rootsProvider(firstRoot.url), rootsProvider(baseDir.url))

    val context = readAction { SettingsContext.file(file, project) } as FileSettingsContext

    assertEquals(firstRoot.url, context.rootUrl)
    assertEquals("Sample.txt", context.relativePath)
  }

  /**
   * Pins that a root of a file system, such as `file:///`, matches no file, so the file gets the project context.
   * The settings contexts spec holds the open question about such a root.
   */
  @Test
  fun `a file system root matches no file`(): Unit = runBlocking {
    val file = VfsTestUtil.createFile(baseDir(), "fsRoot/Sample.txt")
    maskProviders(rootsProvider("file:///"))

    val context = readAction { SettingsContext.file(file, project) }

    assertEquals(SettingsContext.project(project).identity, context.identity)
  }

  private fun maskProviders(vararg providers: SettingsContentRootsProvider) {
    ExtensionTestUtil.maskExtensions(SettingsContentRootsProvider.EP_NAME, providers.toList(), disposable)
  }

  private fun rootsProvider(rootUrl: String): SettingsContentRootsProvider = object : SettingsContentRootsProvider {
    override fun rootUrls(project: Project): List<String> = listOf(rootUrl)
  }

  private fun baseDir(): VirtualFile =
    VirtualFileManager.getInstance().refreshAndFindFileByNioPath(Path.of(project.basePath!!))!!
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

import com.intellij.injected.editor.DocumentWindow
import com.intellij.injected.editor.VirtualFileDelegate
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.openapi.application.readAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Pins the file context of a non-persistent file (ADR 0151). The walk takes the delegate of a [VirtualFileDelegate]
 * or a [VirtualFileWindow], and the parent of any other light file. A delegate loop gets the project context.
 */
@TestApplication
class SettingsNonPersistentFileTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
    private val moduleFixture = projectFixture.moduleFixture()
    private val sourceRootFixture = moduleFixture.sourceRootFixture()
  }

  private val project get() = projectFixture.get()
  private val sourceRoot get() = sourceRootFixture.get().virtualFile

  @Test
  fun `a light file with a persistent parent gets the context of that directory`(): Unit = runBlocking {
    val directory = VfsTestUtil.createDir(sourceRoot, "light/parent")
    assertSameContext(expected = directory, actual = LightFile(parentDirectory = directory))
  }

  @Test
  fun `a delegating file gets the context of its delegate, not of its parent`(): Unit = runBlocking {
    val real = VfsTestUtil.createFile(sourceRoot, "delegate/real.txt")
    val file = DelegatingFile(parentDirectory = decoyDirectory("delegate")).apply { target = real }
    assertSameContext(expected = real, actual = file)
  }

  @Test
  fun `an injected window file gets the context of its host file`(): Unit = runBlocking {
    val host = VfsTestUtil.createFile(sourceRoot, "window/host.txt")
    assertSameContext(expected = host, actual = WindowFile(host, parentDirectory = decoyDirectory("window")))
  }

  @Test
  fun `a chain of a delegate and a light file ends at the parent of the light file`(): Unit = runBlocking {
    val directory = VfsTestUtil.createDir(sourceRoot, "chain/parent")
    val file = DelegatingFile(parentDirectory = decoyDirectory("chain")).apply { target = LightFile(parentDirectory = directory) }
    assertSameContext(expected = directory, actual = file)
  }

  @Test
  fun `a delegate loop gets the project context`(): Unit = runBlocking {
    val decoy = decoyDirectory("loop")
    val first = DelegatingFile(parentDirectory = decoy)
    val second = DelegatingFile(parentDirectory = decoy)
    first.target = second
    second.target = first
    assertEquals(SettingsContext.project(project), readAction { SettingsContext.file(first, project) })
  }

  /** Creates a directory in [area] that a delegating file has as its parent, so a walk that takes the parent fails. */
  private fun decoyDirectory(area: String): VirtualFile = VfsTestUtil.createDir(sourceRoot, "$area/decoy")

  private suspend fun assertSameContext(expected: VirtualFile, actual: VirtualFile) {
    readAction {
      val expectedContext = SettingsContext.file(expected, project)
      assertEquals(SettingsContextKind.FILE, expectedContext.kind, "the expected file must be under a content root")
      assertEquals(expectedContext, SettingsContext.file(actual, project))
    }
  }
}

/** A light file with the parent [parentDirectory]. A plain [LightVirtualFile] has no parent. */
private open class LightFile(private val parentDirectory: VirtualFile?) : LightVirtualFile("Light.txt", "") {
  override fun getParent(): VirtualFile? = parentDirectory
}

/** A light file that delegates to [target]. */
private class DelegatingFile(parentDirectory: VirtualFile) : LightFile(parentDirectory), VirtualFileDelegate {
  lateinit var target: VirtualFile

  override fun getDelegate(): VirtualFile = target
}

/** A light window file of injected code in [host]. The resolution reads no document, so it has none. */
private class WindowFile(private val host: VirtualFile, parentDirectory: VirtualFile) : LightFile(parentDirectory), VirtualFileWindow {
  override fun getDelegate(): VirtualFile = host

  override fun getDocumentWindow(): DocumentWindow = throw UnsupportedOperationException("not used by the resolution")
}

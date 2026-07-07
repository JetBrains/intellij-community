// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

import com.intellij.openapi.application.readAction
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.settings.v2.impl.VirtualFileSettingsContext
import com.intellij.psi.PsiManager
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * Checks how the public [SettingsContext] API creates a context and finds its parents, through the [ContextFactory]
 * service that the platform registers. The class also checks that an identity rebuilds the same context, and that a
 * PSI file context pins the language of its file.
 */
@TestApplication
class SettingsContextTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
    private val moduleFixture = projectFixture.moduleFixture()
    private val sourceRootFixture = moduleFixture.sourceRootFixture()
  }

  private val project get() = projectFixture.get()
  private val sourceRoot get() = sourceRootFixture.get().virtualFile

  @Test
  fun `application context is the root and project parents to it`() {
    val app = SettingsContext.app()
    assertNull(app.parentContext)

    val projectContext = SettingsContext.project(project)
    assertEquals(app, projectContext.parentContext)
  }

  @Test
  fun `in-tree file resolves through its directory chain to the content root, project and application`(): Unit = runBlocking {
    val file = VfsTestUtil.createFile(sourceRoot, "a/b/c.txt")
    readAction {
      val chain = SettingsContext.file(file, project).chain()

      assertNull(chain.last().parentContext, "the chain must end at the application context")

      val contentRoot = requireNotNull(ProjectFileIndex.getInstance(project).getContentRootForFile(file))
      val expected = generateSequence(file) { it.parent }.takeWhile { it != contentRoot }.toList() + contentRoot
      assertEquals(expected.map { it.url }, chain.fileNodes(), "file then each ancestor directory up to the content root")
      assertEquals(expected.size + 2, chain.size, "followed by the project and application contexts")
    }
  }

  @Test
  fun `files with no in-tree location resolve directly to the project`(@TempDir tempDir: Path): Unit = runBlocking {
    val outOfTree = requireNotNull(VirtualFileManager.getInstance().refreshAndFindFileByNioPath(Files.writeString(tempDir.resolve("x.txt"), "")))
    val light: VirtualFile = LightVirtualFile("scratch.txt", "")
    readAction {
      val outChain = SettingsContext.file(outOfTree, project).chain()
      assertTrue(outChain.fileNodes().isEmpty(), "a file under no content root has no file context")
      assertEquals(2, outChain.size, "just project and application")

      val lightChain = SettingsContext.file(light, project).chain()
      assertTrue(lightChain.fileNodes().isEmpty(), "an in-memory file has no persistent anchor")
      assertEquals(2, lightChain.size, "just project and application")
    }
  }

  @Test
  fun `an identity holds the target only and no editing flag`(): Unit = runBlocking {
    val file = VfsTestUtil.createFile(sourceRoot, "u/v.txt")
    val projectId = project.locationHash
    val contentRoot = readAction { requireNotNull(ProjectFileIndex.getInstance(project).getContentRootForFile(file)) }

    assertEquals("app", SettingsContext.app().identity)
    assertEquals("project|$projectId", SettingsContext.project(project).identity)
    assertEquals("file|$projectId|${contentRoot.url}|${file.url.removePrefix("${contentRoot.url}/")}",
                 readAction { SettingsContext.file(file, project) }.identity)
  }

  @Test
  fun `every context kind rebuilds from its identity with the same chain`(): Unit = runBlocking {
    val file = VfsTestUtil.createFile(sourceRoot, "r/s/t.txt")
    val chain = readAction { SettingsContext.file(file, project).chain() }
    assertEquals(SettingsContextKind.entries.toSet(), chain.map { it.kind }.toSet(), "the chain covers every context kind")

    for (context in chain) {
      val restored = requireNotNull(ContextFactory.getInstance().deserialize(context.identity)) { context.identity }
      assertEquals(context, restored)
      assertEquals(context.kind, restored.kind, context.identity)
      assertEquals(context.chain().map { it.identity }, restored.chain().map { it.identity }, context.identity)
    }
  }

  @Test
  fun `a malformed identity rebuilds to null and logs an error`() {
    val factory = ContextFactory.getInstance()
    val malformed = listOf("", "unknown", "app|false", "project", "project|false|hash", "file|hash|root",
                           "project|", "file||root|a.txt", "file|hash||a.txt")
    for (identity in malformed) {
      val (context, errors) = collectingErrors { factory.deserialize(identity) }
      assertNull(context, identity)
      assertEquals(1, errors.size, identity)
      assertTrue("'$identity'" in errors.single(), errors.single())
    }
  }

  @Test
  fun `a content root identity with an empty relative path rebuilds`() {
    val context = ContextFactory.getInstance().deserialize("file|hash|file:///root|")
    assertEquals("file|hash|file:///root|", context?.identity)
    assertEquals("project|hash", context?.parentContext?.identity)
  }

  @Test
  fun `psi file context pins the file language across its whole resolution chain`(): Unit = runBlocking {
    val file = VfsTestUtil.createFile(sourceRoot, "d/e.txt")
    readAction {
      val psiFile = requireNotNull(PsiManager.getInstance(project).findFile(file))
      val context = SettingsContext.file(psiFile)

      assertEquals(psiFile.language, context.language)
      assertTrue(context.chain().all { it.language == psiFile.language }, "the language pin propagates to every ancestor")
      assertEquals(SettingsContext.file(file, project).chain().size, context.chain().size,
                   "same structure as the plain file context it overlays")
    }
  }

  @Test
  fun `psi file context reflects the concrete file language`(): Unit = runBlocking {
    val file = VfsTestUtil.createFile(sourceRoot, "page.html")
    readAction {
      val psiFile = requireNotNull(PsiManager.getInstance(project).findFile(file))
      assertEquals("HTML", psiFile.language.id, "the fixture must actually parse as HTML")
      assertEquals("HTML", SettingsContext.file(psiFile).language?.id)
    }
  }
}

private fun SettingsContext.chain(): List<SettingsContext> = generateSequence(this) { it.parentContext }.toList()

private fun List<SettingsContext>.fileNodes(): List<String> = mapNotNull { (it as? VirtualFileSettingsContext)?.url }

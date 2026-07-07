// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.settings.v2.impl.VirtualFileSettingsContext
import com.intellij.platform.settings.v2.impl.topmostContentRoots
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Pins that a file context anchors on the **topmost** content root. A module content root can hold another one, and a
 * real project has one per source set, so an anchor on the nearest root would cut the chain short and hide the
 * directories in between. A file in a jar or outside every root gets the project context (ADR 0125).
 */
@TestApplication
class SettingsNestedRootsTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
    private val moduleFixture = projectFixture.moduleFixture()
    private val sourceRootFixture = moduleFixture.sourceRootFixture()
  }

  private val project get() = projectFixture.get()
  private val sourceRoot get() = sourceRootFixture.get().virtualFile

  @Test
  fun `a file under a nested root anchors on the outer root`(): Unit = runBlocking {
    val outer = VfsTestUtil.createDir(sourceRoot, "outer")
    val file = VfsTestUtil.createFile(outer, "module/src/main/a/Sample.java")
    addContentRoot(readAction { file.parent })

    val context = readAction { SettingsContext.file(file, project) } as VirtualFileSettingsContext

    assertEquals(sourceRoot.url, context.rootUrl)
    assertEquals("outer/module/src/main/a/Sample.java", context.relativePath)
  }

  @Test
  fun `the chain reaches every directory between the file and the outer root`(): Unit = runBlocking {
    val outer = VfsTestUtil.createDir(sourceRoot, "chain")
    val file = VfsTestUtil.createFile(outer, "module/src/Sample.java")
    addContentRoot(readAction { file.parent })

    val context = readAction { SettingsContext.file(file, project) }
    val chain = generateSequence(context) { it.parentContext }.map { it.identity }.toList()

    val expectedRelativePaths = listOf("chain/module/src/Sample.java", "chain/module/src", "chain/module", "chain", "")
    assertEquals(expectedRelativePaths.map { fileIdentity(it) }, chain.take(expectedRelativePaths.size))
    assertEquals(SettingsContext.project(project).identity, chain[expectedRelativePaths.size])
  }

  @Test
  fun `a file in a jar under the project resolves at the project`(): Unit = runBlocking {
    val jarPath = Path.of(sourceRoot.path, "lib", "sample.jar")
    Files.createDirectories(jarPath.parent)
    Files.write(jarPath, jarWithOneEntry())
    val jar = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(jarPath)!!
    val entry = readAction { JarFileSystem.getInstance().getJarRootForLocalFile(jar)!!.findChild("entry.xml")!! }

    val context = readAction { SettingsContext.file(entry, project) }

    // The jar sits at a path under the content root, and its entries belong to another file system.
    assertEquals(SettingsContext.project(project).identity, context.identity)
  }

  @Test
  fun `a file outside every root resolves at the project`(): Unit = runBlocking {
    val outside = VfsTestUtil.createFile(readAction { sourceRoot.parent }, "outside.txt")

    val context = readAction { SettingsContext.file(outside, project) }

    assertEquals(SettingsContext.project(project).identity, context.identity)
  }

  @Test
  fun `the topmost content roots drop a nested root and keep a sibling with a shared name prefix`(): Unit = runBlocking {
    addContentRoot(VfsTestUtil.createDir(sourceRoot, "topmost/module"))
    val sibling = VfsTestUtil.createDir(readAction { sourceRoot.parent }, "${sourceRoot.name}-sibling")
    addContentRoot(sibling)

    val roots = readAction { project.topmostContentRoots() }.map { it.url }

    assertEquals(listOf(sourceRoot.url, sibling.url), roots)
  }

  /** Registers [dir] as one more content root of the module. */
  private suspend fun addContentRoot(dir: VirtualFile) {
    writeIntentReadAction { PsiTestUtil.addContentRoot(moduleFixture.get(), dir) }
  }

  /** Returns the bytes of a jar with one entry, so the jar file system has something to open. */
  private fun jarWithOneEntry(): ByteArray {
    val bytes = ByteArrayOutputStream()
    ZipOutputStream(bytes).use { zip ->
      zip.putNextEntry(ZipEntry("entry.xml"))
      zip.write("<root/>".toByteArray())
      zip.closeEntry()
    }
    return bytes.toByteArray()
  }

  private fun fileIdentity(relativePath: String): String =
    VirtualFileSettingsContext(projectId = project.locationHash,
                               rootUrl = sourceRoot.url,
                               relativePath = relativePath).identity
}

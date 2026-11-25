// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.ide.impl.MultipleFileOpener
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/** A [MultipleFileOpener] takes several paths that come from outside the IDE at once. */
@TestApplication
internal class MultipleFileOpenerExternalFilesTest {
  private val projectFixture = projectFixture()
  private val tempPath by tempPathFixture()

  @TestDisposable
  lateinit var disposable: Disposable

  @Test
  fun `a file association gives several paths to the opener`(): Unit = timeoutRunBlocking {
    val projectToClose = projectFixture.get()
    val opener = maskOpeners(MultipleFileOpener.ExternalAnswer(null))
    val paths = listOf(createDirectory("a"), createDirectory("b"))
    val openProjects = ProjectManagerEx.getOpenProjects()

    assertNull(ProjectUtil.openOrImportFilesAsync(paths, "test", projectToClose))

    assertEquals(listOf(Request(paths, projectToClose, forceOpenInNewFrame = true)), opener.requests)
    assertEquals(openProjects, ProjectManagerEx.getOpenProjects())
  }

  @Test
  fun `a file association keeps one path from the opener`(): Unit = timeoutRunBlocking {
    val opener = maskOpeners(MultipleFileOpener.ExternalAnswer(null))

    assertNull(ProjectUtil.openOrImportFilesAsync(listOf(missingPath("a")), "test"))

    assertEquals(emptyList<Request>(), opener.requests)
  }

  @Test
  fun `the command line gives several paths to the opener`(): Unit = timeoutRunBlocking {
    val opener = maskOpeners(MultipleFileOpener.ExternalAnswer(null))
    val paths = listOf(createDirectory("a"), createDirectory("b"))
    val openProjects = ProjectManagerEx.getOpenProjects()

    assertNull(CommandLineProcessor.processExternalCommandLine(paths.map { it.toString() }, currentDirectory = null).project)

    assertEquals(listOf(Request(paths, projectToClose = null, forceOpenInNewFrame = false)), opener.requests)
    assertEquals(openProjects, ProjectManagerEx.getOpenProjects())
  }

  @Test
  fun `the platform continues after an opener that leaves the paths`(): Unit = timeoutRunBlocking {
    val opener = maskOpeners(answer = null)
    val paths = listOf(missingPath("a"), missingPath("b"))

    assertNull(ProjectUtil.openOrImportFilesAsync(paths, "test"))

    assertEquals(listOf(Request(paths, projectToClose = null, forceOpenInNewFrame = true)), opener.requests)
  }

  /** A test masks the openers one time: a second mask of the same point is read-only. */
  private fun maskOpeners(answer: MultipleFileOpener.ExternalAnswer?): RecordingOpener {
    val opener = RecordingOpener(answer)
    val point = ExtensionPointName.create<MultipleFileOpener>("com.intellij.multipleFileOpener")
    ExtensionTestUtil.maskExtensions(point, listOf(opener), disposable)
    return opener
  }

  private fun createDirectory(name: String): Path = Files.createDirectory(tempPath.resolve(name))

  /** The platform opens nothing for a path that does not exist. */
  private fun missingPath(name: String): Path = tempPath.resolve(name)

  private data class Request(val files: List<Path>, val projectToClose: Project?, val forceOpenInNewFrame: Boolean)

  private class RecordingOpener(private val answer: MultipleFileOpener.ExternalAnswer?) : MultipleFileOpener {
    val requests = ArrayList<Request>()

    override suspend fun openFiles(files: List<VirtualFile>, project: Project?): Boolean = false

    override suspend fun openExternalFiles(
      files: List<Path>,
      projectToClose: Project?,
      forceOpenInNewFrame: Boolean,
    ): MultipleFileOpener.ExternalAnswer? {
      requests += Request(files, projectToClose, forceOpenInNewFrame)
      return answer
    }
  }
}

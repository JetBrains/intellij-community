// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github

import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.HeavyTestHelper
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import org.jetbrains.plugins.github.api.data.request.GithubGistRequest.FileContent
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class GithubCreateGistContentTest {
  private val projectFixture = projectFixture(openAfterCreation = true)

  private val project: Project get() = projectFixture.get()

  private lateinit var projectRoot: VirtualFile
  private var editor: Editor? = null

  @BeforeEach
  fun setUp() {
    projectRoot = HeavyTestHelper.getOrCreateProjectBaseDir(project)
    createProjectFiles()
  }

  @AfterEach
  fun tearDown() {
    editor?.let { e ->
      timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) { EditorFactory.getInstance().releaseEditor(e) }
    }
  }

  @Test
  fun testCreateFromFile() = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    writeIntentReadAction {
      val expected = listOf(FileContent("file.txt", "file.txt content"))

      val file = projectRoot.findFileByRelativePath("file.txt")
      assertNotNull(file)

      val actual = collectContents(null, file, null)

      checkEquals(expected, actual)
    }
  }

  @Test
  fun testCreateFromDirectory() = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    writeIntentReadAction {
      val expected = listOf(
        FileContent("folder_file1", "file1 content"),
        FileContent("folder_file2", "file2 content"),
        FileContent("folder_dir_file3", "file3 content"),
      )

      val file = projectRoot.findFileByRelativePath("folder")
      assertNotNull(file)

      val actual = collectContents(null, file, null)

      checkEquals(expected, actual)
    }
  }

  @Test
  fun testCreateFromEmptyDirectory() = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    writeIntentReadAction {
      val expected = emptyList<FileContent>()

      val file = projectRoot.findFileByRelativePath("folder/empty_folder")
      assertNotNull(file)

      val actual = collectContents(null, file, null)

      checkEquals(expected, actual)
    }
  }

  @Test
  fun testCreateFromEmptyFile() = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    writeIntentReadAction {
      val expected = emptyList<FileContent>()

      val file = projectRoot.findFileByRelativePath("folder/empty_file")
      assertNotNull(file)

      val actual = collectContents(null, file, null)

      checkEquals(expected, actual)
    }
  }

  @Test
  fun testCreateFromFiles() = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    writeIntentReadAction {
      val expected = listOf(
        FileContent("file.txt", "file.txt content"),
        FileContent("file2", "file2 content"),
        FileContent("file3", "file3 content"),
      )

      val files = arrayOf(
        projectRoot.findFileByRelativePath("file.txt")!!,
        projectRoot.findFileByRelativePath("folder/file2")!!,
        projectRoot.findFileByRelativePath("folder/dir/file3")!!,
      )

      val actual = collectContents(null, null, files)

      checkEquals(expected, actual)
    }
  }

  @Test
  fun testCreateFromEmptyFiles() = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    writeIntentReadAction {
      val expected = emptyList<FileContent>()

      val files = VirtualFile.EMPTY_ARRAY

      val actual = collectContents(null, null, files)

      checkEquals(expected, actual)
    }
  }

  @Test
  fun testCreateFromEditor() = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    writeIntentReadAction {
      val file = projectRoot.findFileByRelativePath("file.txt")
      assertNotNull(file)

      val document = FileDocumentManager.getInstance().getDocument(file!!)
      assertNotNull(document)

      editor = EditorFactory.getInstance().createEditor(document!!, project)
      assertNotNull(editor)

      val expected = listOf(FileContent("file.txt", "file.txt content"))

      val actual = collectContents(editor, file, null)

      checkEquals(expected, actual)
    }
  }

  @Test
  fun testCreateFromEditorWithoutFile() = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    writeIntentReadAction {
      val file = projectRoot.findFileByRelativePath("file.txt")
      assertNotNull(file)

      val document = FileDocumentManager.getInstance().getDocument(file!!)
      assertNotNull(document)

      editor = EditorFactory.getInstance().createEditor(document!!, project)
      assertNotNull(editor)

      val expected = listOf(FileContent("", "file.txt content"))

      val actual = collectContents(editor, null, null)

      checkEquals(expected, actual)
    }
  }

  private fun createProjectFiles() {
    VfsTestUtil.createFile(projectRoot, "file.txt", "file.txt content")
    VfsTestUtil.createFile(projectRoot, "file", "file content")
    VfsTestUtil.createFile(projectRoot, "folder/file1", "file1 content")
    VfsTestUtil.createFile(projectRoot, "folder/file2", "file2 content")
    VfsTestUtil.createFile(projectRoot, "folder/empty_file")
    VfsTestUtil.createFile(projectRoot, "folder/dir/file3", "file3 content")
    VfsTestUtil.createDir(projectRoot, "folder/empty_folder")
  }

  private fun collectContents(editor: Editor?, file: VirtualFile?, files: Array<VirtualFile>?): List<FileContent> =
    GithubGistContentsCollector.collectContents(project, editor, file, files)

  private fun checkEquals(expected: List<FileContent>, actual: List<FileContent>) {
    assertTrue(Comparing.haveEqualElements(expected, actual), "Gist content differs from sample")
  }
}

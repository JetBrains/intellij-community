// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.snippets

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.plugins.gitlab.testutil.getGitLabTestDataPath
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.file.Path
import kotlin.io.path.absolutePathString

class GitLabSnippetServiceTest : BasePlatformTestCase() {
  override fun getTestDataPath(): String {
    val path = getGitLabTestDataPath("community/plugins/gitlab/testResources")?.absolutePathString()
    requireNotNull(path)
    return path
  }

  fun `test - canOpenDialog checks for nested files`() {
    val lfs = LocalFileSystem.getInstance()

    val e = mock<AnActionEvent>()
    whenever(e.getData(eq(CommonDataKeys.PROJECT))).thenReturn(project)
    whenever(e.getData(eq(CommonDataKeys.VIRTUAL_FILE))).thenReturn(
      lfs.findFileByNioFile(Path.of(testDataPath, "snippets/1-nested-files")))

    assertTrue(project.service<GitLabSnippetService>().canOpenDialog(e))
  }

  fun `test - canOpenDialog is false for empty files`() {
    val lfs = LocalFileSystem.getInstance()
    val file = lfs.findFileByNioFile(Path.of(testDataPath, "snippets/2-empty-file/empty.txt"))

    val document = mock<Document>()
    whenever(document.text).thenReturn("")
    whenever(document.textLength).thenReturn(0)

    val editor = mock<Editor>()
    whenever(editor.virtualFile).thenReturn(file)
    whenever(editor.document).thenReturn(document)

    val e = mock<AnActionEvent>()
    whenever(e.getData(eq(CommonDataKeys.PROJECT))).thenReturn(project)
    whenever(e.getData(eq(CommonDataKeys.EDITOR))).thenReturn(editor)

    assertFalse(project.service<GitLabSnippetService>().canOpenDialog(e))
  }

  fun `test - canOpenDialog prefers editor over selected files`() {
    val lfs = LocalFileSystem.getInstance()
    val emptyFile = lfs.findFileByNioFile(Path.of(testDataPath, "snippets/2-empty-file/empty.txt"))
    val nonEmptyFile = lfs.findFileByNioFile(Path.of(testDataPath, "snippets/1-nested-files/example.txt"))

    val document = mock<Document>()
    whenever(document.text).thenReturn("")
    whenever(document.textLength).thenReturn(0)

    val editor = mock<Editor>()
    whenever(editor.virtualFile).thenReturn(emptyFile)
    whenever(editor.document).thenReturn(document)

    val e = mock<AnActionEvent>()
    whenever(e.getData(eq(CommonDataKeys.PROJECT))).thenReturn(project)
    whenever(e.getData(eq(CommonDataKeys.EDITOR))).thenReturn(editor)
    whenever(e.getData(eq(CommonDataKeys.VIRTUAL_FILE))).thenReturn(nonEmptyFile)

    assertFalse(project.service<GitLabSnippetService>().canOpenDialog(e))
  }

  fun `test - canOpenDialog is true for directory with empty file`() {
    val lfs = LocalFileSystem.getInstance()

    val e = mock<AnActionEvent>()
    whenever(e.getData(eq(CommonDataKeys.PROJECT))).thenReturn(project)
    whenever(e.getData(eq(CommonDataKeys.VIRTUAL_FILE))).thenReturn(
      lfs.findFileByNioFile(Path.of(testDataPath, "snippets/2-empty-file")))

    assertTrue(project.service<GitLabSnippetService>().canOpenDialog(e))
  }
}
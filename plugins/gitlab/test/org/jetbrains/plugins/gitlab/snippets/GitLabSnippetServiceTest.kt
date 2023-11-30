// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.snippets

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.registerOrReplaceServiceInstance
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.testutil.getGitLabTestDataPath
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.file.Path
import kotlin.io.path.absolutePathString

class GitLabSnippetServiceTest : BasePlatformTestCase() {
  private val service by lazy { project.service<GitLabSnippetService>() }

  override fun getTestDataPath(): String {
    val path = getGitLabTestDataPath("community/plugins/gitlab/testResources")?.absolutePathString()
    requireNotNull(path)
    return path
  }

  private fun createMockAccount(): GitLabAccount {
    val acc = mock<GitLabAccount>()
    whenever(acc.id).thenReturn("550")
    whenever(acc.name).thenReturn("user")

    return acc
  }

  private fun setAccountManager(accounts: Set<GitLabAccount>) {
    val accountManager = mock<GitLabAccountManager>()
    whenever(accountManager.accountsState).thenReturn(MutableStateFlow(accounts))

    val application = ApplicationManager.getApplication()
    application.registerOrReplaceServiceInstance(GitLabAccountManager::class.java, accountManager, application)
  }

  fun `test - canOpenDialog checks for nested files`() {
    val lfs = LocalFileSystem.getInstance()
    val vf = lfs.findFileByNioFile(Path.of(testDataPath, "snippets/1-nested-files"))

    setAccountManager(setOf(createMockAccount()))

    assertTrue(service.canCreateSnippet(null, vf, null))
  }

  fun `test - canOpenDialog is false for empty files`() {
    val lfs = LocalFileSystem.getInstance()
    val file = lfs.findFileByNioFile(Path.of(testDataPath, "snippets/2-empty-file/empty.txt"))

    setAccountManager(setOf(createMockAccount()))

    val document = mock<Document>()
    whenever(document.text).thenReturn("")
    whenever(document.textLength).thenReturn(0)

    val editor = mock<Editor>()
    whenever(editor.virtualFile).thenReturn(file)
    whenever(editor.document).thenReturn(document)

    val e = mock<AnActionEvent>()
    whenever(e.getData(eq(CommonDataKeys.PROJECT))).thenReturn(project)
    whenever(e.getData(eq(CommonDataKeys.EDITOR))).thenReturn(editor)

    assertFalse(service.canCreateSnippet(editor, null, null))
  }

  fun `test - canOpenDialog prefers editor over selected files`() {
    val lfs = LocalFileSystem.getInstance()
    val emptyFile = lfs.findFileByNioFile(Path.of(testDataPath, "snippets/2-empty-file/empty.txt"))
    val nonEmptyFile = lfs.findFileByNioFile(Path.of(testDataPath, "snippets/1-nested-files/example.txt"))

    setAccountManager(setOf(createMockAccount()))

    val document = mock<Document>()
    whenever(document.text).thenReturn("")
    whenever(document.textLength).thenReturn(0)

    val editor = mock<Editor>()
    whenever(editor.virtualFile).thenReturn(emptyFile)
    whenever(editor.document).thenReturn(document)

    assertFalse(service.canCreateSnippet(editor, nonEmptyFile, null))
  }

  fun `test - canOpenDialog is true for directory with empty file`() {
    val lfs = LocalFileSystem.getInstance()

    setAccountManager(setOf(createMockAccount()))

    val vf = lfs.findFileByNioFile(Path.of(testDataPath, "snippets/2-empty-file"))

    assertTrue(service.canCreateSnippet(null, vf, null))
  }
}
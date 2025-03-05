// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.snippets

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.registerOrReplaceServiceInstance
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.testutil.getGitLabTestDataPath
import java.nio.file.Path
import kotlin.io.path.absolutePathString

class GitLabSnippetServiceTest : BasePlatformTestCase() {
  private val service by lazy { project.service<GitLabSnippetService>() }

  override fun getTestDataPath(): String {
    val path = getGitLabTestDataPath("community/plugins/gitlab/gitlab-core/testResources")?.absolutePathString()
    requireNotNull(path)
    return path
  }

  private fun createMockAccount(): GitLabAccount {
    return mockk<GitLabAccount> {
      every { id } returns "550"
      every { name } returns "user"
    }
  }

  private fun setAccountManager(accounts: Set<GitLabAccount>) {
    val accountManager = mockk<GitLabAccountManager> {
      every { accountsState } returns MutableStateFlow(accounts)
    }

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

    val document = mockk<Document> {
      every { text } returns ""
      every { textLength } returns 0
    }

    val editor = mockk<Editor> {
      every { virtualFile } returns file
      every { this@mockk.document } returns document
    }

    assertFalse(service.canCreateSnippet(editor, null, null))
  }

  fun `test - canOpenDialog prefers editor over selected files`() {
    val lfs = LocalFileSystem.getInstance()
    val emptyFile = lfs.findFileByNioFile(Path.of(testDataPath, "snippets/2-empty-file/empty.txt"))
    val nonEmptyFile = lfs.findFileByNioFile(Path.of(testDataPath, "snippets/1-nested-files/example.txt"))

    setAccountManager(setOf(createMockAccount()))

    val document = mockk<Document> {
      every { text } returns ""
      every { textLength } returns 0
    }

    val editor = mockk<Editor> {
      every { virtualFile } returns emptyFile
      every { this@mockk.document } returns document
    }

    assertFalse(service.canCreateSnippet(editor, nonEmptyFile, null))
  }

  fun `test - canOpenDialog is true for directory with empty file`() {
    val lfs = LocalFileSystem.getInstance()

    setAccountManager(setOf(createMockAccount()))

    val vf = lfs.findFileByNioFile(Path.of(testDataPath, "snippets/2-empty-file"))

    assertTrue(service.canCreateSnippet(null, vf, null))
  }
}
// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github

import com.intellij.openapi.util.Clock
import com.intellij.openapi.util.Comparing
import com.intellij.testFramework.RunAll
import com.intellij.util.ThrowableRunnable
import com.intellij.util.text.DateFormatUtil
import org.jetbrains.plugins.github.api.GithubApiRequests
import org.jetbrains.plugins.github.api.data.GithubGist
import org.jetbrains.plugins.github.api.data.request.GithubGistRequest.FileContent
import org.jetbrains.plugins.github.test.GithubTest
import java.io.IOException
import java.util.*

abstract class GithubCreateGistTestBase : GithubTest() {
  protected lateinit var gistDescription: String
  protected var gistId: String? = null
  private val gist: GithubGist by lazy {
    assertNotNull(gistId)
    val loaded = mainAccount.executor.execute(GithubApiRequests.Gists.get(mainAccount.account.server, gistId!!))
    assertNotNull("Gist does not exist", loaded)
    loaded!!
  }

  override fun setUp() {
    super.setUp()

    val time = Clock.getTime()
    gistDescription = getTestName(false) + "_" + DateFormatUtil.formatDate(time)
  }

  override fun tearDown() {
    RunAll(
      ThrowableRunnable { deleteGist() },
      ThrowableRunnable { super.tearDown() }
    ).run()
  }

  @Throws(IOException::class)
  private fun deleteGist() {
    if (gistId != null) {
      mainAccount.executor.execute(GithubApiRequests.Gists.delete(mainAccount.account.server, gistId!!))
      gistId = null
    }
  }

  protected fun checkGistExists() {
    gist
  }

  protected fun checkGistPublic() {
    val result = gist

    assertTrue("Gist is not public", result.isPublic)
  }

  protected fun checkGistSecret() {
    val result = gist

    assertFalse("Gist is not private", result.isPublic)
  }

  protected fun checkGistNotAnonymous() {
    val result = gist

    assertFalse("Gist is not anonymous", result.user == null)
  }

  protected fun checkGistDescription(expected: String) {
    val result = gist

    assertEquals("Gist content differs from sample", expected, result.description)
  }

  protected fun checkGistContent(expected: List<FileContent>) {
    val result = gist

    val files = ArrayList<FileContent>()
    for (file in result.files) {
      files.add(FileContent(file.filename, file.content))
    }

    assertTrue("Gist content differs from sample", Comparing.haveEqualElements(files, expected))
  }

  companion object {

    fun createContent(): List<FileContent> {
      val content = ArrayList<FileContent>()

      content.add(FileContent("file1", "file1 content"))
      content.add(FileContent("file2", "file2 content"))
      content.add(FileContent("dir_file3", "file3 content"))

      return content
    }
  }
}

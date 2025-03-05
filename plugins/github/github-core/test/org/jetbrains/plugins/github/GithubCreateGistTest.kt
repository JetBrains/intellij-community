// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github

import com.intellij.idea.IJIgnore
import com.intellij.notification.NotificationType
import com.intellij.openapi.progress.DumbProgressIndicator
import org.jetbrains.plugins.github.api.data.request.GithubGistRequest.FileContent
import org.junit.Ignore

@Ignore
@IJIgnore(issue = "no server")
class GithubCreateGistTest : GithubCreateGistTestBase() {
  private val indicator = DumbProgressIndicator.INSTANCE

  fun testSimple() {
    val expected = createContent()

    val url = GithubCreateGistAction.createGist(myProject, mainAccount.executor, indicator, mainAccount.account.server,
                                                expected, true, gistDescription, null)
    assertNotNull(url)
    gistId = url!!.substring(url.lastIndexOf('/') + 1)

    checkGistExists()
    checkGistNotAnonymous()
    checkGistSecret()
    checkGistDescription(gistDescription)
    checkGistContent(expected)
  }

  fun testUnusedFilenameField() {
    val expected = createContent()

    val url = GithubCreateGistAction.createGist(myProject, mainAccount.executor, indicator, mainAccount.account.server,
                                                expected, true, gistDescription, "filename")
    assertNotNull(url)
    gistId = url!!.substring(url.lastIndexOf('/') + 1)

    checkGistExists()
    checkGistNotAnonymous()
    checkGistSecret()
    checkGistDescription(gistDescription)
    checkGistContent(expected)
  }

  fun testUsedFilenameField() {
    val content = listOf(FileContent("file.txt", "file.txt content"))
    val expected = listOf(FileContent("filename", "file.txt content"))

    val url = GithubCreateGistAction.createGist(myProject, mainAccount.executor, indicator, mainAccount.account.server,
                                                content, true, gistDescription, "filename")
    assertNotNull(url)
    gistId = url!!.substring(url.lastIndexOf('/') + 1)

    checkGistExists()
    checkGistNotAnonymous()
    checkGistSecret()
    checkGistDescription(gistDescription)
    checkGistContent(expected)
  }

  fun testPublic() {
    val expected = createContent()

    val url = GithubCreateGistAction.createGist(myProject, mainAccount.executor, indicator, mainAccount.account.server,
                                                expected, false, gistDescription, null)
    assertNotNull(url)
    gistId = url!!.substring(url.lastIndexOf('/') + 1)

    checkGistExists()
    checkGistNotAnonymous()
    checkGistPublic()
    checkGistDescription(gistDescription)
    checkGistContent(expected)
  }

  fun testEmpty() {
    val expected = emptyList<FileContent>()

    val url = GithubCreateGistAction.createGist(myProject, mainAccount.executor, indicator, mainAccount.account.server,
                                                expected, true, gistDescription, null)
    assertNull("Gist was created", url)

    checkNotification(NotificationType.WARNING, "Can't create Gist", "Can't create empty gist")
  }
}

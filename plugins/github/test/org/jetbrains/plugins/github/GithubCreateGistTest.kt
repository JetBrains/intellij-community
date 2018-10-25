/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.github

import com.intellij.notification.NotificationType
import com.intellij.openapi.progress.DumbProgressIndicator
import org.jetbrains.plugins.github.api.requests.GithubGistRequest.FileContent

/**
 * @author Aleksey Pivovarov
 */
class GithubCreateGistTest : GithubCreateGistTestBase() {
  private val myIndicator = DumbProgressIndicator.INSTANCE

  fun testSimple() {
    val expected = GithubCreateGistTestBase.createContent()

    val url = GithubCreateGistAction.createGist(myProject, myExecutor, myIndicator, myAccount.server, expected, true, GIST_DESCRIPTION,
                                                null)
    TestCase.assertNotNull(url)
    GIST_ID = url!!.substring(url.lastIndexOf('/') + 1)

    checkGistExists()
    checkGistNotAnonymous()
    checkGistSecret()
    checkGistDescription(GIST_DESCRIPTION)
    checkGistContent(expected)
  }

  fun testUnusedFilenameField() {
    val expected = GithubCreateGistTestBase.createContent()

    val url = GithubCreateGistAction
      .createGist(myProject, myExecutor, myIndicator, myAccount.server, expected, true, GIST_DESCRIPTION, "filename")
    TestCase.assertNotNull(url)
    GIST_ID = url!!.substring(url.lastIndexOf('/') + 1)

    checkGistExists()
    checkGistNotAnonymous()
    checkGistSecret()
    checkGistDescription(GIST_DESCRIPTION)
    checkGistContent(expected)
  }

  fun testUsedFilenameField() {
    val content = listOf(FileContent("file.txt", "file.txt content"))
    val expected = listOf(FileContent("filename", "file.txt content"))

    val url = GithubCreateGistAction
      .createGist(myProject, myExecutor, myIndicator, myAccount.server, content, true, GIST_DESCRIPTION, "filename")
    TestCase.assertNotNull(url)
    GIST_ID = url!!.substring(url.lastIndexOf('/') + 1)

    checkGistExists()
    checkGistNotAnonymous()
    checkGistSecret()
    checkGistDescription(GIST_DESCRIPTION)
    checkGistContent(expected)
  }

  fun testPublic() {
    val expected = GithubCreateGistTestBase.createContent()

    val url = GithubCreateGistAction.createGist(myProject, myExecutor, myIndicator, myAccount.server, expected, false, GIST_DESCRIPTION,
                                                null)
    TestCase.assertNotNull(url)
    GIST_ID = url!!.substring(url.lastIndexOf('/') + 1)

    checkGistExists()
    checkGistNotAnonymous()
    checkGistPublic()
    checkGistDescription(GIST_DESCRIPTION)
    checkGistContent(expected)
  }

  fun testEmpty() {
    val expected = emptyList<FileContent>()

    val url = GithubCreateGistAction.createGist(myProject, myExecutor, myIndicator, myAccount.server, expected, true, GIST_DESCRIPTION,
                                                null)
    TestCase.assertNull("Gist was created", url)

    checkNotification(NotificationType.WARNING, "Can't create Gist", "Can't create empty gist")
  }
}

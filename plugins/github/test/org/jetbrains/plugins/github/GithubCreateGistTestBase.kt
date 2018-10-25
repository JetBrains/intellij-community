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

import com.intellij.openapi.util.Clock
import com.intellij.openapi.util.Comparing
import com.intellij.util.text.DateFormatUtil
import org.jetbrains.plugins.github.api.GithubApiRequests
import org.jetbrains.plugins.github.api.data.GithubGist
import org.jetbrains.plugins.github.api.requests.GithubGistRequest.FileContent
import org.jetbrains.plugins.github.test.GithubTest
import java.io.IOException
import java.util.*

/**
 * @author Aleksey Pivovarov
 */
abstract class GithubCreateGistTestBase : GithubTest() {
  protected var GIST_ID: String? = null
  protected var GIST: GithubGist? = null
  protected var GIST_DESCRIPTION: String

  protected val gist: GithubGist
    get() {
      TestCase.assertNotNull(GIST_ID)

      if (GIST == null) {
        try {
          GIST = myExecutor.execute<GithubGist>(GithubApiRequests.Gists.get(myAccount.server, GIST_ID!!))
        }
        catch (e: IOException) {
          System.err.println(e.message)
        }

      }

      TestCase.assertNotNull("Gist does not exist", GIST)
      return GIST
    }

  override fun beforeTest() {
    val time = Clock.getTime()
    GIST_DESCRIPTION = getTestName(false) + "_" + DateFormatUtil.formatDate(time)
  }

  @Throws(Exception::class)
  override fun afterTest() {
    deleteGist()
  }

  @Throws(IOException::class)
  protected fun deleteGist() {
    if (GIST_ID != null) {
      myExecutor.execute(GithubApiRequests.Gists.delete(myAccount.server, GIST_ID!!))
      GIST = null
      GIST_ID = null
    }
  }

  protected fun checkGistExists() {
    gist
  }

  protected fun checkGistPublic() {
    val result = gist

    TestCase.assertTrue("Gist is not public", result.isPublic)
  }

  protected fun checkGistSecret() {
    val result = gist

    TestCase.assertFalse("Gist is not private", result.isPublic)
  }

  protected fun checkGistNotAnonymous() {
    val result = gist

    TestCase.assertFalse("Gist is not anonymous", result.user == null)
  }

  protected fun checkGistDescription(expected: String) {
    val result = gist

    TestCase.assertEquals("Gist content differs from sample", expected, result.description)
  }

  protected fun checkGistContent(expected: List<FileContent>) {
    val result = gist

    val files = ArrayList<FileContent>()
    for (file in result.files) {
      files.add(FileContent(file.filename, file.content))
    }

    TestCase.assertTrue("Gist content differs from sample", Comparing.haveEqualElements(files, expected))
  }

  companion object {

    protected fun createContent(): List<FileContent> {
      val content = ArrayList<FileContent>()

      content.add(FileContent("file1", "file1 content"))
      content.add(FileContent("file2", "file2 content"))
      content.add(FileContent("dir_file3", "file3 content"))

      return content
    }
  }
}

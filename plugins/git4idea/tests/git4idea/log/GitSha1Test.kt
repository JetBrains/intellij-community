/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package git4idea.log

import com.intellij.openapi.vcs.Executor.cd
import com.intellij.openapi.vcs.Executor.touch
import com.intellij.openapi.vcs.changes.patch.BlobIndexUtil
import git4idea.GitVcs
import git4idea.test.GitExecutor.*
import git4idea.test.GitSingleRepoTest
import git4idea.test.GitTestUtil
import junit.framework.TestCase

class GitSha1Test : GitSingleRepoTest() {
  var AFILE = "a.txt"
  val BFILE = "b.txt"

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()
    try {
      initTest()
    }
    catch (e: Exception) {
      super.tearDown()
      throw e
    }

  }

  private fun initTest() {
    myVcs = GitVcs.getInstance(myProject)!!
    TestCase.assertNotNull(myVcs)
    GitTestUtil.createFileStructure(myProjectRoot, AFILE, BFILE)
    addCommit("initial")
  }

  fun `test sha for add`() {
    cd(myProjectPath)
    val newFile = "newFile.txt"
    touch(newFile, "Hello World!")
    add(newFile)
    updateChangeListManager()
    val changes = changeListManager.allChanges
    assertTrue(changes.size == 1)
    val beforeAfterSha1 = BlobIndexUtil.getBeforeAfterSha1(changes.elementAt(0))
    TestCase.assertEquals(BlobIndexUtil.NOT_COMMITTED_HASH, (beforeAfterSha1.first))
    TestCase.assertEquals(git("hash-object " + newFile), beforeAfterSha1.second)
  }

  fun `test sha for del`() {
    cd(myProjectPath)
    val virtualFile = myProjectRoot.findChild(AFILE)
    val path = virtualFile!!.path
    val expectedBefore = git("hash-object " + path)
    git("rm " + path)
    updateChangeListManager()
    val changes = changeListManager.allChanges
    assertTrue(changes.size == 1)
    val beforeAfterSha1 = BlobIndexUtil.getBeforeAfterSha1(changes.elementAt(0))
    TestCase.assertEquals(BlobIndexUtil.NOT_COMMITTED_HASH, beforeAfterSha1.second)
    TestCase.assertEquals(expectedBefore, beforeAfterSha1.first)
  }

  fun `test sha for modified`() {
    cd(myProjectPath)
    val virtualFile = myProjectRoot.findChild(AFILE)
    val path = virtualFile!!.path
    val expectedBefore = git("hash-object " + path)
    append(AFILE, "echo content\n with line separator")
    myProjectRoot.refresh(false, true)
    updateChangeListManager()
    val changes = changeListManager.allChanges
    assertTrue(changes.size == 1)
    val expectedAfter = git("hash-object " + path)
    val beforeAfterSha1 = BlobIndexUtil.getBeforeAfterSha1(changes.elementAt(0))
    TestCase.assertEquals(expectedBefore, beforeAfterSha1.first)
    TestCase.assertEquals(expectedAfter, beforeAfterSha1.second)
  }
}


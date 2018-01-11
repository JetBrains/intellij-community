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
import com.intellij.openapi.vfs.LocalFileSystem
import git4idea.test.GitSingleRepoTest
import git4idea.test.add
import git4idea.test.addCommit
import git4idea.test.createFileStructure
import java.nio.file.Paths

class GitSha1Test : GitSingleRepoTest() {
  var A_FILE = "a.txt"

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()
    createFileStructure(projectRoot, A_FILE)
    addCommit("initial")
  }

  fun `test sha for add`() {
    cd(projectPath)
    val newFile = "newFile.txt"
    touch(newFile, "Hello World!")
    add(newFile)
    checkSha1ForSingleChange(BlobIndexUtil.NOT_COMMITTED_HASH, git("hash-object $newFile"))
  }

  fun `test sha for del`() {
    cd(projectPath)
    val path = Paths.get(projectPath, A_FILE)
    val expectedBefore = git("hash-object $path")
    git("rm $path")
    checkSha1ForSingleChange(expectedBefore, BlobIndexUtil.NOT_COMMITTED_HASH)
  }

  fun `test sha for modified`() {
    cd(projectPath)
    val path = Paths.get(projectPath, A_FILE)
    val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(path.toFile())!!
    val expectedBefore = git("hash-object $path")
    setFileText(virtualFile, "echo content\n with line separator")
    checkSha1ForSingleChange(expectedBefore, git("hash-object $path"))
  }

  private fun checkSha1ForSingleChange(expectedBefore: String?, expectedAfter: String?) {
    updateChangeListManager()
    val changes = changeListManager.allChanges
    assertTrue(changes.size == 1)
    val beforeAfterSha1 = BlobIndexUtil.getBeforeAfterSha1(changes.first())
    assertEquals(expectedBefore, beforeAfterSha1.first)
    assertEquals(expectedAfter, beforeAfterSha1.second)
  }
}


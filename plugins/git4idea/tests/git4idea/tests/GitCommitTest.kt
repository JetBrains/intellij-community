/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package git4idea.tests

import com.intellij.openapi.vcs.Executor.overwrite
import com.intellij.openapi.vcs.VcsException
import com.intellij.util.containers.ContainerUtil
import git4idea.test.GitExecutor.*
import git4idea.test.GitSingleRepoTest
import git4idea.test.GitTestUtil.createFileStructure
import java.io.File
import java.util.*

class GitCommitTest : GitSingleRepoTest() {

  // IDEA-50318
  fun `test merge commit with spaces in path`() {
    val PATH = "dir with spaces/file with spaces.txt"
    createFileStructure(myProjectRoot, PATH)
    addCommit("created some file structure")

    git("branch feature")

    val file = File(myProjectPath, PATH)
    assertTrue("File doesn't exist!", file.exists())
    overwrite(file, "my content")
    addCommit("modified in master")

    checkout("feature")
    overwrite(file, "brother content")
    addCommit("modified in feature")

    checkout("master")
    git("merge feature", true) // ignoring non-zero exit-code reporting about conflicts
    overwrite(file, "merged content") // manually resolving conflict
    git("add .")

    updateChangeListManager()
    val changeList = myChangeListManager.defaultChangeList
    changeList.comment = "Commit message"
    val changes = ArrayList(myChangeListManager.getChangesIn(myProjectRoot))
    assertTrue(!changes.isEmpty())

    val exceptions = myVcs.checkinEnvironment!!.commit(changes, "comment")
    assertNoExceptions(exceptions)

    updateChangeListManager()
    assertTrue(myChangeListManager.getChangesIn(myProjectRoot).isEmpty())
  }

  private fun assertNoExceptions(exceptions: List<VcsException>?) {
    val ex = ContainerUtil.getFirstItem(exceptions)
    if (ex != null) {
      LOG.error(ex)
      fail("Exception during executing the commit: " + ex.message)
    }
  }
}

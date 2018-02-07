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

import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.Executor.*
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
import com.intellij.util.containers.ContainerUtil
import git4idea.GitUtil
import git4idea.checkin.GitCheckinEnvironment
import git4idea.config.GitVersion
import git4idea.test.*
import org.junit.Assume.assumeTrue
import java.io.File
import java.util.*

class GitCommitTest : GitSingleRepoTest() {

  override fun getDebugLogCategories() = super.getDebugLogCategories().plus("#" + GitCheckinEnvironment::class.java.name)

  // IDEA-50318
  fun `test merge commit with spaces in path`() {
    val PATH = "dir with spaces/file with spaces.txt"
    createFileStructure(projectRoot, PATH)
    addCommit("created some file structure")

    git("branch feature")

    val file = File(projectPath, PATH)
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
    val changes = changeListManager.allChanges
    assertTrue(!changes.isEmpty())

    commit(changes)

    assertNoChanges()
  }

  fun `test commit case rename`() {
    generateCaseRename("a.java", "A.java")

    val changes = assertChanges {
      rename("a.java", "A.java")
    }
    commit(changes)
    assertNoChanges()
    repo.assertCommitted {
      rename("a.java", "A.java")
    }
  }

  fun `test commit case rename + one staged file`() {
    generateCaseRename("a.java", "A.java")
    touch("s.java")
    git("add s.java")

    val changes = assertChanges {
      rename("a.java", "A.java")
      added("s.java")
    }

    commit(changes)

    assertNoChanges()
    repo.assertCommitted {
      rename("a.java", "A.java")
      added("s.java")
    }
  }

  fun `test commit case rename + one unstaged file`() {
    tac("m.java")
    generateCaseRename("a.java", "A.java")
    echo("m.java", "unstaged")

    val changes = assertChanges {
      rename("a.java", "A.java")
      modified("m.java")
    }

    commit(changes)

    assertNoChanges()
    repo.assertCommitted {
      rename("a.java", "A.java")
      modified("m.java")
    }
  }

  fun `test commit case rename & don't commit one unstaged file`() {
    tac("m.java")
    generateCaseRename("a.java", "A.java")
    echo("m.java", "unstaged")

    val changes = assertChanges {
      rename("a.java", "A.java")
      modified("m.java")
    }

    commit(listOf(changes[0]))

    assertChanges {
      modified("m.java")
    }
    repo.assertCommitted {
      rename("a.java", "A.java")
    }
  }

  fun `test commit case rename & don't commit one staged file`() {
    `assume version where git reset returns 0 exit code on success `()

    tac("s.java")
    generateCaseRename("a.java", "A.java")
    echo("s.java", "staged")
    git("add s.java")

    val changes = assertChanges {
      rename("a.java", "A.java")
      modified("s.java")
    }

    commit(listOf(changes[0]))

    repo.assertCommitted {
      rename("a.java", "A.java")
    }
    assertChanges {
      modified("s.java")
    }
    repo.assertStagedChanges {
      modified("s.java")
    }
  }

  fun `test commit case rename & don't commit one staged simple rename, then rename should remain staged`() {
    `assume version where git reset returns 0 exit code on success `()

    echo("before.txt", "some\ncontent\nere")
    addCommit("created before.txt")
    generateCaseRename("a.java", "A.java")
    git("mv before.txt after.txt")

    val changes = assertChanges {
      rename("a.java", "A.java")
      rename("before.txt", "after.txt")
    }

    commit(listOf(changes[0]))

    repo.assertCommitted {
      rename("a.java", "A.java")
    }
    assertChanges {
      rename("before.txt", "after.txt")
    }
    repo.assertStagedChanges {
      rename("before.txt", "after.txt")
    }
  }

  fun `test commit case rename + one unstaged file & don't commit one staged file`() {
    `assume version where git reset returns 0 exit code on success `()

    tac("s.java")
    tac("m.java")
    generateCaseRename("a.java", "A.java")
    echo("s.java", "staged")
    echo("m.java", "unstaged")
    git("add s.java m.java")

    val changes = assertChanges {
      rename("a.java", "A.java")
      modified("s.java")
      modified("m.java")
    }

    commit(listOf(changes[0], changes[2]))

    repo.assertCommitted {
      rename("a.java", "A.java")
      modified("m.java")
    }
    assertChanges {
      modified("s.java")
    }
    repo.assertStagedChanges {
      modified("s.java")
    }
  }

  fun `test commit case rename & don't commit a file which is both staged and unstaged, should reset and restore`() {
    `assume version where git reset returns 0 exit code on success `()

    tac("c.java", "initial")
    generateCaseRename("a.java", "A.java")
    val STAGED_CONTENT = "staged"
    overwrite("c.java", STAGED_CONTENT)
    git("add c.java")
    val UNSTAGED_CONTENT = "unstaged"
    overwrite("c.java", UNSTAGED_CONTENT)

    val changes = assertChanges {
      rename("a.java", "A.java")
      modified("c.java")
    }

    commit(listOf(changes[0]))

    repo.assertCommitted {
      rename("a.java", "A.java")
    }
    assertChanges {
      modified("c.java")
    }
    repo.assertStagedChanges {
      modified("c.java")
    }

    val expectedIndexContent = if (SystemInfo.isFileSystemCaseSensitive) {
      STAGED_CONTENT
    }
    else {
      // this is intentional data loss: it is a rare case, while restoring both staged and unstaged part is not so easy,
      // so we are not doing it, at least until IDEA supports Git index
      // (which will mean that users will be able to produce such situation intentionally with a help of IDE).
      UNSTAGED_CONTENT
    }
    assertEquals(expectedIndexContent, git("show :c.java"))
    assertEquals(UNSTAGED_CONTENT, FileUtil.loadFile(File(projectPath, "c.java")))
  }

  fun `test commit case rename with additional non-staged changes should commit everything`() {
    val initialContent = """
      some
      large
      content
      to let
      rename
      detection
      work
    """.trimIndent()
    touch("a.java", initialContent)
    addCommit("initial a.java")
    git("mv -f a.java A.java")
    val additionalContent = "non-staged content"
    append("A.java", additionalContent)

    val changes = assertChanges {
      rename("a.java", "A.java")
    }

    commit(changes)

    repo.assertCommitted {
      rename("a.java", "A.java")
    }
    assertNoChanges()
    assertEquals(initialContent + additionalContent, git("show HEAD:A.java"))
  }

  private fun `assume version where git reset returns 0 exit code on success `() {
    assumeTrue("Not testing: git reset returns 1 and fails the commit process in ${vcs.version}",
               vcs.version.isLaterOrEqual(GitVersion(1, 8, 2, 0)))
  }

  private fun generateCaseRename(from: String, to: String) {
    tac(from)
    git("mv -f $from $to")
  }

  private fun commit(changes: Collection<Change>) {
    val exceptions = vcs.checkinEnvironment!!.commit(ArrayList(changes), "comment")
    assertNoExceptions(exceptions)
    updateChangeListManager()
  }

  private fun assertNoChanges() {
    val changes = changeListManager.getChangesIn(projectRoot)
    assertTrue("We expected no changes but found these: " + GitUtil.getLogString(projectPath, changes), changes.isEmpty())
  }

  private fun assertNoExceptions(exceptions: List<VcsException>?) {
    val ex = ContainerUtil.getFirstItem(exceptions)
    if (ex != null) {
      LOG.error(ex)
      fail("Exception during executing the commit: " + ex.message)
    }
  }

  private fun assertChanges(changes: ChangesBuilder.() -> Unit) : List<Change> {
    val cb = ChangesBuilder()
    cb.changes()

    updateChangeListManager()
    val allChanges = mutableListOf<Change>()
    val actualChanges = HashSet(changeListManager.allChanges)

    for (change in cb.changes) {
      val found = actualChanges.find(change.matcher)
      assertNotNull("The change [$change] not found", found)
      actualChanges.remove(found)
      allChanges.add(found!!)
    }
    assertTrue(actualChanges.isEmpty())
    return allChanges
  }
}

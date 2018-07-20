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

import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.Executor.*
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
import com.intellij.util.containers.ContainerUtil
import git4idea.GitUtil
import git4idea.checkin.GitCheckinEnvironment
import git4idea.checkin.GitCheckinExplicitMovementProvider
import git4idea.config.GitVersion
import git4idea.test.*
import org.junit.Assume.assumeTrue
import java.io.File
import java.util.*


open class GitCommitTest : GitSingleRepoTest() {
  private val myMovementProvider = MyExplicitMovementProvider()

  override fun getDebugLogCategories() = super.getDebugLogCategories().plus("#" + GitCheckinEnvironment::class.java.name)

  override fun setUp() {
    super.setUp()

    val point = Extensions.getRootArea().getExtensionPoint(GitCheckinExplicitMovementProvider.EP_NAME)
    point.registerExtension(myMovementProvider)
    Registry.get("git.allow.explicit.commit.renames").setValue(true)

    (vcs.checkinEnvironment as GitCheckinEnvironment).setCommitRenamesSeparately(true)
  }

  override fun tearDown() {
    try {
      val point = Extensions.getRootArea().getExtensionPoint(GitCheckinExplicitMovementProvider.EP_NAME)
      point.unregisterExtension(myMovementProvider)
      Registry.get("git.allow.explicit.commit.renames").resetToDefault()

      (vcs.checkinEnvironment as GitCheckinEnvironment).setCommitRenamesSeparately(false)
    }
    finally {
      super.tearDown()
    }
  }

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

  fun `test commit with excluded staged rename`() {
    tac("a.java", "old content")
    tac("b.java")

    git("mv -f b.java c.java")
    overwrite("a.java", "new content")

    val changes = assertChanges {
      modified("a.java")
      rename("b.java", "c.java")
    }

    commit(listOf(changes[0]))

    assertChanges {
      rename("b.java", "c.java")
    }
    repo.assertCommitted {
      modified("a.java")
    }
  }

  fun `test commit with excluded case rename`() {
    tac("a.java", "old content")
    tac("b.java")

    git("mv -f b.java B.java")
    overwrite("a.java", "new content")

    val changes = assertChanges {
      modified("a.java")
      rename("b.java", "B.java")
    }

    commit(listOf(changes[0]))

    assertChanges {
      rename("b.java", "B.java")
    }
    repo.assertCommitted {
      modified("a.java")
    }
  }

  fun `test commit staged rename`() {
    tac("b.java")

    git("mv -f b.java c.java")

    val changes = assertChanges {
      rename("b.java", "c.java")
    }

    commit(changes)

    assertNoChanges()
    repo.assertCommitted {
      rename("b.java", "c.java")
    }
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

  fun `test commit unstaged case rename - case ignored on case insensitive system`() {
    assumeTrue(!SystemInfo.isFileSystemCaseSensitive)

    tac("a.java", "old content")
    rm("a.java")
    touch("A.java", "new content")

    val changes = assertChanges {
      modified("a.java")
    }
    commit(changes)
    assertNoChanges()
    repo.assertCommitted {
      modified("a.java")
    }
  }

  fun `test commit wrongly staged case rename - case ignored on case insensitive system`() {
    assumeTrue(!SystemInfo.isFileSystemCaseSensitive)

    tac("a.java", "old content")
    rm("a.java")
    touch("A.java", "new content")
    git("add -A a.java A.java")

    val changes = assertChanges {
      modified("a.java")
    }
    commit(changes)
    assertNoChanges()
    repo.assertCommitted {
      modified("a.java")
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

  fun `test commit case rename with another staged rename`() {
    tac("a.java")
    tac("b.java")

    git("mv -f a.java c.java")
    git("mv -f b.java B.java")

    val changes = assertChanges {
      rename("b.java", "B.java")
      rename("a.java", "c.java")
    }

    commit(changes)

    assertNoChanges()
    repo.assertCommitted {
      rename("b.java", "B.java")
      rename("a.java", "c.java")
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

  fun `test commit case rename & don't commit one staged case rename`() {
    `assume version where git reset returns 0 exit code on success `()

    tac("s.java")
    generateCaseRename("a.java", "A.java")
    git("mv s.java S.java")

    val changes = assertChanges {
      rename("a.java", "A.java")
      rename("s.java", "S.java")
    }

    commit(listOf(changes[0]))

    repo.assertCommitted {
      rename("a.java", "A.java")
    }
    assertChanges {
      rename("s.java", "S.java")
    }
    repo.assertStagedChanges {
      rename("s.java", "S.java")
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

    val expectedIndexContent = if (SystemInfo.isFileSystemCaseSensitive && !Registry.`is`("git.force.commit.using.staging.area")) {
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

  fun `test commit explicit rename`() {
    `assume version where git reset returns 0 exit code on success `()

    tac("a.before", "before content")

    rm("a.before")
    touch("a.after", "after content")
    git("add a.after")


    val changes = assertChanges {
      deleted("a.before")
      added("a.after")
    }

    commit(changes)
    assertNoChanges()

    assertMessage("comment", repo.message("HEAD"))
    assertMessage("explicit movement in tests", repo.message("HEAD~1"))

    repo.assertCommitted(1) {
      modified("a.after")
    }
    repo.assertCommitted(2) {
      rename("a.before", "a.after")
    }
  }

  fun `test commit explicit rename + one unstaged file & don't commit one staged file`() {
    `assume version where git reset returns 0 exit code on success `()

    tac("s.java")
    tac("m.java")
    tac("a.before")

    rm("a.before")
    touch("a.after", "after content")
    git("add a.after")

    echo("s.java", "staged")
    echo("m.java", "unstaged")
    git("add s.java m.java")

    val changes = assertChanges {
      deleted("a.before")
      added("a.after")
      modified("m.java")
      modified("s.java")
    }

    commit(listOf(changes[0], changes[1], changes[2]))

    assertChanges {
      modified("s.java")
    }
    repo.assertStagedChanges {
      modified("s.java")
    }

    assertMessage("comment", repo.message("HEAD"))
    assertMessage("explicit movement in tests", repo.message("HEAD~1"))

    repo.assertCommitted(1) {
      modified("a.after")
      modified("m.java")
    }
    repo.assertCommitted(2) {
      rename("a.before", "a.after")
    }
  }

  fun `test commit rename with conflicting staged rename`() {
    `assume version where git reset returns 0 exit code on success `()
    assumeTrue(Registry.`is`("git.force.commit.using.staging.area")) // known bug in "--only" implementation

    tac("a.txt", "file content")

    rm("a.txt")
    touch("b.txt", "file content")
    touch("c.txt", "file content")
    git("add a.txt")
    git("add b.txt")

    val changes = assertChanges {
      rename("a.txt", "b.txt")
    }

    git("add c.txt")
    git("rm b.txt --cached")
    assertChanges {
      rename("a.txt", "c.txt")
    }

    commit(changes)

    assertChanges {
      added("c.txt")
    }

    assertMessage("comment", repo.message("HEAD"))

    repo.assertCommitted {
      rename("a.txt", "b.txt")
    }
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
    val vcsChanges = changeListManager.allChanges
    val allChanges = mutableListOf<Change>()
    val actualChanges = HashSet(vcsChanges)

    for (change in cb.changes) {
      val found = actualChanges.find(change.matcher)
      assertNotNull("The change [$change] not found\n$vcsChanges", found)
      actualChanges.remove(found)
      allChanges.add(found!!)
    }
    assertTrue(actualChanges.isEmpty())
    return allChanges
  }

  private class MyExplicitMovementProvider : GitCheckinExplicitMovementProvider() {
    override fun isEnabled(project: Project): Boolean = true

    override fun getDescription(): String = "explicit movement in tests"

    override fun getCommitMessage(originalCommitMessage: String): String = description

    override fun collectExplicitMovements(project: Project,
                                          beforePaths: MutableList<FilePath>,
                                          afterPaths: MutableList<FilePath>): MutableCollection<Movement> {
      val beforeMap = beforePaths.filter { it.name.endsWith(".before") }
        .associate { it.name.removeSuffix(".before") to it }

      val afterMap = afterPaths.filter { it.name.endsWith(".after") }
        .associate { it.name.removeSuffix(".after") to it }

      val movedChanges = ArrayList<Movement>()
      for (key in (beforeMap.keys + afterMap.keys)) {
        val beforePath = beforeMap[key] ?: continue
        val afterPath = afterMap[key] ?: continue
        movedChanges.add(Movement(beforePath, afterPath))
      }

      return movedChanges
    }
  }
}

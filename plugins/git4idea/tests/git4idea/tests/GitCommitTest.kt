// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.tests

import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.IoTestUtil
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.IssueNavigationConfiguration
import com.intellij.openapi.vcs.IssueNavigationLink
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.testFramework.junit5.eel.params.api.DockerTest
import com.intellij.platform.testFramework.junit5.eel.params.api.EelHolder
import com.intellij.platform.testFramework.junit5.eel.params.api.TestApplicationWithEel
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.extensionPointFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.registryKeyFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.vcs.commit.CommitExceptionWithActions
import com.intellij.vcs.test.updateChangeListManager
import com.intellij.vcs.test.vcsPlatformFixture
import git4idea.checkin.GitCheckinExplicitMovementProvider
import git4idea.checkin.isCommitRenamesSeparately
import git4idea.config.GitConfigUtil
import git4idea.config.GitSaveChangesPolicy
import git4idea.config.GitVersion
import git4idea.i18n.GitBundle
import git4idea.test.GitSingleRepoContext
import git4idea.test.addCommit
import git4idea.test.assertChangesWithRefresh
import git4idea.test.assertCommitted
import git4idea.test.assertMessage
import git4idea.test.assertNoChanges
import git4idea.test.assertStagedChanges
import git4idea.test.checkout
import git4idea.test.commit
import git4idea.test.createFileStructure
import git4idea.test.createSubRepository
import git4idea.test.git
import git4idea.test.gitPlatformFixture
import git4idea.test.gitSingleRepoFixture
import git4idea.test.message
import git4idea.test.tac
import git4idea.test.tryCommit
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.params.ParameterizedClass
import java.io.File
import kotlin.io.path.exists
import kotlin.test.DefaultAsserter.assertEquals
import kotlin.test.DefaultAsserter.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertTrue


internal abstract class GitCommitTestBase(@Suppress("unused") val eelHolder: EelHolder, val gitCommitFixture: TestFixture<GitCommitContext>) {

  protected abstract fun `verify test commit case rename & don't commit a file which is both staged and unstaged, should reset and restore unstaged`()
  protected abstract fun `verify test commit with excluded added-deleted and added files`()

  // IDEA-50318
  @Test
  fun `test merge commit with spaces in path`() = with(gitCommitFixture.get()) {
    val PATH = "dir with spaces/file with spaces.txt"
    createFileStructure(projectRoot, PATH)
    addCommit("created some file structure")

    git("branch feature")

    val file = projectNioRoot.resolve(PATH)
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

  @Test
  fun `test commit with excluded staged rename`() = with(gitCommitFixture.get()) {
    tac("a.java", "old content")
    tac("b.java")

    git("mv -f b.java c.java")
    overwrite("a.java", "new content")

    val changes = assertChangesWithRefresh {
      modified("a.java")
      rename("b.java", "c.java")
    }

    commit(listOf(changes[0]))

    assertChangesWithRefresh {
      rename("b.java", "c.java")
    }
    repo.assertCommitted {
      modified("a.java")
    }
  }

  @Test
  fun `test commit with excluded case rename`() = with(gitCommitFixture.get()) {
    tac("a.java", "old content")
    tac("b.java")

    git("mv -f b.java B.java")
    overwrite("a.java", "new content")

    val changes = assertChangesWithRefresh {
      modified("a.java")
      rename("b.java", "B.java")
    }

    commit(listOf(changes[0]))

    assertChangesWithRefresh {
      rename("b.java", "B.java")
    }
    repo.assertCommitted {
      modified("a.java")
    }
  }

  @Test
  fun `test commit staged rename`() = with(gitCommitFixture.get()) {
    tac("b.java")

    git("mv -f b.java c.java")

    val changes = assertChangesWithRefresh {
      rename("b.java", "c.java")
    }

    commit(changes)

    assertNoChanges()
    repo.assertCommitted {
      rename("b.java", "c.java")
    }
  }

  @Test
  fun `test commit case rename`() = with(gitCommitFixture.get()) {
    generateCaseRename("a.java", "A.java")

    val changes = assertChangesWithRefresh {
      rename("a.java", "A.java")
    }
    commit(changes)
    assertNoChanges()
    repo.assertCommitted {
      rename("a.java", "A.java")
    }
  }

  @Test
  fun `test commit unstaged case rename - case ignored on case insensitive system`() = with(gitCommitFixture.get()) {
    IoTestUtil.assumeCaseInsensitiveFS(projectNioRoot)

    tac("a.java", "old content")
    rm("a.java")
    touch("A.java", "new content")

    val changes = assertChangesWithRefresh {
      modified("a.java")
    }
    commit(changes)
    assertNoChanges()
    repo.assertCommitted {
      modified("a.java")
    }
  }

  @Test
  fun `test commit wrongly staged case rename - case ignored on case insensitive system`() = with(gitCommitFixture.get()) {
    IoTestUtil.assumeCaseInsensitiveFS(projectNioRoot)

    tac("a.java", "old content")
    rm("a.java")
    touch("A.java", "new content")
    git("add -A a.java A.java")

    val changes = assertChangesWithRefresh {
      modified("a.java")
    }
    commit(changes)
    assertNoChanges()
    repo.assertCommitted {
      modified("a.java")
    }
  }

  @Test
  fun `test commit case rename + one staged file`() = with(gitCommitFixture.get()) {
    generateCaseRename("a.java", "A.java")
    touch("s.java")
    git("add s.java")

    val changes = assertChangesWithRefresh {
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

  @Test
  fun `test commit case rename with another staged rename`() = with(gitCommitFixture.get()) {
    tac("a.java")
    tac("b.java")

    git("mv -f a.java c.java")
    git("mv -f b.java B.java")

    val changes = assertChangesWithRefresh {
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

  @Test
  fun `test commit case rename + one unstaged file`() = with(gitCommitFixture.get()) {
    tac("m.java")
    generateCaseRename("a.java", "A.java")
    echo("m.java", "unstaged")

    val changes = assertChangesWithRefresh {
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

  @Test
  fun `test commit case rename & don't commit one unstaged file`() = with(gitCommitFixture.get()) {
    tac("m.java")
    generateCaseRename("a.java", "A.java")
    echo("m.java", "unstaged")

    val changes = assertChangesWithRefresh {
      rename("a.java", "A.java")
      modified("m.java")
    }

    commit(listOf(changes[0]))

    assertChangesWithRefresh {
      modified("m.java")
    }
    repo.assertCommitted {
      rename("a.java", "A.java")
    }
  }

  @Test
  fun `test commit case rename & don't commit one staged file`() = with(gitCommitFixture.get()) {
    `assume version where git reset returns 0 exit code on success `()

    tac("s.java")
    generateCaseRename("a.java", "A.java")
    echo("s.java", "staged")
    git("add s.java")

    val changes = assertChangesWithRefresh {
      rename("a.java", "A.java")
      modified("s.java")
    }

    commit(listOf(changes[0]))

    repo.assertCommitted {
      rename("a.java", "A.java")
    }
    assertChangesWithRefresh {
      modified("s.java")
    }
    repo.assertStagedChanges {
      modified("s.java")
    }
  }

  @Test
  fun `test commit case rename & don't commit one staged case rename`() = with(gitCommitFixture.get()) {
    `assume version where git reset returns 0 exit code on success `()

    tac("s.java")
    generateCaseRename("a.java", "A.java")
    git("mv s.java S.java")

    val changes = assertChangesWithRefresh {
      rename("a.java", "A.java")
      rename("s.java", "S.java")
    }

    commit(listOf(changes[0]))

    repo.assertCommitted {
      rename("a.java", "A.java")
    }
    assertChangesWithRefresh {
      rename("s.java", "S.java")
    }
    repo.assertStagedChanges {
      rename("s.java", "S.java")
    }
  }

  @Test
  fun `test commit case rename & don't commit one staged simple rename, then rename should remain staged`() = with(gitCommitFixture.get()) {
    `assume version where git reset returns 0 exit code on success `()

    echo("before.txt", "some\ncontent\nere")
    addCommit("created before.txt")
    generateCaseRename("a.java", "A.java")
    git("mv before.txt after.txt")

    val changes = assertChangesWithRefresh {
      rename("a.java", "A.java")
      rename("before.txt", "after.txt")
    }

    commit(listOf(changes[0]))

    repo.assertCommitted {
      rename("a.java", "A.java")
    }
    assertChangesWithRefresh {
      rename("before.txt", "after.txt")
    }
    repo.assertStagedChanges {
      rename("before.txt", "after.txt")
    }
  }

  @Test
  fun `test commit case rename + one unstaged file & don't commit one staged file`() = with(gitCommitFixture.get()) {
    `assume version where git reset returns 0 exit code on success `()

    tac("s.java")
    tac("m.java")
    generateCaseRename("a.java", "A.java")
    echo("s.java", "staged")
    echo("m.java", "unstaged")
    git("add s.java m.java")

    val changes = assertChangesWithRefresh {
      rename("a.java", "A.java")
      modified("s.java")
      modified("m.java")
    }

    commit(listOf(changes[0], changes[2]))

    repo.assertCommitted {
      rename("a.java", "A.java")
      modified("m.java")
    }
    assertChangesWithRefresh {
      modified("s.java")
    }
    repo.assertStagedChanges {
      modified("s.java")
    }
  }

  @Test
  fun `test commit case rename & don't commit a file which is both staged and unstaged, should reset and restore unstaged`() = with(gitCommitFixture.get()) {
    `assume version where git reset returns 0 exit code on success `()

    tac("c.java", "initial")
    generateCaseRename("a.java", "A.java")
    overwrite("c.java", STAGED_CONTENT)
    git("add c.java")
    overwrite("c.java", UNSTAGED_CONTENT)

    val changes = assertChangesWithRefresh {
      rename("a.java", "A.java")
      modified("c.java")
    }

    commit(listOf(changes[0]))

    repo.assertCommitted {
      rename("a.java", "A.java")
    }
    assertChangesWithRefresh {
      modified("c.java")
    }
    repo.assertStagedChanges {
      modified("c.java")
    }

    `verify test commit case rename & don't commit a file which is both staged and unstaged, should reset and restore unstaged`()
  }

  @RegistryKey("git.commit.staged.saver.use.index.info", "true")
  @Test
  fun `test commit case rename & don't commit a file which is both staged and unstaged, should reset and restore both staged and unstaged`() = with(gitCommitFixture.get()) {
    `assume version where git reset returns 0 exit code on success `()

    tac("c.java", "initial")
    generateCaseRename("a.java", "A.java")
    overwrite("c.java", STAGED_CONTENT)
    git("add c.java")
    overwrite("c.java", UNSTAGED_CONTENT)

    val changes = assertChangesWithRefresh {
      rename("a.java", "A.java")
      modified("c.java")
    }

    commit(listOf(changes[0]))

    repo.assertCommitted {
      rename("a.java", "A.java")
    }
    assertChangesWithRefresh {
      modified("c.java")
    }
    repo.assertStagedChanges {
      modified("c.java")
    }

    assertEquals(STAGED_CONTENT, git("show :c.java"))
    assertEquals(UNSTAGED_CONTENT, FileUtil.loadFile(File(projectPath, "c.java")))
  }

  @Test
  fun `test commit case rename with additional non-staged changes should commit everything`() = with(gitCommitFixture.get()) {
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

    val changes = assertChangesWithRefresh {
      rename("a.java", "A.java")
    }

    commit(changes)

    repo.assertCommitted {
      rename("a.java", "A.java")
    }
    assertNoChanges()
    assertEquals(initialContent + additionalContent, git("show HEAD:A.java"))
  }

  @Test
  fun `test commit explicit rename`() = with(gitCommitFixture.get()) {
    `assume version where git reset returns 0 exit code on success `()

    tac("a.before", "before content")

    rm("a.before")
    touch("a.after", "after content")
    git("add a.after")


    val changes = assertChangesWithRefresh {
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

  @Test
  fun `test commit explicit rename with issue links`() = with(gitCommitFixture.get()) {
    `assume version where git reset returns 0 exit code on success `()

    tac("a.before", "before content")

    rm("a.before")
    touch("a.after", "after content")
    git("add a.after")

    val changes = assertChangesWithRefresh {
      deleted("a.before")
      added("a.after")
    }

    val originalMessage = """
      IDEA-1234 message
      
      related to KTIJ-1234
    """.trimIndent()

    val expectedMessageForRenameCommit = """
      ${myMovementProvider.getCommitMessage(originalMessage)}
      
      IDEA-1234
      KTIJ-1234
    """.trimIndent()

    val navigationConfiguration = IssueNavigationConfiguration.getInstance(project)
    val oldLinks = navigationConfiguration.links

    try {
      navigationConfiguration.links = listOf(
        IssueNavigationLink("[A-Z]+\\-\\d+", "https://youtrack.jetbrains.com/issue/\$0")
      )

      commit(changes, originalMessage)
      assertNoChanges()
    }
    finally {
      navigationConfiguration.links = oldLinks
    }

    assertMessage(repo.message("HEAD"), originalMessage)
    assertMessage(repo.message("HEAD~1"), expectedMessageForRenameCommit)

    repo.assertCommitted(1) {
      modified("a.after")
    }
    repo.assertCommitted(2) {
      rename("a.before", "a.after")
    }
  }

  @Test
  fun `test commit explicit rename + one unstaged file & don't commit one staged file`() = with(gitCommitFixture.get()) {
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

    val changes = assertChangesWithRefresh {
      deleted("a.before")
      added("a.after")
      modified("m.java")
      modified("s.java")
    }

    commit(listOf(changes[0], changes[1], changes[2]))

    assertChangesWithRefresh {
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

  @Test
  fun `test commit rename with conflicting staged rename`() = with(gitCommitFixture.get()) {
    `assume version where git reset returns 0 exit code on success `()

    tac("a.txt", "file content")

    rm("a.txt")
    touch("b.txt", "file content")
    touch("c.txt", "file content")
    git("add a.txt")
    git("add b.txt")

    val changes = assertChangesWithRefresh {
      rename("a.txt", "b.txt")
    }

    git("add c.txt")
    git("rm b.txt --cached")
    assertChangesWithRefresh {
      rename("a.txt", "c.txt")
    }

    commit(changes)

    assertChangesWithRefresh {
      added("c.txt")
    }

    assertMessage("comment", repo.message("HEAD"))

    repo.assertCommitted {
      rename("a.txt", "b.txt")
    }
  }

  @Test
  fun `test commit with excluded added-deleted file`() = with(gitCommitFixture.get()) {
    `assume version where git reset returns 0 exit code on success `()

    tac("a.txt", "file content")
    overwrite("a.txt", "new content")

    touch("b.txt", "file content")
    git("add b.txt")
    rm("b.txt")

    val changes = assertChangesWithRefresh {
      modified("a.txt")
    }

    commit(changes)

    assertNoChanges()
    assertMessage("comment", repo.message("HEAD"))
    repo.assertCommitted {
      modified("a.txt")
    }
  }

  @Test
  fun `test commit with excluded deleted-added file`() = with(gitCommitFixture.get()) {
    `assume version where git reset returns 0 exit code on success `()

    tac("a.txt", "file content")
    tac("b.txt", "file content")

    overwrite("a.txt", "new content")

    rm("b.txt")
    git("add -A b.txt")
    touch("b.txt", "new content")

    val changes = assertChangesWithRefresh {
      modified("a.txt")
      deleted("b.txt")
    }

    commit(listOf(changes[0]))

    assertChangesWithRefresh {
      deleted("b.txt")
    }
    assertMessage("comment", repo.message("HEAD"))
    repo.assertCommitted {
      modified("a.txt")
    }
  }

  @Test
  fun `test commit with excluded added-deleted and added files`() = with(gitCommitFixture.get()) {
    `assume version where git reset returns 0 exit code on success `()

    tac("a.txt", "file content")

    overwrite("a.txt", "new content")

    touch("b.txt", "new content")
    touch("c.txt", "new content")
    git("add -A b.txt c.txt")
    rm("b.txt")

    val changes = assertChangesWithRefresh {
      modified("a.txt")
      added("c.txt")
    }

    commit(listOf(changes[0]))

    `verify test commit with excluded added-deleted and added files`()
  }

  @Test
  fun `test commit with deleted-added file`() = with(gitCommitFixture.get()) {
    `assume version where git reset returns 0 exit code on success `()

    tac("a.txt", "file content")
    tac("b.txt", "file content")

    overwrite("a.txt", "new content")

    rm("b.txt")
    git("add -A b.txt")
    touch("b.txt", "new content")

    val changes = assertChangesWithRefresh {
      modified("a.txt")
      deleted("b.txt")
    }

    commit(changes)

    assertNoChanges()
    assertMessage("comment", repo.message("HEAD"))

    // bad case, but committing "deletion" seems logical (as it is shown in commit dialog)
    repo.assertCommitted {
      modified("a.txt")
      deleted("b.txt")
    }
  }

  @Test
  fun `test commit during unresolved merge conflict`() = with(gitCommitFixture.get()) {
    `assume version where git reset returns 0 exit code on success `()

    createFileStructure(projectRoot, "a.txt")
    addCommit("created some file structure")

    git("branch feature")

    val file = projectNioRoot.resolve("a.txt")
    assertTrue("File doesn't exist!", file.exists())
    overwrite(file, "my content")
    addCommit("modified in master")

    checkout("feature")
    overwrite(file, "brother content")
    addCommit("modified in feature")

    checkout("master")
    git("merge feature", true) // ignoring non-zero exit-code reporting about conflicts

    updateChangeListManager()
    val changes = changeListManager.allChanges
    assertTrue(!changes.isEmpty())

    val exceptions = vcs.checkinEnvironment!!.commit(ArrayList(changes), "comment")
    assertTrue(exceptions!!.isNotEmpty())

    assertMessage("modified in master", repo.message("HEAD"))
  }

  @Test
  fun `test commit gitignored file`() = with(gitCommitFixture.get()) {
    `assume version where git reset returns 0 exit code on success `()

    tac(".gitignore", "ignore*")

    touch("file.txt", "file1 content")
    touch("ignore1.txt", "ignore1 content")
    touch("ignore2.txt", "ignore2 content")
    git("add -f -- ignore1.txt ignore2.txt file.txt")

    val changes1 = assertChangesWithRefresh {
      added("file.txt")
      added("ignore1.txt")
      added("ignore2.txt")
    }

    commit(changes1)

    assertNoChanges()
    assertMessage("comment", repo.message("HEAD"))
    repo.assertCommitted {
      added("file.txt")
      added("ignore1.txt")
      added("ignore2.txt")
    }

    overwrite("file.txt", "new file content")
    overwrite("ignore1.txt", "new file1 content")
    overwrite("ignore2.txt", "new file2 content")

    val changes2 = assertChangesWithRefresh {
      modified("file.txt")
      modified("ignore1.txt")
      modified("ignore2.txt")
    }

    commit(changes2)

    assertNoChanges()
    assertMessage("comment", repo.message("HEAD"))
    repo.assertCommitted {
      modified("file.txt")
      modified("ignore1.txt")
      modified("ignore2.txt")
    }
  }

  @Test
  fun `test commit gitignored directory`() = with(gitCommitFixture.get()) {
    `assume version where git reset returns 0 exit code on success `()

    tac(".gitignore", "ignore/")

    touch("file.txt", "file1 content")
    touch("ignore/ignore1.txt", "ignore1 content")
    touch("ignore/ignore2.txt", "ignore2 content")
    git("add -f -- ignore/ignore1.txt ignore/ignore2.txt file.txt")

    val changes1 = assertChangesWithRefresh {
      added("file.txt")
      added("ignore/ignore1.txt")
      added("ignore/ignore2.txt")
    }

    commit(changes1)

    assertNoChanges()
    assertMessage("comment", repo.message("HEAD"))
    repo.assertCommitted {
      added("file.txt")
      added("ignore/ignore1.txt")
      added("ignore/ignore2.txt")
    }

    overwrite("file.txt", "new file content")
    overwrite("ignore/ignore1.txt", "new file1 content")
    overwrite("ignore/ignore2.txt", "new file2 content")

    val changes2 = assertChangesWithRefresh {
      modified("file.txt")
      modified("ignore/ignore1.txt")
      modified("ignore/ignore2.txt")
    }

    commit(changes2)

    assertNoChanges()
    assertMessage("comment", repo.message("HEAD"))
    repo.assertCommitted {
      modified("file.txt")
      modified("ignore/ignore1.txt")
      modified("ignore/ignore2.txt")
    }
  }

  @Test
  fun `test excluded from commit gitignored file`() = with(gitCommitFixture.get()) {
    `assume version where git reset returns 0 exit code on success `()

    tac(".gitignore", "ignore*")

    touch("file.txt", "file1 content")
    touch("ignore1.txt", "ignore1 content")
    git("add -f -- ignore1.txt file.txt")

    val changes1 = assertChangesWithRefresh {
      added("file.txt")
      added("ignore1.txt")
    }

    commit(changes1)

    assertNoChanges()
    assertMessage("comment", repo.message("HEAD"))
    repo.assertCommitted {
      added("file.txt")
      added("ignore1.txt")
    }

    overwrite("ignore1.txt", "new file content")
    touch("ignore2.txt", "ignore2 content")
    git("add -f -- ignore1.txt ignore2.txt")

    val changes2 = assertChangesWithRefresh {
      modified("ignore1.txt")
      added("ignore2.txt")
    }

    commit(listOf(changes2[0]))

    assertChangesWithRefresh {
      added("ignore2.txt")
    }
    assertMessage("comment", repo.message("HEAD"))
    repo.assertCommitted {
      modified("ignore1.txt")
    }
  }

  @Test
  fun `test file to directory renames`() = with(gitCommitFixture.get()) {
    tac("a_path", "file content 1")
    tac("b_path", "file content 2")

    rm("a_path")
    rm("b_path")
    touch("a_path/file1.txt", "file content 1")
    touch("b_path/file2.txt", "file content 2")
    git("add -A .")

    val changes = assertChangesWithRefresh {
      rename("a_path", "a_path/file1.txt")
      rename("b_path", "b_path/file2.txt")
    }

    commit(listOf(changes[0]))

    assertChangesWithRefresh {
      rename("b_path", "b_path/file2.txt")
    }
    repo.assertStagedChanges {
      rename("b_path", "b_path/file2.txt")
    }
    assertMessage("comment", repo.message("HEAD"))
    repo.assertCommitted {
      rename("a_path", "a_path/file1.txt")
    }
  }

  @Test
  fun `test directory to file renames`() = with(gitCommitFixture.get()) {
    tac("a_path/file1.txt", "file content 1")
    tac("b_path/file2.txt", "file content 2")

    rm("a_path/file1.txt")
    rm("b_path/file2.txt")
    rm("a_path")
    rm("b_path")
    touch("a_path", "file content 1")
    touch("b_path", "file content 2")
    git("add -A .")

    val changes = assertChangesWithRefresh {
      rename("a_path/file1.txt", "a_path")
      rename("b_path/file2.txt", "b_path")
    }

    commit(listOf(changes[0]))

    assertChangesWithRefresh {
      rename("b_path/file2.txt", "b_path")
    }
    repo.assertStagedChanges {
      rename("b_path/file2.txt", "b_path")
    }
    assertMessage("comment", repo.message("HEAD"))
    repo.assertCommitted {
      rename("a_path/file1.txt", "a_path")
    }
  }

  @Test
  fun `test gpg failure notification action`() = with(gitCommitFixture.get()) {
    tac("a.java", "old content")

    git.config(repo, "--local", GitConfigUtil.GPG_COMMIT_SIGN, "true")
    git.config(repo, "--local", GitConfigUtil.GPG_COMMIT_SIGN_KEY, "NON_EXISTENT_KEY")

    overwrite("a.java", "new content")
    val changes = assertChangesWithRefresh {
      modified("a.java")
    }

    val exceptions = tryCommit(changes).orEmpty()
    assertEquals(exceptions.toString(), 1, exceptions.size)

    val notification = VcsNotifier.standardNotification().createNotification("notification content", NotificationType.ERROR)
    val actions = (exceptions.single() as? CommitExceptionWithActions)?.getActions(notification) ?: emptyList()
    assertEquals(actions.toString(), 1, actions.size)

    val action = actions.single()
    assertEquals(GitBundle.message("gpg.error.see.documentation.link.text"), action.templateText)
  }

  @Test
  fun `test commit with excluded staged submodule`() = with(gitCommitFixture.get()) {
    tac("a", "old content")

    overwrite("a", "new content")

    val submodule = repo.createSubRepository("submodule", addToGitIgnore = false)
    val submodulePath = submodule.root.toNioPath().asEelPath().toString()
    git("add submodule $submodulePath")

    repo.assertStagedChanges {
      added("submodule")
    }

    val changes = assertChangesWithRefresh {
      modified("a")
      added("submodule")
    }

    commit(listOf(changes[0]))

    repo.assertCommitted {
      modified("a")
    }
    repo.assertStagedChanges {
      added("submodule")
    }
  }

  private fun `assume version where git reset returns 0 exit code on success `() = with(gitCommitFixture.get()) {
    Assumptions.assumeTrue(vcs.version.isLaterOrEqual(GitVersion(1, 8, 2, 0)),
                           "Not testing: git reset returns 1 and fails the commit process in ${vcs.version}")
  }

  private fun generateCaseRename(from: String, to: String) = with(gitCommitFixture.get()) {
    tac(from)
    git("mv -f $from $to")
  }

  companion object {
    protected const val STAGED_CONTENT = "staged"
    protected const val UNSTAGED_CONTENT = "unstaged"
  }
}

private class MyExplicitMovementProvider : GitCheckinExplicitMovementProvider() {
  override fun isEnabled(project: Project): Boolean = true

  override fun getDescription(): String = "explicit movement in tests"

  override fun getCommitMessage(originalCommitMessage: String): String = description

  override fun collectExplicitMovements(
    project: Project,
    beforePaths: MutableList<FilePath>,
    afterPaths: MutableList<FilePath>,
  ): MutableCollection<Movement> {
    val beforeMap = beforePaths.filter { it.name.endsWith(".before") }.associateBy { it.name.removeSuffix(".before") }

    val afterMap = afterPaths.filter { it.name.endsWith(".after") }.associateBy { it.name.removeSuffix(".after") }

    val movedChanges = ArrayList<Movement>()
    for (key in (beforeMap.keys + afterMap.keys)) {
      val beforePath = beforeMap[key] ?: continue
      val afterPath = afterMap[key] ?: continue
      movedChanges.add(Movement(beforePath, afterPath))
    }

    return movedChanges
  }
}

internal class GitCommitTest {

  @TestApplicationWithEel(osesMayNotHaveRemoteEels = [OS.WINDOWS, OS.LINUX, OS.MAC])
  @ParameterizedClass
  @DockerTest(image = "alpine/git", mandatory = false)
  @Nested
  inner class GitCommitWithResetAddTest(eelHolder: EelHolder) : GitCommitTestBase(eelHolder, gitCommitFixture(false)) {
    override fun `verify test commit case rename & don't commit a file which is both staged and unstaged, should reset and restore unstaged`() = with(gitCommitFixture.get()) {
      // this is intentional data loss: it is a rare case, while restoring both staged and unstaged part is not so easy,
      // so we are not doing it, at least until IDEA supports Git index
      // (which will mean that users will be able to produce such situation intentionally with a help of IDE).
      assertEquals(UNSTAGED_CONTENT, git("show :c.java"))
      assertEquals(UNSTAGED_CONTENT, FileUtil.loadFile(File(projectPath, "c.java")))
    }

    override fun `verify test commit with excluded added-deleted and added files`() = with(gitCommitFixture.get()) {
      assertChangesWithRefresh {
        added("c.txt")
      }
      repo.assertStagedChanges {
        added("c.txt")
      }
      assertMessage("comment", repo.message("HEAD"))
      repo.assertCommitted {
        modified("a.txt")
      }
    }
  }

  @TestApplicationWithEel(osesMayNotHaveRemoteEels = [OS.WINDOWS, OS.LINUX, OS.MAC])
  @ParameterizedClass
  @DockerTest(image = "alpine/git", mandatory = false)
  @Nested
  inner class GitCommitWithIndexInfoTest(eelHolder: EelHolder) : GitCommitTestBase(eelHolder, gitCommitFixture(true)) {
    override fun `verify test commit case rename & don't commit a file which is both staged and unstaged, should reset and restore unstaged`() = with(gitCommitFixture.get()) {
      assertEquals(STAGED_CONTENT, git("show :c.java"))
      assertEquals(UNSTAGED_CONTENT, FileUtil.loadFile(File(projectPath, "c.java")))
    }

    override fun `verify test commit with excluded added-deleted and added files`() = with(gitCommitFixture.get()) {
      assertChangesWithRefresh {
        added("c.txt")
      }
      repo.assertStagedChanges {
        added("b.txt")
        added("c.txt")
      }
      assertMessage("comment", repo.message("HEAD"))
      repo.assertCommitted {
        modified("a.txt")
      }
    }
  }
}

internal interface GitCommitContext : GitSingleRepoContext {
  val myMovementProvider: GitCheckinExplicitMovementProvider
}

private fun gitCommitFixture(useIndexInfoStagedChangesSaver: Boolean) =
  projectFixture(openAfterCreation = true).let { projectFixture ->
    projectFixture
      .vcsPlatformFixture()
      .gitPlatformFixture(projectFixture,
                          defaultSaveChangesPolicy = GitSaveChangesPolicy.SHELVE,
                          hasRemoteGitOperation = false) {
        isCommitRenamesSeparately = true
      }
      .gitSingleRepoFixture(makeInitialCommit = true)
      .gitCommitFixture(useIndexInfoStagedChangesSaver = useIndexInfoStagedChangesSaver)
  }

private fun TestFixture<GitSingleRepoContext>.gitCommitFixture(useIndexInfoStagedChangesSaver: Boolean): TestFixture<GitCommitContext> = testFixture {
  val singleRepoContext = init()
  val movementProvider = extensionPointFixture(GitCheckinExplicitMovementProvider.EP_NAME) {
    MyExplicitMovementProvider()
  }.init()
  registryKeyFixture("git.commit.staged.saver.use.index.info") { setValue(useIndexInfoStagedChangesSaver) }.init()
  val result = object : GitCommitContext, GitSingleRepoContext by singleRepoContext {
    override val myMovementProvider: MyExplicitMovementProvider = movementProvider
  }
  initialized(result) {}
}
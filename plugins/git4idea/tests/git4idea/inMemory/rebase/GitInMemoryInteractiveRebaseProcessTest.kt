// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.inMemory.rebase

import com.intellij.vcs.log.data.VcsLogData
import git4idea.GitDisposable
import git4idea.inMemory.MergeConflictException
import git4idea.inMemory.rebase.log.GitInMemoryOperationTest
import git4idea.log.createLogDataIn
import git4idea.log.refreshAndWait
import git4idea.rebase.interactive.convertToModel
import git4idea.rebase.interactive.getEntriesUsingLog
import git4idea.rebase.log.GitCommitEditingOperationResult
import git4idea.test.assertCommitted
import git4idea.test.assertLatestHistory
import git4idea.test.commit
import git4idea.test.getHash
import git4idea.test.last
import kotlinx.coroutines.CoroutineScope
import org.junit.jupiter.api.assertThrows

internal class GitInMemoryInteractiveRebaseProcessTest : GitInMemoryOperationTest() {
  private lateinit var testCs: CoroutineScope
  private lateinit var logData: VcsLogData

  override fun setUp() {
    super.setUp()
    testCs = GitDisposable.getInstance(project).coroutineScope
    logData = createLogDataIn(testCs, repo, logProvider)
  }

  fun `test pick and reword operations`() {
    file("a").create("content a").add()
    val firstCommit = commitDetails(commit("Add a"))
    file("b").create("content b").addCommit("Add b")
    file("c").create("content c").addCommit("Add c")

    logData.refreshAndWait(repo, true)
    refresh()
    updateChangeListManager()

    val entries = getEntriesUsingLog(repo, firstCommit, logData)
    val model = convertToModel(entries)

    val newMessageForSecond = "Reworded second commit"
    model.reword(1, newMessageForSecond)
    val newMessageForThird = "Reworded third commit"
    model.reword(2, newMessageForThird)

    val validationResult = GitInMemoryRebaseData.createValidatedRebaseData(model, firstCommit, entries.last().commitDetails.id) as GitInMemoryRebaseData.Companion.ValidationResult.Valid

    GitInMemoryInteractiveRebaseProcess(objectRepo, validationResult.rebaseData).run() as GitCommitEditingOperationResult.Complete

    repo.assertLatestHistory(newMessageForThird, newMessageForSecond, "Add a")

    with(repo) {
      assertCommitted(1) { added("c", "content c") }
      assertCommitted(2) { added("b", "content b") }
      assertCommitted(3) { added("a", "content a") }
    }
  }

  fun `test reorder commits without conflicts`() {
    file("a").create("content a").add()
    val firstCommit = commitDetails(commit("Add a"))
    file("b").create("content b").addCommit("Add b")
    file("c").create("content c").addCommit("Add c")

    logData.refreshAndWait(repo, true)
    refresh()
    updateChangeListManager()

    val entries = getEntriesUsingLog(repo, firstCommit, logData)
    val model = convertToModel(entries)

    model.exchangeIndices(1, 2) // Move "Add b" down, making it after "Add c"

    val validationResult = GitInMemoryRebaseData.createValidatedRebaseData(model, firstCommit, entries.last().commitDetails.id) as GitInMemoryRebaseData.Companion.ValidationResult.Valid
    GitInMemoryInteractiveRebaseProcess(objectRepo, validationResult.rebaseData).run() as GitCommitEditingOperationResult.Complete

    repo.assertLatestHistory(
      "Add b",
      "Add c",
      "Add a"
    )

    with(repo) {
      assertCommitted(1) { added("b") }
      assertCommitted(2) { added("c") }
    }
  }

  fun `test drop and fixup operations`() {
    file("a").create("content a").add()
    val firstCommit = commitDetails(commit("Add a"))
    file("b").create("content b").addCommit("Add b")
    file("c").create("content c").addCommit("Add c")
    file("d").create("content d").addCommit("Add d")

    logData.refreshAndWait(repo, true)
    refresh()
    updateChangeListManager()

    val entries = getEntriesUsingLog(repo, firstCommit, logData)
    val model = convertToModel(entries)

    model.drop(listOf(1)) // Drop "Add b"
    model.unite(listOf(2, 3)) // Fixup "Add d" into "Add c"
    model.reword(2, "Combined commit")

    val validationResult = GitInMemoryRebaseData.createValidatedRebaseData(model, firstCommit, entries.last().commitDetails.id) as GitInMemoryRebaseData.Companion.ValidationResult.Valid

    GitInMemoryInteractiveRebaseProcess(objectRepo, validationResult.rebaseData).run() as GitCommitEditingOperationResult.Complete

    repo.assertLatestHistory(
      "Combined commit",
      "Add a"
    )

    with(repo) {
      assertCommitted(1) {
        added("c")
        added("d")
      }
      assertCommitted(2) { added("a") }
    }
  }

  fun `test rebase with merge conflicts throws exception`() {
    file("conflict").create("original content").add()
    val firstCommit = commitDetails(commit("Add conflict file"))

    file("conflict").write("A content").addCommit("Modify A")
    file("conflict").write("B content").addCommit("Modify B")

    logData.refreshAndWait(repo, true)
    refresh()
    updateChangeListManager()

    val entries = getEntriesUsingLog(repo, firstCommit, logData)
    val model = convertToModel(entries)

    model.exchangeIndices(1, 2)

    val validationResult = GitInMemoryRebaseData.createValidatedRebaseData(model, firstCommit, entries.last().commitDetails.id) as GitInMemoryRebaseData.Companion.ValidationResult.Valid

    assertThrows<MergeConflictException> {
      GitInMemoryInteractiveRebaseProcess(objectRepo, validationResult.rebaseData).run()
    }
  }

  fun `test tree merge without conflicts`() {
    file("src/main/java/App.java").create("public class App {}").add()
    file("src/test/java/AppTest.java").create("public class AppTest {}").add()
    val firstCommit = commitDetails(commit("Add initial project structure"))

    file("src/main/resources/config.properties").create("app.name=MyApp").add()
    file("docs/README.md").create("# Documentation").add()
    commit("Add config and docs")

    file("src/test/java/AppTest.java").delete().add()
    file("src/main/java/Utils.java").create("public class Utils {}").add()
    file("build.gradle").create("plugins { id 'java' }").add()
    commit("Remove test, add Utils and build file")

    file("src/main/java/service/UserService.java").create("public class UserService {}").add()
    file("src/main/java/model/User.java").create("public class User {}").add()
    file("config/application.yml").create("server:\n  port: 8080").add()
    commit("Add service layer and config")

    logData.refreshAndWait(repo, true)
    refresh()
    updateChangeListManager()

    val entries = getEntriesUsingLog(repo, firstCommit, logData)
    val model = convertToModel(entries)

    // Original: initial -> config+docs -> remove+add -> service
    // New: initial -> service -> remove+add -> config+docs
    model.exchangeIndices(1, 3)
    model.exchangeIndices(1, 2)

    val validationResult = GitInMemoryRebaseData.createValidatedRebaseData(model, firstCommit, entries.last().commitDetails.id) as GitInMemoryRebaseData.Companion.ValidationResult.Valid

    GitInMemoryInteractiveRebaseProcess(objectRepo, validationResult.rebaseData).run() as GitCommitEditingOperationResult.Complete

    repo.assertLatestHistory(
      "Add config and docs",
      "Remove test, add Utils and build file",
      "Add service layer and config",
      "Add initial project structure"
    )

    with(repo) {
      assertCommitted(1) {
        added("src/main/resources/config.properties", "app.name=MyApp")
        added("docs/README.md", "# Documentation")
      }
      assertCommitted(2) {
        deleted("src/test/java/AppTest.java")
        added("src/main/java/Utils.java", "public class Utils {}")
        added("build.gradle", "plugins { id 'java' }")
      }
      assertCommitted(3) {
        added("src/main/java/service/UserService.java", "public class UserService {}")
        added("src/main/java/model/User.java", "public class User {}")
        added("config/application.yml", "server:\n  port: 8080")
      }
      assertCommitted(4) {
        added("src/main/java/App.java", "public class App {}")
        added("src/test/java/AppTest.java", "public class AppTest {}")
      }
    }
  }

  fun `test rebase with non-conflicting changes to same file`() {
    val originalContent = """
    line1=value1
    line2=value2
    line3=value3
    line4=value4
    line5=value5
    """.trimIndent()

    file("config.txt").create(originalContent).add()
    val firstCommit = commitDetails(commit("Add config file"))

    val modifiedContent = """
    line1=modified_value1
    line2=value2
    line3=value3
    line4=value4
    line5=value5
    """.trimIndent()

    file("config.txt").write(modifiedContent).addCommit("Modify line1")

    val finalContent = """
    line1=modified_value1
    line2=value2
    line3=value3
    line4=value4
    line5=modified_value5
    """.trimIndent()
    file("config.txt").write(finalContent).addCommit("Modify line5")

    logData.refreshAndWait(repo, true)
    refresh()
    updateChangeListManager()

    val entries = getEntriesUsingLog(repo, firstCommit, logData)
    val model = convertToModel(entries)

    model.exchangeIndices(1, 2)

    val validationResult = GitInMemoryRebaseData.createValidatedRebaseData(model, firstCommit, entries.last().commitDetails.id) as GitInMemoryRebaseData.Companion.ValidationResult.Valid

    GitInMemoryInteractiveRebaseProcess(objectRepo, validationResult.rebaseData).run() as GitCommitEditingOperationResult.Complete

    repo.assertLatestHistory(
      "Modify line1",
      "Modify line5",
      "Add config file"
    )

    val newModifiedContent = """
    line1=value1
    line2=value2
    line3=value3
    line4=value4
    line5=modified_value5
    """.trimIndent()

    with(repo) {
      assertCommitted(1) { modified("config.txt", newModifiedContent, finalContent) }
      assertCommitted(2) { modified("config.txt", originalContent, newModifiedContent) }
      assertCommitted(3) { added("config.txt", originalContent) }
    }
  }

  fun `test reorder commits with reword operations`() {
    file("feature1.txt").create("feature 1 content").add()
    val firstCommit = commitDetails(commit("Add feature 1"))

    file("feature2.txt").create("feature 2 content").addCommit("Add feature 2")
    file("feature3.txt").create("feature 3 content").addCommit("Add feature 3")
    file("feature4.txt").create("feature 4 content").addCommit("Add feature 4")

    logData.refreshAndWait(repo, true)
    refresh()
    updateChangeListManager()

    val entries = getEntriesUsingLog(repo, firstCommit, logData)
    val model = convertToModel(entries)

    // Original: feature1 -> feature2 -> feature3 -> feature4
    // New: feature3 -> feature1 -> feature4 -> feature2
    model.exchangeIndices(1, 3)
    model.exchangeIndices(1, 0)

    val newMessageForFeature1 = "Enhanced feature 1 implementation"
    val newMessageForFeature3 = "Refactored feature 3 with better design"
    val newMessageForFeature4 = "Optimized feature 4 performance"

    model.reword(2, newMessageForFeature4)
    model.reword(1, newMessageForFeature1)
    model.reword(0, newMessageForFeature3)

    val validationResult = GitInMemoryRebaseData.createValidatedRebaseData(model, firstCommit, entries.last().commitDetails.id) as GitInMemoryRebaseData.Companion.ValidationResult.Valid

    GitInMemoryInteractiveRebaseProcess(objectRepo, validationResult.rebaseData).run() as GitCommitEditingOperationResult.Complete

    repo.assertLatestHistory(
      "Add feature 2",
      newMessageForFeature4,
      newMessageForFeature1,
      newMessageForFeature3,
    )

    with(repo) {
      assertCommitted(1) { added("feature2.txt") }
      assertCommitted(2) { added("feature4.txt") }
      assertCommitted(3) { added("feature1.txt") }
      assertCommitted(4) { added("feature3.txt") }
    }
  }

  fun `test dropping commit with directory creation`() {
    file("dir/file1.txt").create().add()
    val firstCommit = commitDetails(commit("Create file1.txt in dir"))
    file("dir/file2.txt").create().add()
    commit("Create file2.txt in dir")

    logData.refreshAndWait(repo, true)
    refresh()
    updateChangeListManager()

    val entries = getEntriesUsingLog(repo, firstCommit, logData)
    val model = convertToModel(entries)
    model.drop(listOf(0))

    val validationResult = GitInMemoryRebaseData.createValidatedRebaseData(model, firstCommit, entries.last().commitDetails.id) as GitInMemoryRebaseData.Companion.ValidationResult.Valid
    GitInMemoryInteractiveRebaseProcess(objectRepo, validationResult.rebaseData).run() as GitCommitEditingOperationResult.Complete

    repo.assertLatestHistory(
      "Create file2.txt in dir",
      "initial"
    )

    with(repo) {
      assertCommitted(1) { added("dir/file2.txt") }
    }
  }

  fun `test rebase with rename and content change preserves both operations correctly`() {
    val originalContent = "content_v1"
    file("old/file.txt").create(originalContent).add()
    val firstCommit = commitDetails(commit("Add file"))

    val modifiedContent = "content_v2"
    file("old/file.txt").write(modifiedContent).addCommit("Modify file")

    file("old/file.txt").delete().add()
    file("new/file.txt").create(modifiedContent).addCommit("Move file")

    val finalContent = "content_v3"
    file("new/file.txt").write(finalContent).addCommit("Update file")

    logData.refreshAndWait(repo, true)
    refresh()
    updateChangeListManager()

    val entries = getEntriesUsingLog(repo, firstCommit, logData)
    val model = convertToModel(entries)

    // Original: Add -> Modify -> Move -> Update
    // New: Add -> Move -> Modify -> Update
    model.exchangeIndices(1, 2)

    val validationResult = GitInMemoryRebaseData.createValidatedRebaseData(model, firstCommit, entries.last().commitDetails.id) as GitInMemoryRebaseData.Companion.ValidationResult.Valid

    GitInMemoryInteractiveRebaseProcess(objectRepo, validationResult.rebaseData).run() as GitCommitEditingOperationResult.Complete

    repo.assertLatestHistory(
      "Update file",
      "Modify file",
      "Move file",
      "Add file"
    )

    with(repo) {
      assertCommitted(1) { modified("new/file.txt", modifiedContent, finalContent) }
      assertCommitted(2) { modified("new/file.txt", originalContent, modifiedContent) }
      assertCommitted(3) { rename("old/file.txt", "new/file.txt") }
      assertCommitted(4) { added("old/file.txt", originalContent) }
    }
  }

  fun `test no changes rebase preserves commit hashes`() {
    file("a").create("content a").add()
    val firstCommit = commitDetails(commit("Add a"))
    file("b").create("content b").addCommit("Add b")
    val lastCommitHashBefore = repo.last()

    logData.refreshAndWait(repo, true)
    refresh()
    updateChangeListManager()

    val entries = getEntriesUsingLog(repo, firstCommit, logData)
    val model = convertToModel(entries)

    // Don't modify the model - just pick all commits in the same order
    val validationResult = GitInMemoryRebaseData.createValidatedRebaseData(model, firstCommit, entries.last().commitDetails.id) as GitInMemoryRebaseData.Companion.ValidationResult.Valid

    GitInMemoryInteractiveRebaseProcess(objectRepo, validationResult.rebaseData).run() as GitCommitEditingOperationResult.Complete

    val lastCommitHashAfter = repo.last()
    assertEquals("Commit hash should remain unchanged when rebase does nothing", lastCommitHashBefore, lastCommitHashAfter)
  }

  fun `test rebase preserves hashes for unchanged segment`() {
    file("a").create("content a").add()
    val firstCommit = commitDetails(commit("Add a"))
    file("b").create("content b").add()
    val secondCommit = commitDetails(commit("Add b"))
    file("c").create("content c").addCommit("Add c")
    file("d").create("content d").addCommit("Add d")

    val commitAHashBefore = firstCommit.id.asString()
    val commitBHashBefore = secondCommit.id.asString()

    logData.refreshAndWait(repo, true)
    refresh()
    updateChangeListManager()

    val entries = getEntriesUsingLog(repo, firstCommit, logData)
    val model = convertToModel(entries)

    model.reword(2, "Modified: Add c")
    model.exchangeIndices(2, 3)

    val validationResult = GitInMemoryRebaseData.createValidatedRebaseData(model, firstCommit, entries.last().commitDetails.id) as GitInMemoryRebaseData.Companion.ValidationResult.Valid

    GitInMemoryInteractiveRebaseProcess(objectRepo, validationResult.rebaseData).run() as GitCommitEditingOperationResult.Complete

    repo.assertLatestHistory(
      "Modified: Add c",
      "Add d",
      "Add b",
      "Add a",
    )

    val commitAHashAfter = getHash(3)
    val commitBHashAfter = getHash(2)

    assertEquals("Commit hashes should not change for commits that were not modified", listOf(commitAHashBefore, commitBHashBefore),
                 listOf(commitAHashAfter, commitBHashAfter))
  }

  fun `test drop commit that adds two files updates working tree and index`() {
    file("a.txt").create("content a").add()
    val firstCommit = commitDetails(commit("Add a"))

    file("a.txt").write("local modified content")

    file("b.txt").create("content b").add()
    file("c.txt").create("content c").add()
    commit("Add b, c")

    file("a.txt").add()

    logData.refreshAndWait(repo, true)
    refresh()
    updateChangeListManager()

    val entries = getEntriesUsingLog(repo, firstCommit, logData)
    val model = convertToModel(entries)

    model.drop(listOf(1)) // drop "Add b, c" commit

    val validationResult = GitInMemoryRebaseData.createValidatedRebaseData(model, firstCommit, entries.last().commitDetails.id) as GitInMemoryRebaseData.Companion.ValidationResult.Valid

    GitInMemoryInteractiveRebaseProcess(objectRepo, validationResult.rebaseData).run() as GitCommitEditingOperationResult.Complete

    repo.assertLatestHistory(
      "Add a",
      "initial"
    )

    refresh()
    updateChangeListManager()

    assertEquals("local modified content", file("a.txt").read())
    file("b.txt").assertNotExists()
    file("c.txt").assertNotExists()
  }

  fun `test drop commit fails when local changes would be overwritten`() {
    file("a.txt").create("content a").add()
    val firstCommit = commitDetails(commit("Add a"))
    file("b.txt").create("content b").addCommit("Add b")

    file("b.txt").write("local modified content")

    logData.refreshAndWait(repo, true)
    refresh()
    updateChangeListManager()

    val entries = getEntriesUsingLog(repo, firstCommit, logData)
    val model = convertToModel(entries)

    model.drop(listOf(1))

    val validationResult = GitInMemoryRebaseData.createValidatedRebaseData(model, firstCommit, entries.last().commitDetails.id) as GitInMemoryRebaseData.Companion.ValidationResult.Valid

    val lastCommitHashBefore = repo.last()
    val result = GitInMemoryInteractiveRebaseProcess(objectRepo, validationResult.rebaseData).run()

    assertTrue(result is GitCommitEditingOperationResult.Incomplete)

    val lastCommitHashAfter = repo.last()
    assertEquals(lastCommitHashBefore, lastCommitHashAfter)
    file("b.txt").assertExists()
    assertEquals("local modified content", file("b.txt").read())
  }

  fun `test rebase initial commit`() {
    val initialCommit = commitDetails(last())
    file("a.txt").create("content a").addCommit("Add a")
    file("b.txt").create("content b").addCommit("Add b")

    logData.refreshAndWait(repo, true)
    refresh()
    updateChangeListManager()

    val entries = getEntriesUsingLog(repo, initialCommit, logData)
    val model = convertToModel(entries)

    model.exchangeIndices(0, 1)

    val validationResult = GitInMemoryRebaseData.createValidatedRebaseData(model, initialCommit, entries.last().commitDetails.id) as GitInMemoryRebaseData.Companion.ValidationResult.Valid

    GitInMemoryInteractiveRebaseProcess(objectRepo, validationResult.rebaseData).run() as GitCommitEditingOperationResult.Complete

    repo.assertLatestHistory(
      "Add b",
      "initial",
      "Add a"
    )

    with(repo) {
      assertCommitted(1) { added("b.txt") }
      assertCommitted(2) { added("initial.txt") }
      assertCommitted(3) { added("a.txt") }
    }
  }
}

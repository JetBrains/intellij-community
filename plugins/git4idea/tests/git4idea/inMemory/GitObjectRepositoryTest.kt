// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.inMemory

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.vcs.test.refresh
import com.intellij.vcs.test.updateChangeListManager
import git4idea.test.GitSingleRepoContext
import git4idea.test.gitSingleRepoContextFixture
import git4idea.test.git
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.EnumSource

import com.intellij.openapi.vcs.VcsException
import git4idea.commands.GitObjectType
import git4idea.config.GitConfigUtil
import git4idea.inMemory.objects.GitObject
import git4idea.inMemory.objects.Oid
import git4idea.repo.GitObjectFormat
import git4idea.test.gitAsBytes
import git4idea.test.tac

private const val SAMPLE_CONTENT = "Hello, World!"
private const val NAME_AND_EMAIL = "John Doe <john.doe@example.com>"

@TestApplication
@ParameterizedClass(name = "git object format = {0}")
@EnumSource(GitObjectFormat::class)
class GitObjectRepositoryTest(private val objectFormat: GitObjectFormat) {
  private val fixture = gitSingleRepoContextFixture(objectFormat = objectFormat)
  private val context: GitSingleRepoContext get() = fixture.get()
  private val testAuthor = GitObject.Commit.Author(NAME_AND_EMAIL, 1234567890, "+0000")

  @Test
  fun `test object caching works correctly`(): Unit = with(context) {
    val repository = GitObjectRepository(repo)

    val blob = repository.createBlob(SAMPLE_CONTENT.toByteArray())

    val cachedBlob = repository.findBlob(blob.oid)
    assertThat(cachedBlob).isNotNull()
    assertThat(cachedBlob).isEqualTo(blob)
  }

  @Test
  fun `test load commit from git with dependencies`(): Unit = with(context) {
    val commitHash = tac("test.txt", SAMPLE_CONTENT)

    val repository = GitObjectRepository(repo)
    val commit = repository.findCommit(Oid.fromHex(commitHash))

    assertPersisted(commit)

    assertThat(commit.message.toString(Charsets.UTF_8).trim()).describedAs("Commit message should match").isEqualTo("Touched test.txt")

    val tree = repository.findTree(commit.treeOid)
    assertPersisted(tree)

    assertThat(tree.entries.containsKey(GitObject.Tree.FileName("test.txt"))).describedAs("Tree should contain test.txt").isTrue()
    val treeEntry = tree.entries[GitObject.Tree.FileName("test.txt")]!!

    val blob = repository.findBlob(treeEntry.oid)

    assertPersisted(blob)
    assertThat(blob.body.toString(Charsets.UTF_8)).describedAs("Blob content should match").isEqualTo("Hello, World!")

    verifyObjectExistsInGit(blob, tree, commit)
  }

  @Test
  fun `test commitTree creates commit with correct metadata`(): Unit = with(context) {
    val repository = GitObjectRepository(repo)

    val blob = repository.createBlob(SAMPLE_CONTENT.toByteArray())

    val entries = createTreeEntries(blob)
    val tree = repository.createTree(entries)

    repository.persistObject(tree)

    val message = "Test commit message".toByteArray()
    val commitOid = repository.commitTree(tree.oid, emptyList(), message, testAuthor)
    val commit = repository.findCommit(commitOid)

    assertThat(commit.treeOid).describedAs("Commit should have correct tree OID").isEqualTo(tree.oid)
    assertThat(commit.author).describedAs("Commit should have correct author").isEqualTo(testAuthor)
    assertThat(commit.message.toString(Charsets.UTF_8)).describedAs("Commit should have correct message")
      .isEqualToIgnoringWhitespace("Test commit message")
    assertThat(commit.parentsOids.size).describedAs("Commit should have no parents").isEqualTo(0)
  }

  @Test
  fun `test persistence is idempotent`(): Unit = with(context) {
    val repository = GitObjectRepository(repo)

    val blob = repository.createBlob(SAMPLE_CONTENT.toByteArray())

    val entries = createTreeEntries(blob)
    val tree = repository.createTree(entries)

    val message = "Test commit message".toByteArray()
    val commitBody = GitObject.Commit.buildBody(testAuthor, testAuthor, emptyList(), tree.oid, message, null)
    val commit = repository.createCommit(
      commitBody,
      repository.fetchOid(GitObjectType.COMMIT, commitBody),
      testAuthor,
      testAuthor,
      emptyList(),
      tree.oid,
      message,
      null
    )

    assertNotPersisted(blob, tree, commit)

    repository.persistObject(blob)
    repository.persistObject(tree)
    repository.persistObject(commit)

    assertPersisted(blob, tree, commit)
    verifyObjectExistsInGit(blob, tree, commit)

    repository.persistObject(blob)
    repository.persistObject(tree)
    repository.persistObject(commit)

    assertPersisted(blob, tree, commit)
    verifyObjectExistsInGit(blob, tree, commit)
  }

  @Test
  fun `test persist in-memory object that already exists in git repository`(): Unit = with(context) {
    val commitHash = tac("test.txt", SAMPLE_CONTENT)

    val repository = GitObjectRepository(repo)

    val existingCommit = repository.findCommit(Oid.fromHex(commitHash))
    val existingTree = repository.findTree(existingCommit.treeOid)
    val existingBlobEntry = existingTree.entries[GitObject.Tree.FileName("test.txt")]!!
    val existingBlob = repository.findBlob(existingBlobEntry.oid)

    repository.clearCache()

    val duplicateBlob = repository.createBlob(SAMPLE_CONTENT.toByteArray())

    assertThat(duplicateBlob.oid).describedAs("In-memory blob should have same OID as existing blob").isEqualTo(existingBlob.oid)
    assertNotPersisted(duplicateBlob)

    verifyObjectExistsInGit(duplicateBlob)

    repository.persistObject(duplicateBlob)

    assertPersisted(duplicateBlob)

    verifyObjectExistsInGit(duplicateBlob)

    val duplicateTree = repository.createTree(existingTree.entries)
    assertThat(duplicateTree.oid).describedAs("In-memory tree should have same OID as existing tree").isEqualTo(existingTree.oid)
    assertThat(duplicateTree.persisted).describedAs("In-memory tree should not be marked as persisted").isFalse()

    repository.persistObject(duplicateTree)
    assertPersisted(duplicateTree)
    verifyObjectExistsInGit(duplicateTree)
  }

  @Test
  fun `test commitTree attempts to sign commit if GPG config is set and vice versa`(): Unit = with(context) {
    val repository = GitObjectRepository(repo)

    val content = SAMPLE_CONTENT.toByteArray()
    val blob = repository.createBlob(content)

    val entries = createTreeEntries(blob)
    val tree = repository.createTree(entries)

    repository.persistObject(tree)

    val message = "Test commit with GPG config".toByteArray()

    git.config(repo, "--local", GitConfigUtil.GPG_COMMIT_SIGN, "true")
    git.config(repo, "--local", GitConfigUtil.GPG_COMMIT_SIGN_KEY, "NON_EXISTENT_KEY")

    assertThatThrownBy {
      repository.commitTree(tree.oid, emptyList(), message, testAuthor)
    }.isInstanceOf(VcsException::class.java)

    git.config(repo, "--local", GitConfigUtil.GPG_COMMIT_SIGN, "false")

    // as config commit.gpgsign may be cached
    refresh()
    updateChangeListManager()

    repository.commitTree(tree.oid, emptyList(), message, testAuthor)
  }

  @Test
  fun `test tree sorts directory entries with trailing slash`(): Unit = with(context) {
    val repository = GitObjectRepository(repo)

    val blob = repository.createBlob(SAMPLE_CONTENT.toByteArray())

    // to ensure incorrect initial order
    val entries = sortedMapOf(
      compareBy { it.value },
      GitObject.Tree.FileName("dir") to GitObject.Tree.Entry(GitObject.Tree.FileMode.DIR, blob.oid),
      GitObject.Tree.FileName("dir-file.txt") to GitObject.Tree.Entry(GitObject.Tree.FileMode.REGULAR, blob.oid)
    )

    val newTree = repository.createTree(entries)
    repository.persistObject(newTree)

    verifyObjectExistsInGit(newTree)
  }

  // IJPL-200053
  @Test
  fun `test commitTree creates commit with empty message`(): Unit = with(context) {
    val repository = GitObjectRepository(repo)

    val blob = repository.createBlob(SAMPLE_CONTENT.toByteArray())

    val entries = createTreeEntries(blob)
    val tree = repository.createTree(entries)

    repository.persistObject(tree)

    val emptyMessage = byteArrayOf()
    val commitOid = repository.commitTree(tree.oid, emptyList(), emptyMessage, testAuthor)
    val commit = repository.findCommit(commitOid)

    assertThat(commit.message.toString(Charsets.UTF_8)).describedAs("Commit should have empty message").isEqualToIgnoringWhitespace("")
  }

  @Test
  fun `test object ids use repository object format length`(): Unit = with(context) {
    val commitHash = tac("test.txt", SAMPLE_CONTENT)

    val repository = GitObjectRepository(repo)
    val commit = repository.findCommit(Oid.fromHex(commitHash))
    val tree = repository.findTree(commit.treeOid)
    val blob = repository.findBlob(tree.entries[GitObject.Tree.FileName("test.txt")]!!.oid)

    assertThat(commit.oid.hex().length).isEqualTo(objectFormat.hexSize)
    assertThat(tree.oid.hex().length).isEqualTo(objectFormat.hexSize)
    assertThat(blob.oid.hex().length).isEqualTo(objectFormat.hexSize)

    val newBlob = repository.createBlob("new content".toByteArray())
    val newTree = repository.createTree(
      mapOf(GitObject.Tree.FileName("file.txt") to GitObject.Tree.Entry(GitObject.Tree.FileMode.REGULAR, newBlob.oid))
    )
    repository.persistObject(newTree)

    assertThat(newBlob.oid.hex().length).isEqualTo(objectFormat.hexSize)
    assertThat(newTree.oid.hex().length).isEqualTo(objectFormat.hexSize)

    val newCommitOid = repository.commitTree(newTree.oid,
                                             listOf(commit.oid),
                                             "new commit".toByteArray(),
                                             testAuthor)
    val newCommit = repository.findCommit(newCommitOid)

    assertThat(newCommit.oid.hex().length).isEqualTo(objectFormat.hexSize)
  }

  private fun createTreeEntries(blob: GitObject.Blob): Map<GitObject.Tree.FileName, GitObject.Tree.Entry> {
    return mapOf(GitObject.Tree.FileName("test.txt") to GitObject.Tree.Entry(GitObject.Tree.FileMode.REGULAR, blob.oid))
  }

  private fun assertPersisted(vararg objects: GitObject) =
    assertThat(objects.all { it.persisted }).describedAs("Objects should be marked as persisted").isTrue()

  private fun assertNotPersisted(vararg objects: GitObject) =
    assertThat(objects.none { it.persisted }).describedAs("Objects should not be marked as persisted").isTrue()

  private fun verifyObjectExistsInGit(vararg objects: GitObject): Unit = with(context) {
    for (obj in objects) {
      val typeOutput = git("cat-file -t ${obj.oid}")
      assertThat(typeOutput).describedAs("Object ${obj.oid} should have type ${obj.type.tag}").isEqualTo(obj.type.tag)
      val contentOutput = repo.gitAsBytes("cat-file $typeOutput ${obj.oid}")
      assertThat(obj.body).isEqualTo(contentOutput)
    }
  }
}
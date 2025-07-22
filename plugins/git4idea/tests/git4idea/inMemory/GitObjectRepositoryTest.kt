// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.inMemory

import com.intellij.openapi.vcs.VcsException
import git4idea.commands.GitObjectType
import git4idea.config.GitConfigUtil
import git4idea.inMemory.objects.GitObject
import git4idea.inMemory.objects.Oid
import git4idea.test.GitSingleRepoTest
import git4idea.test.commit
import git4idea.test.gitAsBytes
import git4idea.test.tac
import kotlin.jvm.java
import kotlin.test.assertContentEquals

class GitObjectRepositoryTest : GitSingleRepoTest() {
  private val SAMPLE_AUTHOR = GitObject.Commit.Author("John Doe <john.doe@example.com>", 1234567890, "+0000")
  private val SAMPLE_CONTENT = "Hello, World!"

  fun `test object caching works correctly`() {
    val repository = GitObjectRepository(repo)

    val blob = repository.createBlob(SAMPLE_CONTENT.toByteArray())

    val cachedBlob = repository.findObjectFromCache(blob.oid)
    assertNotNull(cachedBlob)
    assertEquals(blob, cachedBlob)
  }

  fun `test load commit from git with dependencies`() {
    val commitHash = tac("test.txt", SAMPLE_CONTENT)

    val repository = GitObjectRepository(repo)
    val commit = repository.findCommit(Oid.fromHex(commitHash))

    assertPersisted(commit)

    assertEquals("Commit message should match", "Touched test.txt",
                 commit.message.toString(Charsets.UTF_8).trim())

    val tree = repository.findTree(commit.treeOid)
    assertPersisted(tree)

    assertTrue("Tree should contain test.txt", tree.entries.containsKey(GitObject.Tree.FileName("test.txt")))
    val treeEntry = tree.entries[GitObject.Tree.FileName("test.txt")]!!

    val blob = repository.findBlob(treeEntry.oid)

    assertPersisted(blob)
    assertEquals("Blob content should match", "Hello, World!", blob.body.toString(Charsets.UTF_8))

    verifyObjectExistsInGit(blob, tree, commit)
  }

  fun `test commitTree creates commit with correct metadata`() {
    val repository = GitObjectRepository(repo)

    val blob = repository.createBlob(SAMPLE_CONTENT.toByteArray())

    val entries = createTreeEntries(blob)
    val tree = repository.createTree(entries)

    repository.persistObject(tree)

    val message = "Test commit message".toByteArray()
    val commitOid = repository.commitTree(tree.oid, emptyList(), message, SAMPLE_AUTHOR)
    val commit = repository.findCommit(commitOid)

    assertEquals("Commit should have correct tree OID", tree.oid, commit.treeOid)
    assertEquals("Commit should have correct author", SAMPLE_AUTHOR, commit.author)
    assertEquals("Commit should have correct message", "Test commit message", commit.message.toString(Charsets.UTF_8))
    assertEquals("Commit should have no parents", 0, commit.parentsOids.size)
  }

  fun `test persistence is idempotent`() {
    val repository = GitObjectRepository(repo)

    val blob = repository.createBlob(SAMPLE_CONTENT.toByteArray())

    val entries = createTreeEntries(blob)
    val tree = repository.createTree(entries)

    val message = "Test commit message".toByteArray()
    val commitBody = GitObject.Commit.buildBody(SAMPLE_AUTHOR, SAMPLE_AUTHOR, emptyList(), tree.oid, message, null)
    val commit = repository.createCommit(
      commitBody,
      repository.fetchOid(GitObjectType.COMMIT, commitBody),
      SAMPLE_AUTHOR,
      SAMPLE_AUTHOR,
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

  fun `test persist in-memory object that already exists in git repository`() {
    val commitHash = tac("test.txt", SAMPLE_CONTENT)

    val repository = GitObjectRepository(repo)

    val existingCommit = repository.findCommit(Oid.fromHex(commitHash))
    val existingTree = repository.findTree(existingCommit.treeOid)
    val existingBlobEntry = existingTree.entries[GitObject.Tree.FileName("test.txt")]!!
    val existingBlob = repository.findBlob(existingBlobEntry.oid)

    repository.clearCache()

    val duplicateBlob = repository.createBlob(SAMPLE_CONTENT.toByteArray())

    assertEquals("In-memory blob should have same OID as existing blob", existingBlob.oid, duplicateBlob.oid)
    assertNotPersisted(duplicateBlob)

    verifyObjectExistsInGit(duplicateBlob)

    repository.persistObject(duplicateBlob)

    assertPersisted(duplicateBlob)

    verifyObjectExistsInGit(duplicateBlob)

    val duplicateTree = repository.createTree(existingTree.entries)
    assertEquals("In-memory tree should have same OID as existing tree", existingTree.oid, duplicateTree.oid)
    assertFalse("In-memory tree should not be marked as persisted", duplicateTree.persisted)

    repository.persistObject(duplicateTree)
    assertPersisted(duplicateTree)
    verifyObjectExistsInGit(duplicateTree)
  }

  fun `test commitTree attempts to sign commit if GPG config is set and vice versa`() {
    val repository = GitObjectRepository(repo)

    val content = SAMPLE_CONTENT.toByteArray()
    val blob = repository.createBlob(content)

    val entries = createTreeEntries(blob)
    val tree = repository.createTree(entries)

    repository.persistObject(tree)

    val message = "Test commit with GPG config".toByteArray()

    git.config(repo, "--local", GitConfigUtil.GPG_COMMIT_SIGN, "true")
    git.config(repo, "--local", GitConfigUtil.GPG_COMMIT_SIGN_KEY, "NON_EXISTENT_KEY")

    assertThrows(VcsException::class.java) {
      repository.commitTree(tree.oid, emptyList(), message, SAMPLE_AUTHOR)
    }

    git.config(repo, "--local", GitConfigUtil.GPG_COMMIT_SIGN, "false")

    // as config commit.gpgsign may be cached
    refresh()
    updateChangeListManager()

    repository.commitTree(tree.oid, emptyList(), message, SAMPLE_AUTHOR)
  }

  fun `test tree sorts directory entries with trailing slash`() {
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

  private fun createTreeEntries(blob: GitObject.Blob): Map<GitObject.Tree.FileName, GitObject.Tree.Entry> {
    return mapOf(GitObject.Tree.FileName("test.txt") to GitObject.Tree.Entry(GitObject.Tree.FileMode.REGULAR, blob.oid))
  }

  private fun assertPersisted(vararg objects: GitObject) =
    assertTrue("Objects should be marked as persisted", objects.all { it.persisted })

  private fun assertNotPersisted(vararg objects: GitObject) =
    assertTrue("Objects should not be marked as persisted", objects.none { it.persisted })

  private fun verifyObjectExistsInGit(vararg objects: GitObject) {
    for (obj in objects) {
      val typeOutput = git("cat-file -t ${obj.oid}")
      assertEquals("Object ${obj.oid} should have type ${obj.type.tag}", obj.type.tag, typeOutput)
      val contentOutput = repo.gitAsBytes("cat-file ${typeOutput} ${obj.oid}")
      assertContentEquals(contentOutput, obj.body)
    }
  }
}
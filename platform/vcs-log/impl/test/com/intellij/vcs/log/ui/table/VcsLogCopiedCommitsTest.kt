// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.table

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.vcs.impl.shared.log.VcsCopiedCommit
import com.intellij.vcs.log.CommitId
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.VcsFullCommitDetails
import com.intellij.vcs.log.VcsLogCommitSelection
import com.intellij.vcs.log.VcsLogCommitStorageIndex
import com.intellij.vcs.log.data.LoadingDetails
import com.intellij.vcs.log.impl.HashImpl
import com.intellij.vcs.log.impl.VcsCommitMetadataImpl
import com.intellij.vcs.log.util.VcsUserUtil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import java.util.function.Consumer

class VcsLogCopiedCommitsTest {
  private val root = mock<VirtualFile> { on { path } doReturn "/repo" }
  private val otherRoot = mock<VirtualFile> { on { path } doReturn "/repo/sub" }

  @Test
  fun `commits keep the selection order and the metadata`() {
    val selection = FakeSelection(
      commits = listOf(CommitId(HashImpl.build(FIRST_HASH), root), CommitId(HashImpl.build(SECOND_HASH), otherRoot)),
      cachedMetadata = listOf(
        metadata(FIRST_HASH, root, subject = "Fix the bug", author = "Ann", commitTime = 1_000),
        metadata(SECOND_HASH, otherRoot, subject = "Add the test", author = "Bob", commitTime = 2_000),
      ),
    )

    assertEquals(
      listOf(
        VcsCopiedCommit(FIRST_HASH, "/repo", subject = "Fix the bug", author = "Ann", commitTimeMs = 1_000),
        VcsCopiedCommit(SECOND_HASH, "/repo/sub", subject = "Add the test", author = "Bob", commitTimeMs = 2_000),
      ),
      selection.toCopiedCommits(limit = 10),
    )
  }

  @Test
  fun `a commit that is still loading keeps only its hash and root`() {
    val loading = mock<VcsCommitMetadata>(extraInterfaces = arrayOf(LoadingDetails::class))
    val selection = FakeSelection(
      commits = listOf(CommitId(HashImpl.build(FIRST_HASH), root)),
      cachedMetadata = listOf(loading),
    )

    assertEquals(listOf(VcsCopiedCommit(FIRST_HASH, "/repo")), selection.toCopiedCommits(limit = 10))
  }

  @Test
  fun `a commit without metadata keeps only its hash and root`() {
    val selection = FakeSelection(commits = listOf(CommitId(HashImpl.build(FIRST_HASH), root)), cachedMetadata = emptyList())

    assertEquals(listOf(VcsCopiedCommit(FIRST_HASH, "/repo")), selection.toCopiedCommits(limit = 10))
  }

  @Test
  fun `a commit time that is not set is dropped`() {
    val selection = FakeSelection(
      commits = listOf(CommitId(HashImpl.build(FIRST_HASH), root)),
      cachedMetadata = listOf(metadata(FIRST_HASH, root, subject = "Fix the bug", author = "Ann", commitTime = -1)),
    )

    assertEquals(
      listOf(VcsCopiedCommit(FIRST_HASH, "/repo", subject = "Fix the bug", author = "Ann")),
      selection.toCopiedCommits(limit = 10),
    )
  }

  @Test
  fun `the limit caps the copied commits`() {
    val selection = FakeSelection(
      commits = listOf(CommitId(HashImpl.build(FIRST_HASH), root), CommitId(HashImpl.build(SECOND_HASH), root)),
      cachedMetadata = emptyList(),
    )

    assertEquals(listOf(VcsCopiedCommit(FIRST_HASH, "/repo")), selection.toCopiedCommits(limit = 1))
  }

  @Test
  fun `a zero limit does not read the selection`() {
    val selection = mock<VcsLogCommitSelection>()

    assertEquals(emptyList<VcsCopiedCommit>(), selection.toCopiedCommits(limit = 0))
    verifyNoInteractions(selection)
  }

  @Test
  fun `a negative limit fails before reading the selection`() {
    val selection = mock<VcsLogCommitSelection>()

    assertThrows<IllegalArgumentException> { selection.toCopiedCommits(limit = -1) }
    verifyNoInteractions(selection)
  }

  @Test
  fun `the limit avoids reading later commits and metadata`() {
    val commit = CommitId(HashImpl.build(FIRST_HASH), root)
    val details = metadata(FIRST_HASH, root, subject = "Fix the bug", author = "Ann", commitTime = 1_000)
    val selection = FakeSelection(commits = firstElementOnly(commit), cachedMetadata = firstElementOnly(details))

    assertEquals(
      listOf(VcsCopiedCommit(FIRST_HASH, "/repo", subject = "Fix the bug", author = "Ann", commitTimeMs = 1_000)),
      selection.toCopiedCommits(limit = 1),
    )
  }

  @Test
  fun `an empty selection copies no commits`() {
    assertEquals(emptyList<VcsCopiedCommit>(), FakeSelection(emptyList(), emptyList()).toCopiedCommits(limit = 10))
  }

  private fun metadata(hash: String, root: VirtualFile, subject: String, author: String, commitTime: Long): VcsCommitMetadata {
    val user = VcsUserUtil.createUser(author, "$author@example.com")
    return VcsCommitMetadataImpl(HashImpl.build(hash), emptyList(), commitTime, root, subject, user, subject, user, commitTime)
  }

  private fun <T> firstElementOnly(first: T): List<T> {
    return object : AbstractList<T>() {
      override val size: Int = 2

      override fun get(index: Int): T {
        check(index == 0) { "The copy must not read an element beyond its limit" }
        return first
      }
    }
  }

  private class FakeSelection(
    override val commits: List<CommitId>,
    override val cachedMetadata: List<VcsCommitMetadata>,
  ) : VcsLogCommitSelection {
    override val rows: IntArray get() = IntArray(commits.size) { it }
    override val ids: List<VcsLogCommitStorageIndex> get() = commits.indices.toList()
    override val cachedFullDetails: List<VcsFullCommitDetails> get() = emptyList()
    override fun requestFullDetails(consumer: Consumer<in List<VcsFullCommitDetails>>) = Unit
  }

  private companion object {
    const val FIRST_HASH = "1111111111111111111111111111111111111111"
    const val SECOND_HASH = "2222222222222222222222222222222222222222"
  }
}

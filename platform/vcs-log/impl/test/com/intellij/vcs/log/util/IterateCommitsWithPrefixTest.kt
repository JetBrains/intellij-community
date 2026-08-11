// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.util

import com.intellij.mock.MockVirtualFile
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.CommitId
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.VcsLogCommitStorageIndex
import com.intellij.vcs.log.VcsRef
import com.intellij.vcs.log.data.VcsLogStorage
import com.intellij.vcs.log.impl.HashImpl
import com.intellij.vcs.log.impl.TestVcsLogProvider
import org.junit.Test
import java.util.function.Predicate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IterateCommitsWithPrefixTest {
  @Test
  fun `does not check by prefix when all providers have requested full hash length`() {
    val requestedHash = "a".repeat(40)
    val (firstRoot, firstProvider) = rootToProvider("firstRoot", 40)
    val (secondRoot, secondProvider) = rootToProvider("secondRoot", 40)
    val commitInFirst = CommitId(HashImpl.build("b".repeat(40)), firstRoot)
    val commitInSecond = CommitId(HashImpl.build("c".repeat(40)), secondRoot)
    val storage = TestStorage(listOf(commitInFirst, commitInSecond))

    storage.iterateCommitsWithPrefix(requestedHash,
                                     mapOf(firstRoot to firstProvider,
                                           secondRoot to secondProvider)) {
      error("Unexpected commit resolved: $it")
    }

    assertFalse(storage.iterateCommitsCalled)
  }

  @Test
  fun `does not check by prefix after exact match in some root`() {
    val requestedHash = "a".repeat(40)
    val (sha1Root, sha1Provider) = rootToProvider("sha1", 40)
    val (sha256Root, sha256Provider) = rootToProvider("sha256", 64)
    val exactCommit = CommitId(HashImpl.build(requestedHash), sha1Root)
    val anotherCommit = CommitId(HashImpl.build("b".repeat(64)), sha256Root)
    val storage = TestStorage(listOf(exactCommit, anotherCommit))

    val found = mutableListOf<CommitId>()
    storage.iterateCommitsWithPrefix(
      requestedHash,
      mapOf(sha1Root to sha1Provider, sha256Root to sha256Provider),
    ) { commitId ->
      found += commitId
      true
    }

    assertEquals(listOf(exactCommit), found)
    assertFalse(storage.iterateCommitsCalled)
  }

  @Test
  fun `checks by prefix when exact match is not found and not all providers have requested full hash length`() {
    val requestedHash = "a".repeat(40)
    val (sha1Root, sha1Provider) = rootToProvider("sha1", 40)
    val (sha256Root, sha256Provider) = rootToProvider("sha256", 64)
    val prefixCommit = CommitId(HashImpl.build(requestedHash + "b".repeat(24)), sha256Root)
    val storage = TestStorage(listOf(prefixCommit))

    val found = mutableListOf<CommitId>()
    storage.iterateCommitsWithPrefix(
      requestedHash,
      mapOf(sha1Root to sha1Provider, sha256Root to sha256Provider),
    ) { commitId ->
      found += commitId
      true
    }

    assertEquals(listOf(prefixCommit), found)
    assertTrue(storage.iterateCommitsCalled)
  }

  private fun rootToProvider(name: String, fullHashLength: Int): Pair<VirtualFile, TestVcsLogProvider> {
    return MockVirtualFile(name) to TestVcsLogProvider(fullHashLength)
  }

  private class TestStorage(private val commits: List<CommitId>) : VcsLogStorage {
    var iterateCommitsCalled = false

    override fun getCommitIndex(hash: Hash, root: VirtualFile): VcsLogCommitStorageIndex {
      return commits.indexOf(CommitId(hash, root))
    }

    override fun getCommitId(commitIndex: VcsLogCommitStorageIndex): CommitId? {
      return commits.getOrNull(commitIndex)
    }

    override fun iterateCommits(consumer: Predicate<in CommitId>) {
      iterateCommitsCalled = true
      for (commit in commits) {
        if (!consumer.test(commit)) return
      }
    }

    override fun containsCommit(id: CommitId): Boolean {
      return id in commits
    }

    override fun getRefIndex(ref: VcsRef): Int = throw UnsupportedOperationException()

    override fun getVcsRef(refIndex: Int): VcsRef = throw UnsupportedOperationException()

    override fun flush() {
    }
  }
}

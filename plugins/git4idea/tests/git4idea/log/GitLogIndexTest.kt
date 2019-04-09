// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.log

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.Executor.append
import com.intellij.openapi.vcs.Executor.touch
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Consumer
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.data.VcsLogStorage
import com.intellij.vcs.log.data.index.*
import com.intellij.vcs.log.impl.HashImpl
import com.intellij.vcs.log.impl.VcsUserImpl
import com.intellij.vcs.log.util.VcsUserUtil
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject
import git4idea.test.*
import junit.framework.TestCase

class GitLogIndexTest : GitSingleRepoTest() {
  private val defaultUser = VcsUserImpl(USER_NAME, USER_EMAIL)

  private lateinit var disposable: Disposable
  private lateinit var index: VcsLogPersistentIndex
  private val dataGetter: IndexDataGetter
    get() = index.dataGetter!!
  private val storage: VcsLogStorage
    get() = dataGetter.logStorage

  override fun setUp() {
    super.setUp()

    disposable = Disposable { }
    Disposer.register(myProject, disposable)

    index = setUpIndex(myProject, repo.root, logProvider, disposable)
  }

  override fun tearDown() {
    Disposer.dispose(disposable)
    super.tearDown()
  }

  fun `test indexed`() {
    val file = "file.txt"
    tac(file)
    for (i in 0 until 5) {
      repo.appendAndCommit(file, "new content ${i}\n")
    }

    val commits = indexAll()
    TestCase.assertTrue(index.isIndexed(repo.root))
    for (commit in commits) {
      TestCase.assertTrue(index.isIndexed(commit))
    }
  }

  fun `test forward index`() {
    val commitHash = tac("file.txt")

    indexAll()

    val expectedMetadata = logProvider.readMetadata(repo.root, listOf(commitHash)).first()
    val actualMetadata = IndexedDetails(dataGetter, storage, getCommitIndex(commitHash), 0L)

    TestCase.assertEquals(expectedMetadata.presentation(), actualMetadata.presentation())
  }

  fun `test text filter`() {
    val file = "file.txt"
    touch(file, "content")
    repo.addCommit("some message")

    append(file, "more content")
    val keyword = "keyword"
    val expected = setOf(getCommitIndex(repo.addCommit("message with $keyword")))

    append(file, "even more content")
    repo.addCommit("some other message")

    indexAll()

    val actual = dataGetter.filter(listOf(VcsLogFilterObject.fromPattern(keyword)))

    TestCase.assertEquals(expected, actual)
  }

  fun `test author filter`() {
    val file = "file.txt"
    touch(file, "content")
    repo.addCommit("some message")

    val author = VcsUserImpl("Name", "name@server.com")
    val expected = setOf(getCommitIndex(makeCommit(author, file)))

    append(file, "even more content")
    repo.addCommit("some other message")

    indexAll()

    val actual = dataGetter.filter(listOf(VcsLogFilterObject.fromUser(author, setOf(author, defaultUser))))

    TestCase.assertEquals(expected, actual)
  }

  private fun getCommitIndex(hash: String): Int {
    return storage.getCommitIndex(HashImpl.build(hash), repo.root)
  }

  private fun indexAll(): Set<Int> {
    val commits = readCommits(repo.root)
    index.index(repo.root, commits)
    return commits
  }

  private fun readCommits(root: VirtualFile): Set<Int> {
    val result = mutableSetOf<Int>()
    logProvider.readAllHashes(root, Consumer { commit ->
      result.add(storage.getCommitIndex(commit.id, root))
    })
    return result
  }

  private fun VcsCommitMetadata.presentation(): String {
    return "${id.asString()} (${root.name})\n" +
           "${parents.joinToString(", ") { it.asString() }}\n" +
           "${VcsUserUtil.toExactString(author)} (${VcsUserUtil.toExactString(committer)})\n" +
           "$authorTime ($commitTime)\n" +
           "$subject\n$fullMessage"
  }
}
// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.history

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.Executor.touch
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.util.containers.getIfSingle
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.impl.HashImpl
import com.intellij.vcs.log.impl.VcsUserImpl
import com.intellij.vcsUtil.VcsUtil
import git4idea.log.createLogData
import git4idea.log.refreshAndWait
import git4idea.test.GitSingleRepoTest
import git4idea.test.makeCommit
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class GitHistoryTraverserImplTest : GitSingleRepoTest() {
  private lateinit var logData: VcsLogData

  private val traverser: GitHistoryTraverser
    get() = GitHistoryTraverserImpl(repo.project, logData)

  override fun setUp() {
    super.setUp()
    logData = createLogData(repo, logProvider, testRootDisposable)
  }

  fun `test files from commits made by user`() {
    val file = "file.txt"
    touch(file, "content")

    val authorCommits = mutableSetOf<Hash>()
    val author = VcsUserImpl("Name", "name@server.com")
    val anotherUser = VcsUserImpl("Another Name", "another.name@server.com")
    repeat(5) {
      authorCommits.add(HashImpl.build(makeCommit(author, file)))
      makeCommit(anotherUser, file)
    }

    logData.refreshAndWait(repo, withIndex = true)

    traverser.withIndex(listOf(repo.root), testRootDisposable) { indexedRoots ->
      val indexedRoot = indexedRoots.single()
      val authorCommitIds = indexedRoot.filterCommits(GitHistoryTraverser.IndexedRoot.TraverseCommitsFilter.Author(author))
      traverse(indexedRoot.root) { (commitId, _) ->
        if (commitId in authorCommitIds) {
          loadFullDetailsLater(commitId) { details ->
            assertTrue(details.id in authorCommits)
            assertTrue(ChangesUtil.getFiles(details.changes.stream()).getIfSingle()!!.name.startsWith("file"))
          }
        }
        true
      }
    }
  }

  fun `test bfs early termination`() {
    val file = "file.txt"
    touch(file, "content")

    val anotherFile = "anotherFile.txt"
    touch(anotherFile, "content")

    makeCommit(file)
    makeCommit(file)
    makeCommit(file)
    makeCommit(anotherFile)
    makeCommit(file)
    makeCommit(file)
    makeCommit(anotherFile)
    makeCommit(file)

    logData.refreshAndWait(repo, withIndex = true)

    val maxCommitsHistoryCount = 5
    var fileInCommitCount = 0
    var commitsCounter = 0
    traverser.traverse(repo.root) { (commitId, _) ->
      loadFullDetailsLater(commitId) { details ->
        if (ChangesUtil.getFiles(details.changes.stream()).getIfSingle()!!.name.startsWith("file")) {
          fileInCommitCount++
        }
      }
      ++commitsCounter != maxCommitsHistoryCount
    }

    assertEquals(3, fileInCommitCount)
  }

  fun `test last commit by user with file`() {
    val file = "file.txt"
    val filePath = VcsUtil.getFilePath(touch(file, "content"))

    val anotherFile = "anotherFile.txt"
    touch(anotherFile, "content")

    val author = VcsUserImpl("Name", "name@server.com")
    val anotherUser = VcsUserImpl("Another Name", "another.name@server.com")

    makeCommit(author, file)
    makeCommit(anotherUser, file)
    makeCommit(author, file)
    makeCommit(author, anotherFile)
    val lastCommitByUserWithFile = makeCommit(author, file)
    makeCommit(anotherUser, file)
    makeCommit(anotherUser, anotherFile)
    makeCommit(anotherUser, file)

    logData.refreshAndWait(repo, withIndex = true)
    traverser.withIndex(listOf(repo.root), testRootDisposable) { indexedRoots ->
      val indexedRoot = indexedRoots.single()
      val authorCommitIds = indexedRoot.filterCommits(GitHistoryTraverser.IndexedRoot.TraverseCommitsFilter.Author(author))
      val fileCommits = indexedRoot.filterCommits(GitHistoryTraverser.IndexedRoot.TraverseCommitsFilter.File(filePath))

      val authorCommitsWithFile = authorCommitIds.intersect(fileCommits)
      val actualLastCommitByUserWithFile = authorCommitsWithFile.map { indexedRoot.loadTimedCommit(it) }.maxBy { it.timestamp }!!
      val expectedCommitByUserWithFile = GitHistoryUtils.collectCommitsMetadata(project, repo.root, lastCommitByUserWithFile)!!.single()
      assertEquals(expectedCommitByUserWithFile.commitTime, actualLastCommitByUserWithFile.timestamp)
    }
  }

  fun `test withIndex waiting for index`() {
    val file = "file.txt"
    touch(file, "content")
    repeat(10) {
      makeCommit(file)
    }

    logData.refreshAndWait(repo, withIndex = false)
    val indexingWaiter = CompletableFuture<GitHistoryTraverser.IndexedRoot>()
    val indexWaiterDisposable = Disposable {}
    var blockExecutedCount = 0
    traverser.withIndex(listOf(repo.root), indexWaiterDisposable) { indexedRoots ->
      val indexedRoot = indexedRoots.single()
      blockExecutedCount++
      indexingWaiter.complete(indexedRoot)
    }
    try {
      val indexedRoot = indexingWaiter.get(5, TimeUnit.SECONDS)
      assertEquals(repo.root, indexedRoot.root)
      assertTrue(logData.index.isIndexed(indexedRoot.root))
      assertEquals(1, blockExecutedCount)
    }
    catch (e: Exception) {
      fail(e.message)
    }
    finally {
      Disposer.dispose(indexWaiterDisposable)
    }
  }
}
// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.history

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.Executor.touch
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.impl.HashImpl
import com.intellij.vcs.log.impl.VcsUserImpl
import com.intellij.vcs.log.util.VcsLogUtil
import com.intellij.vcsUtil.VcsUtil
import git4idea.GitCommit
import git4idea.log.createLogDataIn
import git4idea.log.refreshAndWait
import git4idea.test.GitSingleRepoTest
import git4idea.test.makeCommit
import kotlinx.coroutines.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.random.nextInt

class GitHistoryTraverserImplTest : GitSingleRepoTest() {
  private lateinit var testCs: CoroutineScope
  private lateinit var logData: VcsLogData

  private val traverser: GitHistoryTraverser
    get() = GitHistoryTraverserImpl(repo.project, logData, testRootDisposable)

  override fun setUp() {
    super.setUp()
    VcsLogData.getIndexingRegistryValue().setValue(true)
    @Suppress("RAW_SCOPE_CREATION")
    testCs = CoroutineScope(SupervisorJob())
    logData = createLogDataIn(testCs, repo, logProvider)
  }

  override fun tearDown() {
    try {
      runBlocking {
        testCs.coroutineContext.job.cancelAndJoin()
      }
      VcsLogData.getIndexingRegistryValue().resetToDefault()
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
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

    logData.refreshAndWait(repo, waitIndexFinishing = true)

    traverser.addIndexingListener(listOf(repo.root), testRootDisposable) { indexedRoots ->
      val indexedRoot = indexedRoots.single()
      val authorCommitIds = indexedRoot.filterCommits(GitHistoryTraverser.IndexedRoot.TraverseCommitsFilter.Author(author))
      traverser.traverse(indexedRoot.root) { (commitId, _) ->
        if (commitId in authorCommitIds) {
          loadFullDetailsLater(commitId) { details ->
            assertTrue(details.id in authorCommits)
            assertTrue(areOnlyFilesInCommit(details, setOf("file.txt")))
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

    logData.refreshAndWait(repo, waitIndexFinishing = true)

    val maxCommitsHistoryCount = 5
    var fileInCommitCount = 0
    var commitsCounter = 0
    traverser.traverse(repo.root) { (commitId, _) ->
      loadFullDetailsLater(commitId) { details ->
        if (areOnlyFilesInCommit(details, setOf("file.txt"))) {
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

    logData.refreshAndWait(repo, waitIndexFinishing = true)
    traverser.addIndexingListener(listOf(repo.root), testRootDisposable) { indexedRoots ->
      val indexedRoot = indexedRoots.single()
      val authorCommitIds = indexedRoot.filterCommits(GitHistoryTraverser.IndexedRoot.TraverseCommitsFilter.Author(author))
      val fileCommits = indexedRoot.filterCommits(GitHistoryTraverser.IndexedRoot.TraverseCommitsFilter.File(filePath))

      val authorCommitsWithFile = authorCommitIds.intersect(fileCommits)
      val actualLastCommitByUserWithFile = authorCommitsWithFile.map { indexedRoot.loadTimedCommit(it) }.maxByOrNull { it.timestamp }!!
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

    logData.refreshAndWait(repo, waitIndexFinishing = false)
    val indexingWaiter = CompletableFuture<GitHistoryTraverser.IndexedRoot>()
    val indexWaiterDisposable = Disposable {}
    var blockExecutedCount = 0
    traverser.addIndexingListener(listOf(repo.root), testRootDisposable) { indexedRoots ->
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

  fun `test traverse from master`() {
    val file = "file.txt"
    touch(file, "content")
    val expectedCommitsCount = 10 // with initial commit
    repeat(expectedCommitsCount - 1) {
      makeCommit(file)
    }
    logData.refreshAndWait(repo, waitIndexFinishing = true)

    var commitsCount = 0
    traverser.traverse(
      repo.root,
      start = GitHistoryTraverser.StartNode.Branch("master"),
    ) {
      commitsCount++
      true
    }

    assertEquals(expectedCommitsCount, commitsCount)
  }

  fun `test IllegalArgumentException when start hash doesn't exist`() {
    val file = "file.txt"
    touch(file, "content")
    val expectedCommitsCount = 10 // with initial commit
    repeat(expectedCommitsCount - 1) {
      makeCommit(file)
    }
    logData.refreshAndWait(repo, waitIndexFinishing = true)

    val commitHashes = mutableSetOf<Hash>()
    traverser.traverse(
      repo.root,
      start = GitHistoryTraverser.StartNode.Branch("master"),
    ) { (id, _) ->
      loadMetadataLater(id) { metaData ->
        commitHashes.add(metaData.id)
      }
      true
    }
    fun getRandomHash(): Hash = HashImpl.build(
      buildString {
        repeat(VcsLogUtil.FULL_HASH_LENGTH) {
          val randomHexChar = kotlin.random.Random.nextInt(0 until 16).toString(16)
          append(randomHexChar)
        }
      }
    )

    var notExistedHash = getRandomHash()
    while (notExistedHash in commitHashes) {
      notExistedHash = getRandomHash()
    }
    try {
      traverser.traverse(
        repo.root,
        start = GitHistoryTraverser.StartNode.CommitHash(notExistedHash)
      ) {
        true
      }
      fail()
    }
    catch (e: IllegalArgumentException) {
    }
  }

  private fun areOnlyFilesInCommit(commit: GitCommit, fileNames: Collection<String>): Boolean {
    val fileNamesMap = fileNames.associateWith { false }.toMutableMap()
    for (change in commit.changes) {
      val fileName = ChangesUtil.getFilePath(change).name
      if (fileName !in fileNamesMap) {
        return false
      }
      fileNamesMap[fileName] = true
    }
    return fileNamesMap.values.all { it }
  }
}
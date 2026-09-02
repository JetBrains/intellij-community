// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.history

import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.drainUncaughtExceptions
import com.intellij.testFramework.junit5.fixture.disposableFixture
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.impl.HashImpl
import com.intellij.vcs.log.util.VcsUserUtil
import com.intellij.vcsUtil.VcsUtil
import git4idea.GitCommit
import git4idea.log.createLogDataIn
import git4idea.log.refreshAndWait
import git4idea.repo.GitObjectFormat
import git4idea.test.GitSingleRepoContext
import git4idea.test.gitSingleRepoContextFixture
import git4idea.test.makeCommit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.random.nextInt

@TestApplication
class GitHistoryTraverserImplTest {
  private val fixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = fixture.get()

  private val disposable by disposableFixture()
  private lateinit var testCs: CoroutineScope
  private lateinit var logData: VcsLogData

  private val traverser: GitHistoryTraverser
    get() = GitHistoryTraverserImpl(context.project, logData, disposable)

  @BeforeEach
  fun setUp(): Unit = with(context) {
    VcsLogData.getIndexingRegistryValue().setValue(true)
    @Suppress("RAW_SCOPE_CREATION")
    testCs = CoroutineScope(SupervisorJob())
    logData = createLogDataIn(testCs, repo, logProvider)
  }

  @AfterEach
  fun tearDown() {
    runBlocking {
      testCs.coroutineContext.job.cancelAndJoin()
    }

    drainIndexDiagnosticStorageCloseException()
    VcsLogData.getIndexingRegistryValue().resetToDefault()
  }

  @Test
  fun `test files from commits made by user`(): Unit = with(context) {
    val file = "file.txt"
    touch(file, "content")

    val authorCommits = mutableSetOf<Hash>()
    val author = VcsUserUtil.createUser("Name", "name@server.com")
    val anotherUser = VcsUserUtil.createUser("Another Name", "another.name@server.com")
    repeat(5) {
      authorCommits.add(HashImpl.build(makeCommit(author, file)))
      makeCommit(anotherUser, file)
    }

    logData.refreshAndWait(repo, waitIndexFinishing = true)

    traverser.addIndexingListener(listOf(repo.root), disposable) { indexedRoots ->
      val indexedRoot = indexedRoots.single()
      val authorCommitIds = indexedRoot.filterCommits(GitHistoryTraverser.IndexedRoot.TraverseCommitsFilter.Author(author))
      traverser.traverse(indexedRoot.root) { (commitId, _) ->
        if (commitId in authorCommitIds) {
          loadFullDetailsLater(commitId) { details ->
            assertThat(details.id in authorCommits).isTrue()
            assertThat(areOnlyFilesInCommit(details, setOf("file.txt"))).isTrue()
          }
        }
        true
      }
    }
  }

  @Test
  fun `test bfs early termination`(): Unit = with(context) {
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

    assertThat(fileInCommitCount).isEqualTo(3)
  }

  @Test
  fun `test last commit by user with file`(): Unit = with(context) {
    val file = "file.txt"
    val filePath = VcsUtil.getFilePath(touch(file, "content"), false)

    val anotherFile = "anotherFile.txt"
    touch(anotherFile, "content")

    val author = VcsUserUtil.createUser("Name", "name@server.com")
    val anotherUser = VcsUserUtil.createUser("Another Name", "another.name@server.com")

    makeCommit(author, file)
    makeCommit(anotherUser, file)
    makeCommit(author, file)
    makeCommit(author, anotherFile)
    val lastCommitByUserWithFile = makeCommit(author, file)
    makeCommit(anotherUser, file)
    makeCommit(anotherUser, anotherFile)
    makeCommit(anotherUser, file)

    logData.refreshAndWait(repo, waitIndexFinishing = true)
    traverser.addIndexingListener(listOf(repo.root), disposable) { indexedRoots ->
      val indexedRoot = indexedRoots.single()
      val authorCommitIds = indexedRoot.filterCommits(GitHistoryTraverser.IndexedRoot.TraverseCommitsFilter.Author(author))
      val fileCommits = indexedRoot.filterCommits(GitHistoryTraverser.IndexedRoot.TraverseCommitsFilter.File(filePath))

      val authorCommitsWithFile = authorCommitIds.intersect(fileCommits.toSet())
      val actualLastCommitByUserWithFile = authorCommitsWithFile.map { indexedRoot.loadTimedCommit(it) }.maxByOrNull { it.timestamp }!!
      val expectedCommitByUserWithFile = GitHistoryUtils.collectCommitsMetadata(project, repo.root, lastCommitByUserWithFile)!!.single()
      assertThat(actualLastCommitByUserWithFile.timestamp).isEqualTo(expectedCommitByUserWithFile.commitTime)
    }
  }

  @Test
  fun `test withIndex waiting for index`(): Unit = with(context) {
    val file = "file.txt"
    touch(file, "content")
    repeat(10) {
      makeCommit(file)
    }

    logData.refreshAndWait(repo, waitIndexFinishing = false)
    val indexingWaiter = CompletableFuture<GitHistoryTraverser.IndexedRoot>()
    val indexWaiterDisposable = Disposer.newDisposable()
    var blockExecutedCount = 0
    traverser.addIndexingListener(listOf(repo.root), disposable) { indexedRoots ->
      val indexedRoot = indexedRoots.single()
      blockExecutedCount++
      indexingWaiter.complete(indexedRoot)
    }
    try {
      val indexedRoot = indexingWaiter.get(5, TimeUnit.SECONDS)
      assertThat(indexedRoot.root).isEqualTo(repo.root)
      assertThat(logData.index.isIndexed(indexedRoot.root)).isTrue()
      assertThat(blockExecutedCount).isEqualTo(1)
    }
    catch (e: Exception) {
      fail(e.message)
    }
    finally {
      Disposer.dispose(indexWaiterDisposable)
    }
  }

  @Test
  fun `test traverse from master`(): Unit = with(context) {
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

    assertThat(commitsCount).isEqualTo(expectedCommitsCount)
  }

  @Test
  fun `test IllegalArgumentException when start hash doesn't exist`(): Unit = with(context) {
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
        repeat(GitObjectFormat.SHA1.hexSize) {
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
    catch (_: IllegalArgumentException) {
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

  private fun drainIndexDiagnosticStorageCloseException() {
    drainUncaughtExceptions { exception ->
      exception is IllegalStateException &&
      exception.message?.startsWith("Storage is closed:") == true &&
      exception.stackTrace.any { it.className == "com.intellij.vcs.log.data.index.IndexDiagnosticRunner" }
    }
  }
}

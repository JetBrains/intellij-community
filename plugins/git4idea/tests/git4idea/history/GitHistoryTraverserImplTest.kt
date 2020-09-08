// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.history

import com.intellij.openapi.vcs.Executor.touch
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.util.containers.getIfSingle
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.data.index.IndexDataGetter
import com.intellij.vcs.log.data.index.VcsLogIndex
import com.intellij.vcs.log.impl.HashImpl
import com.intellij.vcs.log.impl.VcsUserImpl
import com.intellij.vcsUtil.VcsUtil
import git4idea.log.createLogData
import git4idea.log.refreshAndWait
import git4idea.test.GitSingleRepoTest
import git4idea.test.makeCommit

class GitHistoryTraverserImplTest : GitSingleRepoTest() {
  private lateinit var logData: VcsLogData
  private val index: VcsLogIndex
    get() = logData.index

  private val dataGetter: IndexDataGetter
    get() = index.dataGetter!!

  private val traverser: GitHistoryTraverser
    get() = GitHistoryTraverserImpl(repo.project, repo.root, logData, dataGetter)

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

    val authorCommitIds = traverser.filterCommits(GitHistoryTraverser.TraverseCommitsFilter.Author(author))
    traverser.traverseFromHead(GitHistoryTraverser.TraverseType.DFS) { commitId ->
      if (commitId in authorCommitIds) {
        loadFullDetailsLater(commitId) { details ->
          assertTrue(details.id in authorCommits)
          assertTrue(ChangesUtil.getFiles(details.changes.stream()).getIfSingle()!!.name.startsWith("file"))
        }
      }
      true
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
    traverser.traverseFromHead(GitHistoryTraverser.TraverseType.BFS) { commitId ->
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

    val authorCommitIds = traverser.filterCommits(GitHistoryTraverser.TraverseCommitsFilter.Author(author))
    val fileCommits = traverser.filterCommits(GitHistoryTraverser.TraverseCommitsFilter.File(filePath))

    val authorCommitsWithFile = authorCommitIds.intersect(fileCommits)
    val actualLastCommitByUserWithFile = authorCommitsWithFile.map { traverser.loadTimedCommit(it) }.maxBy { it.timestamp }!!
    val expectedCommitByUserWithFile = GitHistoryUtils.collectCommitsMetadata(project, repo.root, lastCommitByUserWithFile)!!.single()
    assertEquals(expectedCommitByUserWithFile.commitTime, actualLastCommitByUserWithFile.timestamp)
  }
}
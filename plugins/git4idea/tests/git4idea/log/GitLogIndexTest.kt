// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.log

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.Executor.*
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.CollectConsumer
import com.intellij.util.Consumer
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.data.VcsLogStorage
import com.intellij.vcs.log.data.index.*
import com.intellij.vcs.log.impl.HashImpl
import com.intellij.vcs.log.impl.VcsUserImpl
import com.intellij.vcs.log.util.VcsUserUtil
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject
import com.intellij.vcsUtil.VcsUtil
import git4idea.test.*
import junit.framework.TestCase

class GitLogIndexTest : GitSingleRepoTest() {
  //companion object {
  //  init {
  //    System.setProperty("vcs.log.sqlite", "true")
  //  }
  //}

  private val defaultUser = VcsUserImpl(USER_NAME, USER_EMAIL)

  private lateinit var disposable: Disposable
  private lateinit var index: VcsLogPersistentIndex
  private val dataGetter: IndexDataGetter
    get() = index.dataGetter!!
  private val storage: VcsLogStorage
    get() = dataGetter.logStorage

  override fun setUp() {
    super.setUp()

    disposable = Disposer.newDisposable()
    Disposer.register(testRootDisposable, disposable)

    index = setUpIndex(myProject, repo.root, logProvider, disposable)
  }

  override fun tearDown() {
    try {
      Disposer.dispose(disposable)
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
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

    val collector = CollectConsumer<VcsCommitMetadata>()
    logProvider.readMetadata(repo.root, listOf(commitHash), collector)
    val expectedMetadata = collector.result.first()
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

  fun `test regexp text filter`() {
    val expected = mutableSetOf<Int>()
    val pattern = "[A-Z]+\\-\\d+"

    val file = "file.txt"
    touch(file, "content")
    repo.addCommit("some message")

    append(file, "more content")
    expected.add(getCommitIndex(repo.addCommit("message with ABC-18")))

    append(file, "and some more content")
    expected.add(getCommitIndex(repo.addCommit("message with CDE-239")))

    append(file, "even more content")
    repo.addCommit("some other message")

    append(file, "and even more content")
    expected.add(getCommitIndex(repo.addCommit("message with XYZ-42")))

    indexAll()

    val actual = dataGetter.filter(listOf(VcsLogFilterObject.fromPattern(pattern, isRegexpAllowed = true)))

    TestCase.assertEquals(expected, actual)
  }

  private fun `test text filter with multiple patterns`(keyword1: String, keyword2: String) {
    val expected = mutableSetOf<Int>()

    val file = "file.txt"
    touch(file, "content")
    repo.addCommit("some message without any keywords")

    append(file, "more content")
    expected.add(getCommitIndex(repo.addCommit("message with $keyword1")))

    append(file, "some more content")
    expected.add(getCommitIndex(repo.addCommit("message with $keyword2")))

    append(file, "even more content")
    repo.addCommit("some other message")

    indexAll()

    val actual = dataGetter.filter(listOf(VcsLogFilterObject.fromPatternsList(listOf(keyword1, keyword2))))

    assertEquals(expected.sorted(), actual.sorted())
  }

  fun `test text filter with multiple patterns`() {
    `test text filter with multiple patterns`("keyword1", "keyword2")
  }

  fun `test text filter with short and long patterns`() {
    `test text filter with multiple patterns`("k1", "keyword2")
  }

  fun `test text filter with short patterns`() {
    `test text filter with multiple patterns`("k1", "k2")
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

  fun `test text and author filter`() {
    val author = VcsUserImpl("Name", "name@server.com")
    val keyword = "keyword"
    val expected = mutableSetOf<Int>()

    val file = "file.txt"
    touch(file, "content")
    repo.addCommit("some message")

    setupUsername(project, author.name, author.email)

    append(file, "content 2")
    expected.add(getCommitIndex(repo.addCommit("some message with $keyword")))

    append(file, "content 3")
    repo.addCommit("some other message")

    setupDefaultUsername(project)

    append(file, "even more content")
    repo.addCommit("some other message with $keyword")

    indexAll()

    val actual = dataGetter.filter(listOf(VcsLogFilterObject.fromUser(author, setOf(author, defaultUser)),
                                          VcsLogFilterObject.fromPattern(keyword)))

    TestCase.assertEquals(expected, actual)
  }

  fun `test file history`() {
    val expectedHistory = mutableSetOf<Int>()

    val oldFile = "oldFile.txt"
    expectedHistory.add(getCommitIndex(tac(oldFile)))

    tac("somethingUnrelated.txt")

    val newFile = "newFile.txt"
    repo.mv(oldFile, newFile)
    expectedHistory.add(getCommitIndex(repo.addCommit("rename")))

    tac("somethingEvenMoreUnrelated.txt")

    expectedHistory.add(getCommitIndex(modify(newFile)))

    indexAll()

    val newHistory = dataGetter.filter(listOf(createPathFilter(newFile)))
    val oldHistory = dataGetter.filter(listOf(createPathFilter(oldFile)))

    TestCase.assertEquals(expectedHistory, newHistory)
    TestCase.assertEquals(expectedHistory, oldHistory)
  }

  fun `test directory history`() {
    val dir = "dir"
    mkdir(dir)

    val expectedHistory = mutableSetOf<Int>()

    cd(dir)
    val file1 = "file1.txt"
    expectedHistory.add(getCommitIndex(tac(file1)))
    expectedHistory.add(getCommitIndex(tac("file2.txt")))

    cd(repo.root)
    tac("somethingUnrelated.txt")

    cd(dir)
    expectedHistory.add(getCommitIndex(modify(file1)))

    cd(repo.root)
    tac("somethingEvenMoreUnrelated.txt")

    cd(dir)
    mv(file1, "file3.txt")
    expectedHistory.add(getCommitIndex(repo.addCommit("rename")))

    cd(repo.root)
    tac("somethingAbsolutelyUnrelated.txt")

    indexAll()

    val actualHistory = dataGetter.filter(listOf(createPathFilter(dir)))
    TestCase.assertEquals(expectedHistory, actualHistory)
  }

  private fun createPathFilter(relativePath: String) = VcsLogFilterObject.fromPaths(setOf(VcsUtil.getFilePath(child(relativePath))))

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
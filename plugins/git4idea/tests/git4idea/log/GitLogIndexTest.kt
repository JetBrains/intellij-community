// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.log

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.disposableFixture
import com.intellij.util.CollectConsumer
import com.intellij.util.Consumer
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.VcsLogObjectsFactory
import com.intellij.vcs.log.data.VcsLogStorage
import com.intellij.vcs.log.data.index.IndexDataGetter
import com.intellij.vcs.log.data.index.IndexedDetails
import com.intellij.vcs.log.data.index.VcsLogPersistentIndex
import com.intellij.vcs.log.data.index.index
import com.intellij.vcs.log.data.index.setUpIndex
import com.intellij.vcs.log.impl.HashImpl
import com.intellij.vcs.log.util.VcsUserUtil
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject
import git4idea.cherrypick.GitCherryPicker
import git4idea.test.GitSingleRepoContext
import git4idea.test.USER_EMAIL
import git4idea.test.USER_NAME
import git4idea.test.addCommit
import git4idea.test.appendAndCommit
import git4idea.test.build
import git4idea.test.gitSingleRepoContextFixture
import git4idea.test.last
import git4idea.test.makeCommit
import git4idea.test.modify
import git4idea.test.mv
import git4idea.test.readDetails
import git4idea.test.runUnderProgress
import git4idea.test.setupDefaultUsername
import git4idea.test.setupUsername
import git4idea.test.tac
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.ArgumentsProvider
import org.junit.jupiter.params.provider.ArgumentsSource
import org.junit.jupiter.params.support.ParameterDeclarations
import java.util.stream.Stream

/**
 * The shared VCS log index test suite, run against both the persistent-hash-map and the SQLite index implementation.
 */
@ParameterizedClass(name = "{0}")
@ArgumentsSource(GitLogIndexTest.Companion.TestArgumentsProvider::class)
@TestApplication
internal class GitLogIndexTest(val testType: String, val useSqlite: Boolean) {

  companion object {
    private class TestArgumentsProvider : ArgumentsProvider {
      override fun provideArguments(parameters: ParameterDeclarations?, context: ExtensionContext?): Stream<Arguments?> = Stream.of(
        Arguments.of("GitLogSqliteIndexTest", true),
        Arguments.of("GitLogPhmIndexTest", false)
      )
    }
  }

  private val contextFixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = contextFixture.get()

  private val disposableFixture = disposableFixture()

  private val defaultUser = VcsUserUtil.createUser(USER_NAME, USER_EMAIL)

  private lateinit var index: VcsLogPersistentIndex

  private val dataGetter: IndexDataGetter
    get() = index.dataGetter
  private val storage: VcsLogStorage
    get() = dataGetter.logStorage

  @BeforeEach
  fun setUp() {
    with(context) {
      index = setUpIndex(project, repo.root, logProvider, useSqlite, disposableFixture.get())
    }
  }

  @Test
  fun `test indexed`(): Unit = with(context) {
    val file = "file.txt"
    tac(file)
    for (i in 0 until 5) {
      repo.appendAndCommit(file, "new content ${i}\n")
    }

    val commits = indexAll()
    assertThat(index.isIndexed(repo.root)).isTrue()
    for (commit in commits) {
      assertThat(index.isIndexed(commit)).isTrue()
    }
  }

  @Test
  fun `test forward index`(): Unit = with(context) {
    val commitHash = tac("file.txt")

    indexAll()

    val collector = CollectConsumer<VcsCommitMetadata>()
    logProvider.readMetadata(repo.root, listOf(commitHash), collector)
    val expectedMetadata = collector.result.first()
    val actualMetadata = IndexedDetails(dataGetter, storage, getCommitIndex(commitHash), 0L)

    assertThat(actualMetadata.presentation()).isEqualTo(expectedMetadata.presentation())
  }

  @Test
  fun `test forward index with batch api`(): Unit = with(context) {
    val file = "file.txt"
    tac(file)
    for (i in 0 until 5) {
      repo.appendAndCommit(file, "new content ${i}\n")
    }

    val commits = indexAll()

    val collector = CollectConsumer<VcsCommitMetadata>()
    logProvider.readMetadata(repo.root, commits.map { getCommitHash(it) }, collector)
    // skips initial commit for sqlite since commits with no parents are not indexed
    // because it's not possible to distinguish between commits without parents and commits that were not indexed yet
    val expectedPresentation = collector.result.joinToString("\n\n") { metadata ->
      metadata.presentation().takeUnless { useSqlite && it.contains("initial") } ?: "NOT LOADED"
    }

    val actualMetadata = IndexedDetails.createMetadata(commits.toSet(), dataGetter, storage,
                                                       project.getService(VcsLogObjectsFactory::class.java))
    val actualPresentation = commits.map { actualMetadata[it] }.joinToString("\n\n") { it?.presentation() ?: "NOT LOADED" }

    assertThat(actualPresentation).isEqualTo(expectedPresentation)
  }

  @Test
  fun `test text filter`(): Unit = with(context) {
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

    assertThat(actual).isEqualTo(expected)
  }

  @Test
  fun `test regexp text filter`(): Unit = with(context) {
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

    assertThat(actual).isEqualTo(expected)
  }

  private fun checkTextFilterWithMultiplePatterns(keyword1: String, keyword2: String): Unit = with(context) {
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

    assertThat(actual.sorted()).isEqualTo(expected.sorted())
  }

  @Test
  fun `test text filter with multiple patterns`() {
    checkTextFilterWithMultiplePatterns("keyword1", "keyword2")
  }

  @Test
  fun `test text filter with short and long patterns`() {
    checkTextFilterWithMultiplePatterns("k1", "keyword2")
  }

  @Test
  fun `test text filter with short patterns`() {
    checkTextFilterWithMultiplePatterns("k1", "k2")
  }

  @Test
  fun `test author filter`(): Unit = with(context) {
    val file = "file.txt"
    touch(file, "content")
    repo.addCommit("some message")

    val author = VcsUserUtil.createUser("Name", "name@server.com")
    val expected = setOf(getCommitIndex(makeCommit(author, file)))

    append(file, "even more content")
    repo.addCommit("some other message")

    indexAll()

    val actual = dataGetter.filter(listOf(VcsLogFilterObject.fromUser(author, setOf(author, defaultUser))))

    assertThat(actual).isEqualTo(expected)
  }

  @Test
  fun `test author filter with different committer`(): Unit = with(context) {
    val author = VcsUserUtil.createUser("Name", "name@server.com")
    val expected = mutableSetOf<Int>()
    var hashToPick = ""
    build {
      master {
        0("a.txt")
        1("b.txt")
        expected.addAll(indexAll())
      }
      feature(1) {
        setupUsername(project, author.name, author.email)
        2("c.txt")
        3("d.txt")
        hashToPick = repo.last()
        setupDefaultUsername(project)
      }
      master {
        //cherry-pick with default user
        runUnderProgress {
          GitCherryPicker(project).cherryPick(readDetails(hashToPick))
        }
      }
    }

    indexAll()

    val actual = dataGetter.filter(listOf(VcsLogFilterObject.fromUser(defaultUser, setOf(author, defaultUser))))

    assertThat(actual).isEqualTo(expected)
  }

  @Test
  fun `test text and author filter`(): Unit = with(context) {
    val author = VcsUserUtil.createUser("Name", "name@server.com")
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

    assertThat(actual).isEqualTo(expected)
  }

  @Test
  fun `test file history`(): Unit = with(context) {
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

    assertThat(newHistory).isEqualTo(expectedHistory)
    assertThat(oldHistory).isEqualTo(expectedHistory)
  }

  @Test
  fun `test directory history`(): Unit = with(context) {
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
    assertThat(actualHistory).isEqualTo(expectedHistory)
  }

  private fun GitSingleRepoContext.createPathFilter(relativePath: String) =
    VcsLogFilterObject.fromPaths(setOf(childPath(relativePath)))

  private fun GitSingleRepoContext.getCommitIndex(hash: String): Int {
    return storage.getCommitIndex(HashImpl.build(hash), repo.root)
  }

  private fun getCommitHash(commit: Int): String {
    return storage.getCommitId(commit)!!.hash.asString()
  }

  private fun GitSingleRepoContext.indexAll(): Set<Int> {
    val commits = readCommits(repo.root)
    index.index(repo.root, commits)
    return commits
  }

  private fun GitSingleRepoContext.readCommits(root: VirtualFile): Set<Int> {
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

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.log

import com.intellij.openapi.util.text.StringUtil
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.ArrayUtilRt
import com.intellij.util.CollectConsumer
import com.intellij.util.Consumer
import com.intellij.vcs.log.TimedVcsCommit
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.VcsFullCommitDetails
import com.intellij.vcs.log.VcsLogFilterCollection
import com.intellij.vcs.log.VcsLogObjectsFactory
import com.intellij.vcs.log.VcsLogProvider
import com.intellij.vcs.log.VcsLogProvider.DetailedLogData
import com.intellij.vcs.log.VcsShortCommitDetails
import com.intellij.vcs.log.VcsUser
import com.intellij.vcs.log.data.toRefsLoadingPolicy
import com.intellij.vcs.log.graph.PermanentGraph
import com.intellij.vcs.log.impl.HashImpl
import com.intellij.vcs.log.impl.RequirementsImpl
import com.intellij.vcs.log.impl.SimpleLogProviderRequirements
import com.intellij.vcs.log.impl.VcsCommitMetadataImpl
import com.intellij.vcs.log.util.VcsUserUtil
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject.collection
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject.fromBranch
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject.fromPattern
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject.fromRange
import com.intellij.vcs.test.refresh
import git4idea.config.GitVersion
import git4idea.repo.GitObjectFormat
import git4idea.repo.GitRepositoryTagsHolderImpl
import git4idea.test.GitSingleRepoContext
import git4idea.test.USER_EMAIL
import git4idea.test.USER_NAME
import git4idea.test.addCommit
import git4idea.test.createRepository
import git4idea.test.git
import git4idea.test.gitSingleRepoContextFixture
import git4idea.test.last
import git4idea.test.log
import git4idea.test.modify
import git4idea.test.readAllRefs
import git4idea.test.setupDefaultUsername
import git4idea.test.setupUsername
import git4idea.test.tac
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

/**
 * Prior to 1.8.0 --regexp-ignore-case does not work when --fixed-strings parameter is specified,
 * so it is not possible to filter case-insensitively without regex.
 */
private val FIXED_STRINGS_WORKS_WITH_IGNORE_CASE = GitVersion(1, 8, 0, 0)

private const val EXPERIMENTAL_REFS_COLLECTION = "git.log.provider.experimental.refs.collection"

@TestApplication
@RegistryKey(EXPERIMENTAL_REFS_COLLECTION, "false")
internal class GitReadRecentCommitsTest : GitReadRecentCommitsTestBase()

@TestApplication
@RegistryKey(EXPERIMENTAL_REFS_COLLECTION, "true")
internal class GitExperimentalReadRecentCommitsTest : GitReadRecentCommitsTestBase()

@TestApplication
internal class GitLogProviderTest : GitLogProviderTestBase() {
  @Test
  fun `test all log with tagged branch`(): Unit = with(context) {
    prepareSomeHistory()
    createTaggedBranch()
    val expectedLog = readCommitsFromGit()
    val collector = mutableListOf<TimedVcsCommit>()
    logProvider.readAllHashes(projectRoot, CollectConsumer(collector))
    assertThat(collector).containsExactlyElementsOf(expectedLog)
  }

  @Test
  fun `test full hash check sha1 repository`(): Unit = with(context) {
    val sha1Repo = createRepository(project, testNioRoot.resolve("sha1-repo"), makeInitialCommit = true,
                                    objectFormat = GitObjectFormat.SHA1)
    val headHash = last()
    assertThat(headHash).hasSize(GitObjectFormat.SHA1.hexSize)

    assertThat(logProvider.isFullHash(sha1Repo.root, headHash)).isTrue()

    val hashPrefix = headHash.take(7)
    assertThat(logProvider.isFullHash(sha1Repo.root, hashPrefix)).isFalse()

    val notHex = "z".repeat(GitObjectFormat.SHA1.hexSize)
    assertThat(logProvider.isFullHash(sha1Repo.root, notHex)).isFalse()
  }

  @Test
  fun `test full hash check sha256 repository`(): Unit = with(context) {
    val sha256Repo = createRepository(project, testNioRoot.resolve("sha256-repo"), makeInitialCommit = true,
                                      objectFormat = GitObjectFormat.SHA256)
    val headHash = last()
    assertThat(headHash).hasSize(GitObjectFormat.SHA256.hexSize)

    assertThat(logProvider.isFullHash(sha256Repo.root, headHash)).isTrue()

    val hashPrefix = headHash.take(40)
    assertThat(logProvider.isFullHash(sha256Repo.root, hashPrefix)).isFalse()
  }

  @Test
  fun `test get current user`(): Unit = with(context) {
    val user = logProvider.getCurrentUser(projectRoot)
    assertThat(user).describedAs("User is not defined").isNotNull()
    assertThat(user!!.name).describedAs("User name is incorrect").isEqualTo(defaultUser.name)
    assertThat(user.email).describedAs("User email is incorrect").isEqualTo(defaultUser.email)
  }

  @Test
  fun `test filter by branch`(): Unit = with(context) {
    val hashes = generateHistoryForFilters(takeAllUsers = true, allBranches = false)
    val branchFilter = fromBranch("feature")
    repo.update()
    assertThat(getFilteredHashes(collection(branchFilter))).isEqualTo(hashes)
  }

  @Test
  fun `test filter by branch and user`(): Unit = with(context) {
    val hashes = generateHistoryForFilters(takeAllUsers = false, allBranches = false)
    val branchFilter = fromBranch("feature")
    val user = VcsUserUtil.createUser(USER_NAME, USER_EMAIL)
    val userFilter = VcsLogFilterObject.fromUser(user, setOf(user))
    repo.update()
    assertThat(getFilteredHashes(collection(branchFilter, userFilter))).isEqualTo(hashes)
  }

  @Test
  fun `test by range`(): Unit = with(context) {
    repo.tac("a.txt")
    val mergeBase = repo.tac("b.txt")
    val master1 = repo.tac("m1.txt")
    val master2 = repo.tac("m2.txt")
    git("checkout -b feature $mergeBase")
    repo.tac("d.txt")
    repo.update()

    val rangeFilter = fromRange("feature", "master")
    assertThat(getFilteredHashes(collection(rangeFilter))).containsExactly(master2, master1)
  }

  @Test
  fun `test by range and branch`(): Unit = with(context) {
    repo.tac("a.txt")
    git("branch old")
    val mergeBase = repo.tac("b.txt")
    val master1 = repo.tac("m1.txt")
    val master2 = repo.tac("m2.txt")
    git("checkout -b feature $mergeBase")
    repo.tac("d.txt")
    repo.update()

    val rangeFilter = fromRange("feature", "master")
    val branchFilter = fromBranch("old")
    val expected = mutableListOf(master2, master1)
    expected.addAll(StringUtil.splitByLines(repo.log("--pretty=%H old")))
    // NB: not possible to get ordered results here
    assertThat(getFilteredHashes(collection(rangeFilter, branchFilter))).containsExactlyInAnyOrderElementsOf(expected)
  }

  /**
   * 3 cases: no regexp + match case, regex + match case, regex + no matching case
   */
  @Test
  fun `test filter by text`(): Unit = with(context) {
    val initial = repo.last()

    val fileName = "f"

    touch(fileName, "content" + Math.random())
    val smallBrackets = repo.addCommit("[git] $fileName")
    echo(fileName, "content" + Math.random())
    val bigBrackets = repo.addCommit("[GIT] $fileName")
    echo(fileName, "content" + Math.random())
    val smallNoBrackets = repo.addCommit("git $fileName")
    echo(fileName, "content" + Math.random())
    val bigNoBrackets = repo.addCommit("GIT $fileName")

    val text = "[git]"
    assertThat(getFilteredHashes(collection(fromPattern(text, false, true))))
      .isEqualTo(listOf(smallBrackets))
    assertThat(getFilteredHashes(collection(fromPattern(text, true, false))))
      .isEqualTo(listOf(bigNoBrackets, smallNoBrackets, bigBrackets, smallBrackets, initial))
    assertThat(getFilteredHashes(collection(fromPattern(text, true, true))))
      .isEqualTo(listOf(smallNoBrackets, smallBrackets, initial))
  }

  @Test
  fun `test filter by text no regex`(): Unit = with(context) {
    assumeFixedStringsWorks()

    val fileName = "f"

    touch(fileName, "content" + Math.random())
    val smallBrackets = repo.addCommit("[git] $fileName")
    echo(fileName, "content" + Math.random())
    val bigBrackets = repo.addCommit("[GIT] $fileName")
    echo(fileName, "content" + Math.random())

    assertThat(getFilteredHashes(collection(fromPattern("[git]", false, false))))
      .isEqualTo(listOf(bigBrackets, smallBrackets))
  }

  @Test
  fun `test filter by text with regex and user`(): Unit = with(context) {
    checkFilterByTextAndUser(regexp = true)
  }

  @Test
  fun `test filter by simple text and user`(): Unit = with(context) {
    assumeFixedStringsWorks()
    checkFilterByTextAndUser(regexp = false)
  }

  @Test
  fun `test short details`(): Unit = with(context) {
    prepareLongHistory()
    val log = readCommitsFromGit()

    val hashes = mutableListOf<String>()
    logProvider.readAllHashes(projectRoot, Consumer { hashes.add(it.id.asString()) })

    val collectConsumer = CollectConsumer<VcsShortCommitDetails>()
    logProvider.readMetadata(projectRoot, hashes, collectConsumer)

    assertThat(collectConsumer.result.map { it.shortPresentation() })
      .containsExactlyElementsOf(log.map { it.shortPresentation() })
  }

  @Test
  fun `test full details`(): Unit = with(context) {
    prepareLongHistory()
    val log = readCommitsFromGit()

    val hashes = mutableListOf<String>()
    logProvider.readAllHashes(projectRoot, Consumer { hashes.add(it.id.asString()) })

    val result = mutableListOf<VcsFullCommitDetails>()
    logProvider.readFullDetails(projectRoot, hashes, Consumer { result.add(it) })

    // we do not check for changes here
    assertThat(result.map { it.metadataPresentation() })
      .containsExactlyElementsOf(log.map { it.metadataPresentation() })
  }

  private fun GitSingleRepoContext.assumeFixedStringsWorks() {
    assumeTrue(vcs.version.isLaterOrEqual(FIXED_STRINGS_WORKS_WITH_IGNORE_CASE)) {
      "Not testing: --regexp-ignore-case does not affect grep" +
      " or author filter when --fixed-strings parameter is specified prior to 1.8.0"
    }
  }

  private fun GitSingleRepoContext.checkFilterByTextAndUser(regexp: Boolean) {
    val hashes = generateHistoryForFilters(takeAllUsers = false, allBranches = true)
    val user = VcsUserUtil.createUser(USER_NAME, USER_EMAIL)
    val userFilter = VcsLogFilterObject.fromUser(user)
    val textFilter = fromPattern(if (regexp) ".*" else "", regexp, false)
    assertThat(getFilteredHashes(collection(userFilter, textFilter))).isEqualTo(hashes)
  }

  /**
   * Generates some history with two branches: master and feature, and made by two users.
   * Returns hashes of this history filtered by the given parameters.
   *
   * @param takeAllUsers if true, don't filter by users, otherwise filter by default user.
   */
  private fun GitSingleRepoContext.generateHistoryForFilters(takeAllUsers: Boolean, allBranches: Boolean): List<String> {
    val hashes = mutableListOf<String>()
    hashes.add(repo.last())

    setupUsername(project, "bob.smith", "bob.smith@example.com")
    if (takeAllUsers) {
      hashes.add(repo.tac("file.txt"))
    }
    setupDefaultUsername(project)

    hashes.add(repo.tac("file1.txt"))
    git("checkout -b feature")
    hashes.add(repo.tac("file2.txt"))
    git("checkout master")
    val commitOnlyInMaster = repo.tac("master.txt")
    if (allBranches) hashes.add(commitOnlyInMaster)

    hashes.reverse()
    refresh()
    return hashes
  }

  private fun GitSingleRepoContext.getFilteredHashes(filters: VcsLogFilterCollection): List<String> {
    val commits = logProvider.getCommitsMatchingFilter(projectRoot, filters, PermanentGraph.Options.Default, -1)
    return commits.map { it.id.asString() }
  }

  private fun GitSingleRepoContext.prepareLongHistory() {
    for (i in 0..<15) {
      val file = "a" + (i % 10) + ".txt"
      if (i < 10) {
        repo.tac(file)
      }
      else {
        repo.modify(file)
      }
    }
  }
}

internal abstract class GitReadRecentCommitsTestBase : GitLogProviderTestBase() {
  @Test
  fun `test init with tagged branch`(): Unit = with(context) {
    prepareSomeHistory()
    val expectedLogWithoutTaggedBranch = readCommitsFromGit()
    createTaggedBranch()

    val block = readRecentCommits(SimpleLogProviderRequirements(1000))
    assertThat(block.commits).containsExactlyElementsOf(expectedLogWithoutTaggedBranch)
  }

  @Test
  fun `test refresh with new tagged branch`(): Unit = with(context) {
    prepareSomeHistory()
    val prevRefs = readAllRefs(projectRoot, objectsFactory)
    createTaggedBranch()

    val expectedLog = readCommitsFromGit()
    val block = readRecentCommits(RequirementsImpl(1000, true, TestVcsRefsSequences(prevRefs)))
    assertThat(block.commits).containsExactlyInAnyOrderElementsOf(expectedLog)
  }

  @Test
  fun `test refresh when new tag moved`(): Unit = with(context) {
    prepareSomeHistory()
    val prevRefs = readAllRefs(projectRoot, objectsFactory)
    git("tag -f ATAG")

    val expectedLog = readCommitsFromGit()
    val refs = readAllRefs(projectRoot, objectsFactory)
    val block = readRecentCommits(RequirementsImpl(1000, true, TestVcsRefsSequences(prevRefs)))
    assertThat(block.commits).containsExactlyInAnyOrderElementsOf(expectedLog)
    assertThat(block.refsIterable.toList()).containsExactlyInAnyOrderElementsOf(refs)
  }

  @Test
  fun `test new tag on old commit`(): Unit = with(context) {
    prepareSomeHistory()
    val prevRefs = readAllRefs(projectRoot, objectsFactory)
    val commits = readCommitsFromGit()
    val firstCommit = commits[commits.size - 1].id.asString()
    git("tag NEW_TAG $firstCommit")

    val refs = readAllRefs(projectRoot, objectsFactory)
    val block = readRecentCommits(RequirementsImpl(1000, true, TestVcsRefsSequences(prevRefs)))
    assertThat(block.refsIterable.toList()).containsExactlyInAnyOrderElementsOf(refs)
  }

  @Test
  fun `test dont report origin HEAD`(): Unit = with(context) {
    prepareSomeHistory()
    git("update-ref refs/remotes/origin/HEAD master")

    val block = readRecentCommits(SimpleLogProviderRequirements(1000))
    assertThat(block.refsIterable.toList())
      .describedAs("origin/HEAD should be ignored")
      .noneMatch { it.name == "origin/HEAD" }
  }

  @Test
  fun `test support equally named branch and tag`(): Unit = with(context) {
    prepareSomeHistory()
    git("branch build")
    git("tag build")

    val data = readRecentCommits(RequirementsImpl(1000, true, TestVcsRefsSequences(emptyList())))
    val expectedLog = readCommitsFromGit()
    assertThat(data.commits).containsExactlyElementsOf(expectedLog)
    assertThat(data.refsIterable).anyMatch { it.name == "build" && it.type === GitRefManager.LOCAL_BRANCH }
    assertThat(data.refsIterable).anyMatch { it.name == "build" && it.type === GitRefManager.TAG }
  }

  private fun GitSingleRepoContext.readRecentCommits(requirements: VcsLogProvider.Requirements): DetailedLogData {
    repo.update()
    (repo.tagsHolder as? GitRepositoryTagsHolderImpl)?.updateForTests()
    return runBlocking {
      logProvider.readRecentCommits(projectRoot, requirements, requirements.toRefsLoadingPolicy())
    }
  }
}

internal abstract class GitLogProviderTestBase {
  private val contextFixture = gitSingleRepoContextFixture()
  protected val context: GitSingleRepoContext get() = contextFixture.get()

  protected val GitSingleRepoContext.objectsFactory: VcsLogObjectsFactory
    get() = project.getService(VcsLogObjectsFactory::class.java)

  protected val defaultUser: VcsUser = VcsUserUtil.createUser(USER_NAME, USER_EMAIL)

  protected fun GitSingleRepoContext.readCommitsFromGit(): List<VcsCommitMetadataImpl> {
    val output = git("log --all --date-order --full-history --sparse --pretty='%H|%P|%ct|%s|%B'")
    return StringUtil.splitByLines(output).map { record ->
      val items = ArrayUtilRt.toStringArray(StringUtil.split(record, "|", true, false))
      val time = items[2].toLong() * 1000
      VcsCommitMetadataImpl(
        HashImpl.build(items[0]),
        items[1].split(" ".toRegex()).dropLastWhile { it.isEmpty() }.map { HashImpl.build(it) },
        time,
        projectRoot,
        items[3],
        defaultUser,
        items[4],
        defaultUser,
        time
      )
    }
  }

  protected fun GitSingleRepoContext.prepareSomeHistory() {
    repo.tac("a.txt")
    git("tag ATAG")
    repo.tac("b.txt")
  }

  protected fun GitSingleRepoContext.createTaggedBranch() {
    val hash = repo.last()
    repo.tac("c.txt")
    repo.tac("d.txt")
    repo.tac("e.txt")
    git("tag poor-tag")
    git("reset --hard $hash")
  }

  protected fun VcsShortCommitDetails.shortPresentation(): String =
    "${id.toShortString()}\n$authorTime\n$author\n$commitTime\n$committer\n$subject"

  protected fun VcsCommitMetadata.metadataPresentation(): String = "${shortPresentation()}\n$fullMessage"
}

// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.log

import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.Executor
import com.intellij.util.ArrayUtilRt
import com.intellij.util.CollectConsumer
import com.intellij.util.Consumer
import com.intellij.util.Function
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
import git4idea.config.GitVersion
import git4idea.repo.GitRepositoryTagsHolderImpl
import git4idea.test.GitSingleRepoTest
import git4idea.test.USER_EMAIL
import git4idea.test.USER_NAME
import git4idea.test.addCommit
import git4idea.test.findGitLogProvider
import git4idea.test.last
import git4idea.test.log
import git4idea.test.modify
import git4idea.test.readAllRefs
import git4idea.test.setupDefaultUsername
import git4idea.test.setupUsername
import git4idea.test.tac
import junit.framework.TestCase
import kotlinx.coroutines.runBlocking
import org.junit.Assume

class GitReadRecentCommitsTest : GitReadRecentCommitsTestBase(collectRefsFromLog = true)

class GitExperimentalReadRecentCommitsTestBase : GitReadRecentCommitsTestBase(collectRefsFromLog = false)

class GitLogProviderTest : GitLogProviderTestBase() {
  fun test_all_log_with_tagged_branch() {
    prepareSomeHistory()
    createTaggedBranch()
    val expectedLog = readCommitsFromGit()
    val collector: MutableList<TimedVcsCommit?> = ArrayList<TimedVcsCommit?>()
    myLogProvider.readAllHashes(projectRoot, CollectConsumer<TimedVcsCommit?>(collector))
    assertOrderedEquals<TimedVcsCommit?>(expectedLog, collector)
  }

  fun test_get_current_user() {
    val user = myLogProvider.getCurrentUser(projectRoot)
    assertNotNull("User is not defined", user)
    val expected: VcsUser = defaultUser
    TestCase.assertEquals("User name is incorrect", expected.getName(), user!!.getName())
    TestCase.assertEquals("User email is incorrect", expected.getEmail(), user.getEmail())
  }

  fun test_filter_by_branch() {
    val hashes = generateHistoryForFilters(true, false)
    val branchFilter = fromBranch("feature")
    repo.update()
    val actualHashes = getFilteredHashes(collection(branchFilter))
    assertEquals(hashes, actualHashes)
  }

  fun test_filter_by_branch_and_user() {
    val hashes = generateHistoryForFilters(false, false)
    val branchFilter = fromBranch("feature")
    val user = VcsUserUtil.createUser(USER_NAME, USER_EMAIL)
    val userFilter = VcsLogFilterObject.fromUser(user, setOf(user))
    repo.update()
    val actualHashes = getFilteredHashes(collection(branchFilter, userFilter))
    assertEquals(hashes, actualHashes)
  }

  fun test_by_range() {
    repo.tac("a.txt")
    val mergeBase = repo.tac("b.txt")
    val master1 = repo.tac("m1.txt")
    val master2 = repo.tac("m2.txt")
    git("checkout -b feature $mergeBase")
    repo.tac("d.txt")
    repo.update()

    val rangeFilter = fromRange("feature", "master")
    val actualHashes = getFilteredHashes(collection(rangeFilter))
    assertOrderedEquals(actualHashes, listOf(master2, master1))
  }

  fun test_by_range_and_branch() {
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
    val actualHashes = getFilteredHashes(collection(rangeFilter, branchFilter))
    val expected: MutableList<String?> = ArrayList()
    expected.add(master2)
    expected.add(master1)
    expected.addAll(StringUtil.splitByLines(repo.log("--pretty=%H old")))
    assertSameElements(actualHashes, expected) // NB: not possible to get ordered results here
  }

  /*
 3 cases: no regexp + match case, regex + match case, regex + no matching case
  */
  fun test_filter_by_text() {
    val initial = repo.last()

    val fileName = "f"

    Executor.touch(fileName, "content" + Math.random())
    val smallBrackets = repo.addCommit("[git] $fileName")
    Executor.echo(fileName, "content" + Math.random())
    val bigBrackets = repo.addCommit("[GIT] $fileName")
    Executor.echo(fileName, "content" + Math.random())
    val smallNoBrackets = repo.addCommit("git $fileName")
    Executor.echo(fileName, "content" + Math.random())
    val bigNoBrackets = repo.addCommit("GIT $fileName")

    val text = "[git]"
    assertEquals(
      mutableListOf(smallBrackets),
      getFilteredHashes(collection(fromPattern(text, false, true)))
    )
    assertEquals(
      listOf(bigNoBrackets, smallNoBrackets, bigBrackets, smallBrackets, initial),
      getFilteredHashes(collection(fromPattern(text, true, false)))
    )
    assertEquals(
      listOf(smallNoBrackets, smallBrackets, initial),
      getFilteredHashes(collection(fromPattern(text, true, true)))
    )
  }

  fun test_filter_by_text_no_regex() {
    assumeFixedStringsWorks()

    val fileName = "f"

    Executor.touch(fileName, "content" + Math.random())
    val smallBrackets = repo.addCommit("[git] $fileName")
    Executor.echo(fileName, "content" + Math.random())
    val bigBrackets = repo.addCommit("[GIT] $fileName")
    Executor.echo(fileName, "content" + Math.random())

    assertEquals(
      listOf(bigBrackets, smallBrackets),
      getFilteredHashes(collection(fromPattern("[git]", false, false)))
    )
  }

  private fun assumeFixedStringsWorks() {
    Assume.assumeTrue(
      "Not testing: --regexp-ignore-case does not affect grep" +
      " or author filter when --fixed-strings parameter is specified prior to 1.8.0",
      vcs.version.isLaterOrEqual(FIXED_STRINGS_WORKS_WITH_IGNORE_CASE)
    )
  }

  private fun filter_by_text_and_user(regexp: Boolean) {
    val hashes = generateHistoryForFilters(false, true)
    val user = VcsUserUtil.createUser(USER_NAME, USER_EMAIL)
    val userFilter = VcsLogFilterObject.fromUser(user)
    val textFilter = fromPattern(if (regexp) ".*" else "", regexp, false)
    assertEquals(hashes, getFilteredHashes(collection(userFilter, textFilter)))
  }

  fun test_filter_by_text_with_regex_and_user() {
    filter_by_text_and_user(true)
  }

  fun test_filter_by_simple_text_and_user() {
    assumeFixedStringsWorks()
    filter_by_text_and_user(false)
  }

  fun test_short_details() {
    prepareLongHistory()
    val log = readCommitsFromGit()

    val hashes = mutableListOf<String>()
    myLogProvider.readAllHashes(
      projectRoot,
      Consumer { timedVcsCommit: TimedVcsCommit? -> hashes.add(timedVcsCommit!!.getId().asString()) })


    val collectConsumer = CollectConsumer<VcsShortCommitDetails?>()
    myLogProvider.readMetadata(projectRoot, hashes, collectConsumer)

    assertOrderedEquals(
      collectConsumer.getResult().map { shortDetailsToString.`fun`(it) },
      log.map { shortDetailsToString.`fun`(it) }
    )
  }

  fun test_full_details() {
    prepareLongHistory()
    val log = readCommitsFromGit()

    val hashes: MutableList<String> = ArrayList()
    myLogProvider.readAllHashes(
      projectRoot,
      Consumer { timedVcsCommit: TimedVcsCommit? -> hashes.add(timedVcsCommit!!.getId().asString()) })

    val result: MutableList<VcsFullCommitDetails> = ArrayList()
    myLogProvider.readFullDetails(projectRoot, hashes, Consumer { e: VcsFullCommitDetails? -> e?.let { result.add(it) } })

    // we do not check for changes here
    val shortDetailsToString = shortDetailsToString
    val metadataToString =
      Function { details: VcsCommitMetadata? -> shortDetailsToString.`fun`(details) + "\n" + details!!.getFullMessage() }
    assertOrderedEquals(
      result.map { metadataToString.`fun`(it) },
      log.map { metadataToString.`fun`(it) }
    )
  }

  /**
   * Generates some history with two branches: master and feature, and made by two users.
   * Returns hashes of this history filtered by the given parameters:
   *
   * @param takeAllUsers if true, don't filter by users, otherwise filter by default user.
   */
  private fun generateHistoryForFilters(takeAllUsers: Boolean, allBranches: Boolean): MutableList<String?> {
    val hashes: MutableList<String?> = ArrayList()
    hashes.add(repo.last())

    setupUsername(myProject, "bob.smith", "bob.smith@example.com")
    if (takeAllUsers) {
      val commitByBob = repo.tac("file.txt")
      hashes.add(commitByBob)
    }
    setupDefaultUsername(myProject)

    hashes.add(repo.tac("file1.txt"))
    git("checkout -b feature")
    val commitOnlyInFeature = repo.tac("file2.txt")
    hashes.add(commitOnlyInFeature)
    git("checkout master")
    val commitOnlyInMaster = repo.tac("master.txt")
    if (allBranches) hashes.add(commitOnlyInMaster)

    hashes.reverse()
    refresh()
    return hashes
  }

  private fun getFilteredHashes(filters: VcsLogFilterCollection): List<String> {
    val commits = myLogProvider.getCommitsMatchingFilter(projectRoot, filters, PermanentGraph.Options.Default, -1)
    return commits.map { commit: TimedVcsCommit -> commit.getId().asString() }
  }

  private fun prepareLongHistory() {
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

abstract class GitReadRecentCommitsTestBase(val collectRefsFromLog: Boolean) : GitLogProviderTestBase() {
  override fun setUp() {
    super.setUp()
    setRegistryPropertyForTest("git.log.provider.experimental.refs.collection", (!collectRefsFromLog).toString())
  }

  fun test_init_with_tagged_branch() {
    prepareSomeHistory()
    val expectedLogWithoutTaggedBranch = readCommitsFromGit()
    createTaggedBranch()

    val block = readRecentCommits(SimpleLogProviderRequirements(1000))
    assertOrderedEquals(block.commits, expectedLogWithoutTaggedBranch)
  }

  fun test_refresh_with_new_tagged_branch() {
    prepareSomeHistory()
    val prevRefs = readAllRefs(projectRoot, myObjectsFactory)
    createTaggedBranch()

    val expectedLog = readCommitsFromGit()
    val block = readRecentCommits(RequirementsImpl(1000, true, TestVcsRefsSequences(prevRefs)))
    assertSameElements(block.commits, expectedLog)
  }

  fun test_refresh_when_new_tag_moved() {
    prepareSomeHistory()
    val prevRefs = readAllRefs(projectRoot, myObjectsFactory)
    git("tag -f ATAG")

    val expectedLog = readCommitsFromGit()
    val refs = readAllRefs(projectRoot, myObjectsFactory)
    val block = readRecentCommits(RequirementsImpl(1000, true, TestVcsRefsSequences(prevRefs)))
    assertSameElements(block.commits, expectedLog)
    assertSameElements(block.refsIterable.toList(), refs)
  }

  fun test_new_tag_on_old_commit() {
    prepareSomeHistory()
    val prevRefs = readAllRefs(projectRoot, myObjectsFactory)
    val commits = readCommitsFromGit()
    val firstCommit = commits[commits.size - 1].id.asString()
    git("tag NEW_TAG $firstCommit")

    val refs = readAllRefs(projectRoot, myObjectsFactory)
    val block = readRecentCommits(RequirementsImpl(1000, true, TestVcsRefsSequences(prevRefs)))
    assertSameElements(block.refsIterable.toList(), refs)
  }

  fun test_dont_report_origin_HEAD() {
    prepareSomeHistory()
    git("update-ref refs/remotes/origin/HEAD master")

    val block = readRecentCommits(SimpleLogProviderRequirements(1000))
    assertFalse(
      "origin/HEAD should be ignored",
      block.refsIterable.toList().any { ref -> ref.getName() == "origin/HEAD" }
    )
  }

  fun test_support_equally_named_branch_and_tag() {
    prepareSomeHistory()
    git("branch build")
    git("tag build")

    val data = readRecentCommits(RequirementsImpl(1000, true, TestVcsRefsSequences(emptyList())))
    val expectedLog = readCommitsFromGit()
    assertOrderedEquals(data.commits, expectedLog)
    assertTrue(
      data.refsIterable.any { ref -> ref.getName() == "build" && ref.getType() === GitRefManager.LOCAL_BRANCH }
    )
    assertTrue(
      data.refsIterable.any { ref -> ref.getName() == "build" && ref.getType() === GitRefManager.TAG }
    )
  }

  private fun readRecentCommits(requirements: VcsLogProvider.Requirements): DetailedLogData {
    repo.update()
    (repo.tagsHolder as? GitRepositoryTagsHolderImpl)?.updateForTests()
    return runBlocking {
      val refsLoadingPolicy = requirements.toRefsLoadingPolicy()
      myLogProvider.readRecentCommits(projectRoot, requirements, refsLoadingPolicy)
    }
  }
}


abstract class GitLogProviderTestBase : GitSingleRepoTest() {
  protected lateinit var myLogProvider: GitLogProvider
  protected lateinit var myObjectsFactory: VcsLogObjectsFactory

  public override fun setUp() {
    super.setUp()
    myLogProvider = findGitLogProvider(myProject)
    myObjectsFactory = myProject.getService(VcsLogObjectsFactory::class.java)
  }

  protected fun readCommitsFromGit(): List<VcsCommitMetadataImpl> {
    val output = git("log --all --date-order --full-history --sparse --pretty='%H|%P|%ct|%s|%B'")
    val defaultUser: VcsUser = defaultUser
    return StringUtil.splitByLines(output).map { record ->
      val items = ArrayUtilRt.toStringArray(StringUtil.split(record!!, "|", true, false))
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

  protected fun prepareSomeHistory() {
    repo.tac("a.txt")
    git("tag ATAG")
    repo.tac("b.txt")
  }

  protected fun createTaggedBranch() {
    val hash = repo.last()
    repo.tac("c.txt")
    repo.tac("d.txt")
    repo.tac("e.txt")
    git("tag poor-tag")
    git("reset --hard $hash")
  }

  companion object {
    /**
     * Prior to 1.8.0 --regexp-ignore-case does not work when --fixed-strings parameter is specified, so can not filter case-insensitively without regex.
     */
    @JvmStatic
    protected val FIXED_STRINGS_WORKS_WITH_IGNORE_CASE = GitVersion(1, 8, 0, 0)

    @JvmStatic
    protected val shortDetailsToString: Function<VcsShortCommitDetails?, String?>
      get() = Function { details: VcsShortCommitDetails? ->
        var result = ""
        result += details!!.getId().toShortString() + "\n"
        result += details.getAuthorTime().toString() + "\n"
        result += details.getAuthor().toString() + "\n"
        result += details.getCommitTime().toString() + "\n"
        result += details.getCommitter().toString() + "\n"
        result += details.getSubject()
        result
      }

    @JvmStatic
    protected val defaultUser: VcsUser
      get() = VcsUserUtil.createUser(USER_NAME, USER_EMAIL)
  }
}

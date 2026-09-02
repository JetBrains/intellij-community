// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.history

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.Consumer
import com.intellij.vcs.test.refresh
import com.intellij.vcs.test.updateChangeListManager
import com.intellij.vcsUtil.VcsUtil.getFilePath
import git4idea.GitRevisionNumber
import git4idea.test.GitSingleRepoContext
import git4idea.test.addCommit
import git4idea.test.checkout
import git4idea.test.checkoutNew
import git4idea.test.commit
import git4idea.test.createSubRepository
import git4idea.test.git
import git4idea.test.gitSingleRepoContextFixture
import git4idea.test.last
import git4idea.test.lastAuthorTime
import git4idea.test.log
import git4idea.test.mv
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.util.Date
import kotlin.io.path.exists
import kotlin.io.path.writeText

/**
 * Tests for low-level history methods in GitHistoryUtils.
 * There are some known problems with newlines and whitespaces in commit messages, these are ignored by the tests for now.
 * (see #convertWhitespacesToSpacesAndRemoveDoubles).
 */
@TestApplication
class GitHistoryUtilsTest {
  private val fixture = gitSingleRepoContextFixture(makeInitialCommit = false)
  private val context: GitSingleRepoContext get() = fixture.get()

  private lateinit var afile: Path
  private lateinit var bfile: Path
  private lateinit var revisions: MutableList<GitTestRevision>

  @org.junit.jupiter.api.BeforeEach
  fun setUp(): Unit = with(context) {

    revisions = ArrayList(7)

    // initial
    afile = touch("a.txt", "initial content")
    var hash = repo.addCommit("initial commit")
    revisions.add(GitTestRevision(hash, timeStampToDate(repo.lastAuthorTime())))

    // modify
    afile.writeText("second content", Charsets.UTF_8)
    hash = repo.addCommit("simple commit")
    revisions.add(GitTestRevision(hash, timeStampToDate(repo.lastAuthorTime())))

    // mv to dir
    val dir = mkdir("dir")
    bfile = dir.resolve("b.txt")
    assertThat(bfile.exists()).describedAs("File $bfile shouldn't have existed").isFalse()
    repo.mv(afile.toString(), bfile.toString())
    assertThat(bfile.exists()).describedAs("File $bfile was not created by mv command").isTrue()
    hash = repo.commit("moved a.txt to dir/b.txt")
    revisions.add(GitTestRevision(hash, timeStampToDate(repo.lastAuthorTime())))


    val messages = listOf("simple commit after rename",
                          "commit with {%n} some [%ct] special <format:%H%at> characters including " +
                          "--pretty=tformat:%x00%x01%x00%H%x00%ct%x00%an%x20%x3C%ae%x3E%x00%cn%x20%x3C%ce%x3E%x00%x02%x00%s%x00%b%x00%x02%x01",
                          "commit subject\n\ncommit body which is \n multilined.",
                          "first line\nsecond line\nthird line\n\nfifth line\n\nseventh line & the end.")
    val contents = listOf("fourth content", "fifth content", "sixth content", "seventh content")
    // modifications
    for (i in messages.indices) {
      overwrite(bfile, contents[i])
      hash = repo.addCommit(messages[i])
      revisions.add(GitTestRevision(hash, timeStampToDate(repo.lastAuthorTime())))
    }

    revisions.reverse()

    cd(projectPath)
    updateChangeListManager()
  }

  @Throws(Exception::class)
  @Test
  fun testGetCurrentRevision(): Unit = with(context) {
    val revisionNumber = GitHistoryUtils.getCurrentRevision(project, getFilePath(bfile, false), null) as GitRevisionNumber?
    assertThat(revisions[0].hash).isEqualTo(revisionNumber!!.rev)
    assertThat(revisions[0].date).isEqualTo(revisionNumber.timestamp)
  }

  @Throws(Exception::class)
  @Test
  fun testGetCurrentRevisionInMasterBranch(): Unit = with(context) {
    val revisionNumber = GitHistoryUtils.getCurrentRevision(project, getFilePath(bfile, false), "master") as GitRevisionNumber?
    assertThat(revisions[0].hash).isEqualTo(revisionNumber!!.rev)
    assertThat(revisions[0].date).isEqualTo(revisionNumber.timestamp)
  }

  @Throws(Exception::class)
  @Test
  fun testGetCurrentRevisionInOtherBranch(): Unit = with(context) {
    repo.checkout("-b feature")
    overwrite(bfile, "new content")
    repo.addCommit("new content")
    val output = repo.log("master --pretty=%H#%at", "-n1").trim { it <= ' ' }.split(
      "#".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

    val revisionNumber = GitHistoryUtils.getCurrentRevision(project, getFilePath(bfile, false), "master") as GitRevisionNumber?
    assertThat(output[0]).isEqualTo(revisionNumber!!.rev)
    assertThat(timeStampToDate(output[1])).isEqualTo(revisionNumber.timestamp)
  }

  @Throws(Exception::class)
  @Test
  fun testGetLastRevisionForExistingFile(): Unit = with(context) {
    val state = GitHistoryUtils.getLastRevision(project, getFilePath(bfile, false))
    assertThat(state!!.isItemExists).isTrue()
    val revisionNumber = state.number as GitRevisionNumber
    assertThat(revisions[0].hash).isEqualTo(revisionNumber.rev)
    assertThat(revisions[0].date).isEqualTo(revisionNumber.timestamp)
  }

  @Throws(Exception::class)
  @Test
  fun testGetLastRevisionForNonExistingFile(): Unit = with(context) {
    val child = repo.createSubRepository("child")

    git("remote add origin file://${child.root.path}.git")
    git("config branch.master.remote origin")
    git("config branch.master.merge refs/heads/master")

    git("rm $bfile")
    repo.commit("removed bfile")
    val hashAndDate = repo.log("--pretty=format:%H#%ct", "-n1").split("#".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
    git("update-ref refs/remotes/origin/master HEAD") // to avoid pushing to this fake origin

    touch("dir/b.txt", "content")
    repo.addCommit("recreated bfile")

    refresh()
    repo.update()

    val state = GitHistoryUtils.getLastRevision(project, getFilePath(bfile, false))
    assertThat(!state!!.isItemExists).isTrue()
    val revisionNumber = state.number as GitRevisionNumber
    assertThat(hashAndDate[0]).isEqualTo(revisionNumber.rev)
    assertThat(timeStampToDate(hashAndDate[1])).isEqualTo(revisionNumber.timestamp)
  }

  @Test
  fun testHistoryWithMergeCommit(): Unit = with(context) {
    repo.checkoutNew("newBranch", revisions.last().hash)

    git("rm $afile")
    repo.addCommit("remove a.txt")
    // difference with master is going to be in one file (bfile)
    // so merge commit is going to have no difference with one of the parents

    val success = git.merge(repo, "master", mutableListOf("--no-ff")).success()
    if (!success) {
      Assertions.fail<Nothing>("Could not do a merge")
    }

    val mergeCommit = repo.last()

    val history = GitHistoryUtils.history(project, projectRoot)
    assertThat(history.find { it.id.asString() == mergeCommit }).describedAs("History does not contain merge commit").isNotNull()
    assertThat(history.first().id.asString()).describedAs("Merge commit is not the first").isEqualTo(mergeCommit)
  }

  @Test
  fun testCollectCommitsMetadataFromReference(): Unit = with(context) {
    val branchName = "newBranch"
    repo.checkoutNew(branchName, revisions.last().hash)
    overwrite(afile, "new branch content")
    val commitMessage = "change a file"
    val hash = repo.addCommit(commitMessage)

    val commit = GitHistoryUtils.collectCommitsMetadata(project, projectRoot, branchName)!!.single()
    assertThat(commit.id.asString()).isEqualTo(hash)
    assertThat(commit.fullMessage).isEqualTo(commitMessage)
  }

  @Test
  fun testLoadTimedCommits(): Unit = with(context) {
    val branchName = "newBranch"
    repo.checkoutNew(branchName, revisions.last().hash)
    repo.checkout(revisions.first().hash)

    val hashes = mutableListOf<String>()
    GitHistoryUtils.loadTimedCommits(project, projectRoot, Consumer { hashes.add(it.id.asString()) }, "$branchName..HEAD")

    assertThat(hashes).isEqualTo(revisions.subList(0, revisions.size - 1).map { it.hash })
  }

  private fun timeStampToDate(timestamp: String): Date {
    return Date(java.lang.Long.parseLong(timestamp) * 1000)
  }

  private class GitTestRevision(val hash: String, val date: Date) {
    override fun toString(): String {
      return hash
    }
  }
}

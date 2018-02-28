/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package git4idea.history

import com.intellij.dvcs.DvcsUtil.getShortHash
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.Executor.*
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.history.VcsFileRevision
import com.intellij.util.CollectConsumer
import com.intellij.util.Consumer
import com.intellij.util.ExceptionUtil
import com.intellij.vcsUtil.VcsUtil
import git4idea.GitFileRevision
import git4idea.GitRevisionNumber
import git4idea.test.*
import junit.framework.TestCase
import java.io.File
import java.io.IOException
import java.util.*

/**
 * Tests for low-level history methods in GitHistoryUtils.
 * There are some known problems with newlines and whitespaces in commit messages, these are ignored by the tests for now.
 * (see #convertWhitespacesToSpacesAndRemoveDoubles).
 */
class GitHistoryUtilsTest : GitSingleRepoTest() {

  private var bfile: File? = null
  private var myRevisions: MutableList<GitTestRevision>? = null
  private var myRevisionsAfterRename: MutableList<GitTestRevision>? = null

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()

    myRevisions = ArrayList(7)
    myRevisionsAfterRename = ArrayList(4)

    // 1. create a file
    // 2. simple edit with a simple commit message
    // 3. move & rename
    // 4. make 4 edits with commit messages of different complexity
    // (note: after rename, because some GitHistoryUtils methods don't follow renames).

    val commitMessages = arrayOf("initial commit",
                                 "simple commit",
                                 "moved a.txt to dir/b.txt",
                                 "simple commit after rename",
                                 "commit with {%n} some [%ct] special <format:%H%at> characters including --pretty=tformat:%x00%x01%x00%H%x00%ct%x00%an%x20%x3C%ae%x3E%x00%cn%x20%x3C%ce%x3E%x00%x02%x00%s%x00%b%x00%x02%x01",
                                 "commit subject\n\ncommit body which is \n multilined.",
                                 "first line\nsecond line\nthird line\n\nfifth line\n\nseventh line & the end.")
    val contents = arrayOf("initial content", "second content", "second content", // content is the same after rename
                           "fourth content", "fifth content", "sixth content", "seventh content")

    // initial
    var commitIndex = 0
    val afile = touch("a.txt", contents[commitIndex])
    repo.addCommit(commitMessages[commitIndex])
    commitIndex++

    // modify
    overwrite(afile, contents[commitIndex])
    repo.addCommit(commitMessages[commitIndex])
    val RENAME_COMMIT_INDEX = commitIndex
    commitIndex++

    // mv to dir
    val dir = mkdir("dir")
    bfile = File(dir.path, "b.txt")
    TestCase.assertFalse("File $bfile shouldn't have existed", bfile!!.exists())
    repo.mv(afile, bfile!!)
    TestCase.assertTrue("File $bfile was not created by mv command", bfile!!.exists())
    repo.commit(commitMessages[commitIndex])
    commitIndex++

    // modifications
    for (i in 0..3) {
      overwrite(bfile!!, contents[commitIndex])
      repo.addCommit(commitMessages[commitIndex])
      commitIndex++
    }

    // Retrieve hashes and timestamps
    val revisions = repo.log("--pretty=format:%H#%at#%P", "-M").split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
    TestCase.assertEquals("Incorrect number of revisions", commitMessages.size, revisions.size)
    // newer revisions go first in the log output
    var i = revisions.size - 1
    var j = 0
    while (i >= 0) {
      val details = revisions[j].trim { it <= ' ' }.split("#".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
      val revision = GitTestRevision(details[0], details[1], commitMessages[i],
                                     USER_NAME, USER_EMAIL, USER_NAME, USER_EMAIL, null,
                                     contents[i])
      myRevisions!!.add(revision)
      if (i > RENAME_COMMIT_INDEX) {
        myRevisionsAfterRename!!.add(revision)
      }
      i--
      j++
    }

    TestCase.assertEquals("setUp failed", 5, myRevisionsAfterRename!!.size)
    cd(projectPath)
    updateChangeListManager()
  }

  override fun makeInitialCommit(): Boolean {
    return false
  }

  // Inspired by IDEA-89347
  @Throws(Exception::class)
  fun testCyclicRename() {
    val commits = ArrayList<TestCommit>()

    val source = mkdir("source")
    val initialFile = touch("source/PostHighlightingPass.java", "Initial content")
    val initMessage = "Created PostHighlightingPass.java in source"
    repo.addCommit(initMessage)
    val hash = this.last()
    commits.add(TestCommit(hash, initMessage, initialFile.path))

    var filePath = initialFile.path

    commits.add(modify(filePath))

    var commit = move(filePath, mkdir("codeInside-impl"), "Moved from source to codeInside-impl")
    filePath = commit.myPath
    commits.add(commit)
    commits.add(modify(filePath))

    commit = move(filePath, mkdir("codeInside"), "Moved from codeInside-impl to codeInside")
    filePath = commit.myPath
    commits.add(commit)
    commits.add(modify(filePath))

    commit = move(filePath, mkdir("lang-impl"), "Moved from codeInside to lang-impl")
    filePath = commit.myPath
    commits.add(commit)
    commits.add(modify(filePath))

    commit = move(filePath, source, "Moved from lang-impl back to source")
    filePath = commit.myPath
    commits.add(commit)
    commits.add(modify(filePath))

    commit = move(filePath, mkdir("java"), "Moved from source to java")
    filePath = commit.myPath
    commits.add(commit)
    commits.add(modify(filePath))

    Collections.reverse(commits)
    val vFile = VcsUtil.getVirtualFileWithRefresh(File(filePath))
    TestCase.assertNotNull(vFile)
    val history = GitFileHistory.collectHistory(myProject, VcsUtil.getFilePath(vFile!!))
    TestCase.assertEquals("History size doesn't match. Actual history: \n" + toReadable(history), commits.size, history.size)
    TestCase.assertEquals("History is different.", toReadable(commits), toReadable(history))
  }

  private class TestCommit(val hash: String, val commitMessage: String, val myPath: String)

  private fun move(file: String, dir: File, message: String): TestCommit {
    var file = file
    val NAME = "PostHighlightingPass.java"
    repo.mv(file, dir.path)
    file = File(dir, NAME).path
    repo.addCommit(message)
    val hash = this.last()
    return TestCommit(hash, message, file)
  }

  @Throws(IOException::class)
  private fun modify(file: String): TestCommit {
    FileUtil.appendToFile(File(file), "Modified")
    val message = "Modified PostHighlightingPass"
    repo.addCommit(message)
    val hash = this.last()
    return TestCommit(hash, message, file)
  }

  private fun toReadable(history: Collection<VcsFileRevision>): String {
    val maxSubjectLength = findMaxLength(history, { revision -> revision.getCommitMessage() ?: "" })
    val sb = StringBuilder()
    for (revision in history) {
      val rev = revision as GitFileRevision
      val relPath = FileUtil.getRelativePath(File(projectPath), rev.path.ioFile)
      sb.append(String.format("%s  %-" + maxSubjectLength + "s  %s%n", getShortHash(rev.hash), rev.commitMessage, relPath))
    }
    return sb.toString()
  }

  private fun toReadable(commits: List<TestCommit>): String {
    val maxSubjectLength = findMaxLength(commits, { revision -> revision.commitMessage })
    val sb = StringBuilder()
    for (commit in commits) {
      val relPath = FileUtil.getRelativePath(File(projectPath), File(commit.myPath))
      sb.append(String.format("%s  %-" + maxSubjectLength + "s  %s%n", getShortHash(commit.hash), commit.commitMessage, relPath))
    }
    return sb.toString()
  }

  private fun <T> findMaxLength(list: Collection<T>, convertor: (T) -> String): Int {
    var max = 0
    for (element in list) {
      val length = convertor(element).length
      if (length > max) {
        max = length
      }
    }
    return max
  }

  @Throws(Exception::class)
  fun testGetCurrentRevision() {
    val revisionNumber = GitHistoryUtils.getCurrentRevision(myProject, toFilePath(bfile!!), null) as GitRevisionNumber?
    TestCase.assertEquals(revisionNumber!!.rev, myRevisions!![0].myHash)
    TestCase.assertEquals(revisionNumber.timestamp, myRevisions!![0].myDate)
  }

  @Throws(Exception::class)
  fun testGetCurrentRevisionInMasterBranch() {
    val revisionNumber = GitHistoryUtils.getCurrentRevision(myProject, toFilePath(bfile!!), "master") as GitRevisionNumber?
    TestCase.assertEquals(revisionNumber!!.rev, myRevisions!![0].myHash)
    TestCase.assertEquals(revisionNumber.timestamp, myRevisions!![0].myDate)
  }

  @Throws(Exception::class)
  fun testGetCurrentRevisionInOtherBranch() {
    repo.checkout("-b feature")
    overwrite(bfile!!, "new content")
    repo.addCommit("new content")
    val output = repo.log("master --pretty=%H#%at", "-n1").trim { it <= ' ' }.split(
      "#".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

    val revisionNumber = GitHistoryUtils.getCurrentRevision(myProject, toFilePath(bfile!!), "master") as GitRevisionNumber?
    TestCase.assertEquals(revisionNumber!!.rev, output[0])
    TestCase.assertEquals(revisionNumber.timestamp, GitTestRevision.gitTimeStampToDate(output[1]))
  }

  private fun toFilePath(file: File): FilePath {
    return VcsUtil.getFilePath(file)
  }

  @Throws(Exception::class)
  fun testGetLastRevisionForExistingFile() {
    val state = GitHistoryUtils.getLastRevision(myProject, toFilePath(bfile!!))
    TestCase.assertTrue(state!!.isItemExists)
    val revisionNumber = state.number as GitRevisionNumber
    TestCase.assertEquals(revisionNumber.rev, myRevisions!![0].myHash)
    TestCase.assertEquals(revisionNumber.timestamp, myRevisions!![0].myDate)
  }

  @Throws(Exception::class)
  fun testGetLastRevisionForNonExistingFile() {
    git("remote add origin git://example.com/repo.git")
    git("config branch.master.remote origin")
    git("config branch.master.merge refs/heads/master")

    git("rm " + bfile!!.path)
    repo.commit("removed bfile")
    val hashAndDate = repo.log("--pretty=format:%H#%ct", "-n1").split("#".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
    git("update-ref refs/remotes/origin/master HEAD") // to avoid pushing to this fake origin

    touch("dir/b.txt", "content")
    repo.addCommit("recreated bfile")

    refresh()
    repo.update()

    val state = GitHistoryUtils.getLastRevision(myProject, toFilePath(bfile!!))
    TestCase.assertTrue(!state!!.isItemExists)
    val revisionNumber = state.number as GitRevisionNumber
    TestCase.assertEquals(revisionNumber.rev, hashAndDate[0])
    TestCase.assertEquals(revisionNumber.timestamp, GitTestRevision.gitTimeStampToDate(hashAndDate[1]))
  }

  @Throws(Exception::class)
  fun testHistory() {
    val revisions = GitFileHistory.collectHistory(myProject, toFilePath(bfile!!))
    assertHistory(revisions)
  }

  @Throws(Exception::class)
  fun testAppendableHistory() {
    val revisions = ArrayList<GitFileRevision>(3)

    GitFileHistory.loadHistory(myProject, toFilePath(bfile!!), repo.root, null, CollectConsumer(revisions),
                               Consumer { exception: VcsException ->
                                 TestCase.fail("No exception expected " + ExceptionUtil.getThrowableText(exception))
                               })

    assertHistory(revisions)
  }

  @Throws(Exception::class)
  fun testOnlyHashesHistory() {
    val history = GitHistoryUtils.onlyHashesHistory(myProject, toFilePath(bfile!!), projectRoot)
    TestCase.assertEquals(history.size, myRevisionsAfterRename!!.size)
    val itAfterRename = myRevisionsAfterRename!!.iterator()
    for (pair in history) {
      val revision = itAfterRename.next()
      TestCase.assertEquals(pair.first.toString(), revision.myHash)
      TestCase.assertEquals(pair.second, revision.myDate)
    }
  }

  @Throws(VcsException::class)
  private fun assertHistory(actualRevisions: List<VcsFileRevision>) {
    TestCase.assertEquals("Incorrect number of commits in history", myRevisions!!.size, actualRevisions.size)
    for (i in actualRevisions.indices) {
      assertEqualRevisions(actualRevisions[i] as GitFileRevision, myRevisions!![i])
    }
  }

  @Throws(VcsException::class)
  private fun assertEqualRevisions(actual: GitFileRevision, expected: GitTestRevision) {
    val actualRev = (actual.revisionNumber as GitRevisionNumber).rev
    TestCase.assertEquals(expected.myHash, actualRev)
    TestCase.assertEquals(expected.myDate, (actual.revisionNumber as GitRevisionNumber).timestamp)
    // TODO: whitespaces problem is known, remove convertWhitespaces... when it's fixed
    TestCase.assertEquals(convertWhitespacesToSpacesAndRemoveDoubles(expected.myCommitMessage),
                          convertWhitespacesToSpacesAndRemoveDoubles(actual.commitMessage!!))
    TestCase.assertEquals(expected.myAuthorName, actual.author)
    TestCase.assertEquals(expected.myBranchName, actual.branchName)
    TestCase.assertNotNull("No content in revision " + actualRev, actual.content)
    TestCase.assertEquals(String(expected.myContent), String(actual.content!!))
  }

  private fun convertWhitespacesToSpacesAndRemoveDoubles(s: String): String {
    return s.replace("[\\s^ ]".toRegex(), " ").replace(" +".toRegex(), " ")
  }

  private class GitTestRevision(internal val myHash: String,
                                gitTimestamp: String,
                                internal val myCommitMessage: String,
                                internal val myAuthorName: String,
                                internal val myAuthorEmail: String,
                                internal val myCommitterName: String,
                                internal val myCommitterEmail: String,
                                internal val myBranchName: String?,
                                content: String) {
    internal val myDate: Date
    internal val myContent: ByteArray

    init {
      myDate = gitTimeStampToDate(gitTimestamp)
      myContent = content.toByteArray()
    }

    override fun toString(): String {
      return myHash
    }

    companion object {
      fun gitTimeStampToDate(gitTimestamp: String): Date {
        return Date(java.lang.Long.parseLong(gitTimestamp) * 1000)
      }
    }
  }
}

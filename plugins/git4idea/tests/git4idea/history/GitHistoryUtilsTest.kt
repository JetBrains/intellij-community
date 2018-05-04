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

import com.intellij.openapi.vcs.Executor.*
import com.intellij.vcsUtil.VcsUtil.getFilePath
import git4idea.GitRevisionNumber
import git4idea.test.*
import junit.framework.TestCase
import java.io.File
import java.util.*

/**
 * Tests for low-level history methods in GitHistoryUtils.
 * There are some known problems with newlines and whitespaces in commit messages, these are ignored by the tests for now.
 * (see #convertWhitespacesToSpacesAndRemoveDoubles).
 */
class GitHistoryUtilsTest : GitSingleRepoTest() {
  private lateinit var afile: File
  private lateinit var bfile: File
  private lateinit var revisions: MutableList<GitTestRevision>

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()

    revisions = ArrayList(7)

    // initial
    afile = touch("a.txt", "initial content")
    var hash = repo.addCommit("initial commit")
    revisions.add(GitTestRevision(hash, timeStampToDate(repo.lastAuthorTime()), afile))

    // modify
    overwrite(afile, "second content")
    hash = repo.addCommit("simple commit")
    revisions.add(GitTestRevision(hash, timeStampToDate(repo.lastAuthorTime()), afile))

    // mv to dir
    val dir = mkdir("dir")
    bfile = File(dir.path, "b.txt")
    TestCase.assertFalse("File $bfile shouldn't have existed", bfile.exists())
    repo.mv(afile, bfile)
    TestCase.assertTrue("File $bfile was not created by mv command", bfile.exists())
    hash = repo.commit("moved a.txt to dir/b.txt")
    revisions.add(GitTestRevision(hash, timeStampToDate(repo.lastAuthorTime()), bfile))


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
      revisions.add(GitTestRevision(hash, timeStampToDate(repo.lastAuthorTime()), bfile))
    }

    revisions.reverse()

    cd(projectPath)
    updateChangeListManager()
  }

  override fun makeInitialCommit(): Boolean {
    return false
  }

  @Throws(Exception::class)
  fun testGetCurrentRevision() {
    val revisionNumber = GitHistoryUtils.getCurrentRevision(myProject, getFilePath(bfile), null) as GitRevisionNumber?
    TestCase.assertEquals(revisionNumber!!.rev, revisions[0].hash)
    TestCase.assertEquals(revisionNumber.timestamp, revisions[0].date)
  }

  @Throws(Exception::class)
  fun testGetCurrentRevisionInMasterBranch() {
    val revisionNumber = GitHistoryUtils.getCurrentRevision(myProject, getFilePath(bfile), "master") as GitRevisionNumber?
    TestCase.assertEquals(revisionNumber!!.rev, revisions[0].hash)
    TestCase.assertEquals(revisionNumber.timestamp, revisions[0].date)
  }

  @Throws(Exception::class)
  fun testGetCurrentRevisionInOtherBranch() {
    repo.checkout("-b feature")
    overwrite(bfile, "new content")
    repo.addCommit("new content")
    val output = repo.log("master --pretty=%H#%at", "-n1").trim { it <= ' ' }.split(
      "#".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

    val revisionNumber = GitHistoryUtils.getCurrentRevision(myProject, getFilePath(bfile), "master") as GitRevisionNumber?
    TestCase.assertEquals(revisionNumber!!.rev, output[0])
    TestCase.assertEquals(revisionNumber.timestamp, timeStampToDate(output[1]))
  }

  @Throws(Exception::class)
  fun testGetLastRevisionForExistingFile() {
    val state = GitHistoryUtils.getLastRevision(myProject, getFilePath(bfile))
    TestCase.assertTrue(state!!.isItemExists)
    val revisionNumber = state.number as GitRevisionNumber
    TestCase.assertEquals(revisionNumber.rev, revisions[0].hash)
    TestCase.assertEquals(revisionNumber.timestamp, revisions[0].date)
  }

  @Throws(Exception::class)
  fun testGetLastRevisionForNonExistingFile() {
    git("remote add origin git://example.com/repo.git")
    git("config branch.master.remote origin")
    git("config branch.master.merge refs/heads/master")

    git("rm " + bfile.path)
    repo.commit("removed bfile")
    val hashAndDate = repo.log("--pretty=format:%H#%ct", "-n1").split("#".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
    git("update-ref refs/remotes/origin/master HEAD") // to avoid pushing to this fake origin

    touch("dir/b.txt", "content")
    repo.addCommit("recreated bfile")

    refresh()
    repo.update()

    val state = GitHistoryUtils.getLastRevision(myProject, getFilePath(bfile))
    TestCase.assertTrue(!state!!.isItemExists)
    val revisionNumber = state.number as GitRevisionNumber
    TestCase.assertEquals(revisionNumber.rev, hashAndDate[0])
    TestCase.assertEquals(revisionNumber.timestamp, timeStampToDate(hashAndDate[1]))
  }

  @Throws(Exception::class)
  fun testOnlyHashesHistory() {
    val history = GitHistoryUtils.onlyHashesHistory(myProject, getFilePath(bfile), projectRoot)
    val revisionsAfterRename = revisions.filter { it.file == bfile }
    TestCase.assertEquals(history.size, revisionsAfterRename.size)
    val itAfterRename = revisionsAfterRename.iterator()
    for (pair in history) {
      val revision = itAfterRename.next()
      TestCase.assertEquals(pair.first.toString(), revision.hash)
      TestCase.assertEquals(pair.second, revision.date)
    }
  }

  private fun timeStampToDate(timestamp: String): Date {
    return Date(java.lang.Long.parseLong(timestamp) * 1000)
  }

  private class GitTestRevision(internal val hash: String, internal val date: Date, internal val file: File) {
    override fun toString(): String {
      return hash
    }
  }
}

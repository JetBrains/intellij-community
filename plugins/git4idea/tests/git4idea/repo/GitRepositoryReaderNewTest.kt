/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package git4idea.repo

import com.intellij.dvcs.repo.Repository.State
import com.intellij.openapi.util.SystemInfo
import git4idea.GitLocalBranch
import git4idea.branch.GitBranchUtil
import git4idea.test.GitScenarios.conflict
import git4idea.test.GitSingleRepoTest
import git4idea.test.last
import git4idea.test.makeCommit
import git4idea.test.tac
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.assertNotEquals

/**
 * [GitRepositoryReaderTest] reads information from the pre-created .git directory from a real project.
 * This one, on the other hand, operates on a live Git repository, putting it to various situations and checking the results.
 */
class GitRepositoryReaderNewTest : GitSingleRepoTest() {

  override fun makeInitialCommit() = false

  // IDEA-152632
  fun `test current branch is known during rebase`() {
    makeCommit("file.txt")
    conflict(repo, "feature")
    git("checkout feature")
    git("rebase master", true)

    val state = readState()
    assertEquals("State value is incorrect", State.REBASING, state.state)
    val currentBranch = state.currentBranch
    assertNotNull("Current branch should be known during rebase", currentBranch)
    assertEquals("Current branch is incorrect", "feature", currentBranch!!.name)
  }

  fun `test rebase with conflicts while being on detached HEAD`() {
    makeCommit("file.txt")
    conflict(repo, "feature")
    makeCommit("file2.txt")
    git("checkout HEAD^")
    git("rebase feature", true)

    val state = readState()
    assertNull("Current branch can't be identified for this case", state.currentBranch)
    assertEquals("State value is incorrect", State.REBASING, state.state)
  }

  // IDEA-124052
  fun `test remote reference without remote`() {
    makeCommit("file.txt")
    val INVALID_REMOTE = "invalid-remote"
    val INVALID_REMOTE_BRANCH = "master"
    git("update-ref refs/remotes/$INVALID_REMOTE/$INVALID_REMOTE_BRANCH HEAD")

    val remoteBranches = readState().remoteBranches.keys
    assertTrue("Remote branch not found", remoteBranches.any { it.nameForLocalOperations == "$INVALID_REMOTE/$INVALID_REMOTE_BRANCH" })
  }

  // IDEA-134286
  fun `test detached HEAD`() {
    val head = moveToDetachedHead()
    val state = readState()
    assertEquals("Detached HEAD is not detected", State.DETACHED, state.state)
    assertEquals("Detached HEAD hash is incorrect", head, state.currentRevision)
  }

  // IDEA-135966
  fun `test no local branches`() {
    val head = moveToDetachedHead()
    git("branch -D master")
    val state = readState()
    assertEquals("Detached HEAD is not detected", State.DETACHED, state.state)
    assertEquals("Detached HEAD hash is incorrect", head, state.currentRevision)
    assertTrue("There should be no local branches", state.localBranches.isEmpty())
  }

  fun `test tracking remote with complex name`() {
    makeCommit("file.txt")
    git("remote add my/remote http://my.remote.git")
    git("update-ref refs/remotes/my/remote/master HEAD")
    git("config branch.master.remote my/remote")
    git("config branch.master.merge refs/heads/master")
    repo.update()

    val trackInfo = GitBranchUtil.getTrackInfoForBranch(repo, repo.currentBranch!!)!!
    val remote = trackInfo.remote
    assertEquals("my/remote", remote.name)
    assertEquals("http://my.remote.git", remote.firstUrl)
  }

  // IDEA-134412
  fun `test fresh repository is on branch`() {
    val currentBranch = readState().currentBranch
    assertNotNull("Current branch shouldn't be null in a fresh repository", currentBranch)
    assertEquals("Fresh repository should be on master", "master", currentBranch!!.name)
  }

  // IDEA-101222
  fun `test non-ascii current branch name`() {
    makeCommit("file.txt")
    val branch = "teslá"
    git("checkout -b $branch")
    val state = readState()
    assertEquals(branch, state.currentBranch!!.name)
  }

  // IDEA-143791
  fun `test branches are case-insensitive on case-insensitive systems`() {
    assumeTrue(!SystemInfo.isFileSystemCaseSensitive)

    makeCommit("file.txt")
    git("branch UpperCase")
    git("checkout uppercase")

    repo.update()
    assertEquals("UpperCase", repo.currentBranchName)
    assertEquals(repo.branches.findBranchByName("UpperCase"), repo.branches.findBranchByName("uppercase"))
    assertEquals(GitLocalBranch("UpperCase"), GitLocalBranch("uppercase"))
  }

  fun `test branches are case-sensitive on case-sensitive systems`() {
    assumeTrue("Not tested: this test is for case sensitive FS only", SystemInfo.isFileSystemCaseSensitive)

    makeCommit("file.txt")
    git("branch uppercase")
    git("branch UpperCase") // doesn't fail on case-sensitive OS: new branch is created
    git("checkout UpperCase")

    repo.update()
    assertEquals("UpperCase", repo.currentBranchName)
    assertEquals(3, repo.branches.localBranches.size)
    assertNotEquals(repo.branches.findBranchByName("uppercase"), repo.branches.findBranchByName("UpperCase"))
    assertNotEquals(GitLocalBranch("UpperCase"), GitLocalBranch("uppercase"))
  }

  fun `test non-branch files are ignored`() {
    tac("f.txt")
    assertTrue(File(repo.repositoryFiles.refsHeadsFile, "master.lock").createNewFile())

    repo.update()
    assertSameElements(listOf("master"), repo.branches.localBranches.map { it.name })
  }

  private fun moveToDetachedHead(): String {
    makeCommit("file.txt")
    makeCommit("file.txt")
    git("checkout HEAD^")
    return last()
  }

  private fun readState(): GitBranchState {
    val gitFiles = repo.repositoryFiles
    val config = GitConfig.read(gitFiles.configFile)
    val reader = GitRepositoryReader(gitFiles)
    val remotes = config.parseRemotes()
    return reader.readState(remotes)
  }
}

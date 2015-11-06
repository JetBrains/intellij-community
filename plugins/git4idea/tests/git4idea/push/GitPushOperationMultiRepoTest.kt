/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package git4idea.push

import com.intellij.dvcs.push.PushSpec
import com.intellij.openapi.vcs.Executor
import com.intellij.util.containers.ContainerUtil
import git4idea.commands.GitCommandResult
import git4idea.repo.GitRepository
import git4idea.test.GitExecutor.*
import git4idea.test.GitTestUtil
import git4idea.update.GitUpdateResult
import java.io.File
import java.util.*

class GitPushOperationMultiRepoTest : GitPushOperationBaseTest() {

  private lateinit var myCommunity: GitRepository
  private lateinit var myRepository: GitRepository

  private lateinit var myBro: File
  private lateinit var myBroCommunity: File

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()

    val mainRepo = setupRepositories(myProjectPath, "parent", "bro")
    myRepository = mainRepo.first
    myBro = mainRepo.third

    val community = File(myProjectPath, "community")
    assertTrue(community.mkdir())
    val enclosingRepo = setupRepositories(community.path,
        "community_parent", "community_bro")
    myCommunity = enclosingRepo.first
    myBroCommunity = enclosingRepo.third

    Executor.cd(myProjectPath)
    refresh()
  }

  fun test_try_push_from_all_roots_even_if_one_fails() {
    // fail in the first repo
    myGit.onPush {
      if (it == myRepository) GitCommandResult(false, 128, listOf("Failed to push to origin"), listOf<String>(), null)
      else null
    }

    cd(myRepository)
    GitTestUtil.makeCommit("file.txt")
    cd(myCommunity)
    GitTestUtil.makeCommit("com.txt")

    val spec1 = makePushSpec(myRepository, "master", "origin/master")
    val spec2 = makePushSpec(myCommunity, "master", "origin/master")
    val map = ContainerUtil.newHashMap<GitRepository, PushSpec<GitPushSource, GitPushTarget>>()
    map.put(myRepository, spec1)
    map.put(myCommunity, spec2)
    val result = GitPushOperation(myProject, myPushSupport, map, null, false).execute()

    val result1 = result.results[myRepository]!!
    val result2 = result.results[myCommunity]!!

    assertResult(GitPushRepoResult.Type.ERROR, -1, "master", "origin/master", null, result1)
    assertEquals("Error text is incorrect", "Failed to push to origin", result1.error)
    assertResult(GitPushRepoResult.Type.SUCCESS, 1, "master", "origin/master", null, result2)
  }

  fun test_update_all_roots_on_reject_when_needed_even_if_only_one_in_push_spec() {
    Executor.cd(myBro)
    val broHash = GitTestUtil.makeCommit("bro.txt")
    git("push")
    Executor.cd(myBroCommunity)
    val broCommunityHash = GitTestUtil.makeCommit("bro_com.txt")
    git("push")

    cd(myRepository)
    GitTestUtil.makeCommit("file.txt")

    val mainSpec = makePushSpec(myRepository, "master", "origin/master")
    agreeToUpdate(GitRejectedPushUpdateDialog.MERGE_EXIT_CODE) // auto-update-all-roots is selected by default
    val result = GitPushOperation(myProject, myPushSupport,
        Collections.singletonMap<GitRepository, PushSpec<GitPushSource, GitPushTarget>>(myRepository, mainSpec), null, false).execute()

    val result1 = result.results[myRepository]!!
    val result2 = result.results[myCommunity]

    assertResult(GitPushRepoResult.Type.SUCCESS, 2, "master", "origin/master", GitUpdateResult.SUCCESS, result1)
    assertNull(result2) // this was not pushed => no result should be generated

    cd(myCommunity)
    val lastHash = last()
    assertEquals("Update in community didn't happen", broCommunityHash, lastHash)

    cd(myRepository)
    val lastCommitParents = git("log -1 --pretty=%P").split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
    assertEquals("Merge didn't happen in main repository", 2, lastCommitParents.size)
    assertEquals("Commit from bro repository didn't arrive", broHash, git("log --no-walk HEAD^2 --pretty=%H"))
  }

}

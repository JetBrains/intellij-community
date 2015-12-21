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

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.Executor
import com.intellij.openapi.vcs.update.FileGroup
import com.intellij.openapi.vcs.update.UpdatedFiles
import com.intellij.testFramework.UsefulTestCase
import com.intellij.util.Function
import com.intellij.util.containers.ContainerUtil
import git4idea.branch.GitBranchUtil
import git4idea.config.UpdateMethod
import git4idea.push.GitPushRepoResult.Type.*
import git4idea.repo.GitRepository
import git4idea.test.GitExecutor.*
import git4idea.test.GitTestUtil.makeCommit
import git4idea.update.GitRebaseOverMergeProblem
import git4idea.update.GitUpdateResult
import java.io.File
import java.io.IOException
import java.util.Collections.singletonMap
import javax.swing.Action

@SuppressWarnings("StringToUpperCaseOrToLowerCaseWithoutLocale")
class GitPushOperationSingleRepoTest : GitPushOperationBaseTest() {

  protected lateinit var myRepository: GitRepository
  protected lateinit var myParentRepo: File
  protected lateinit var myBroRepo: File

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()

    val trinity = setupRepositories(myProjectPath, "parent", "bro")
    myParentRepo = trinity.second
    myBroRepo = trinity.third
    myRepository = trinity.first

    Executor.cd(myProjectPath)
    refresh()
  }

  fun test_successful_push() {
    val hash = makeCommit("file.txt")
    val result = push("master", "origin/master")

    assertResult(SUCCESS, 1, "master", "origin/master", result)
    assertPushed(hash, "master")
  }

  fun test_push_new_branch() {
    git("checkout -b feature")
    val result = push("feature", "origin/feature")

    assertResult(NEW_BRANCH, -1, "feature", "origin/feature", result)
    assertBranchExists("feature")
  }

  fun test_push_new_branch_with_commits() {
    Executor.touch("feature.txt", "content")
    addCommit("feature commit")
    val hash = last()
    git("checkout -b feature")
    val result = push("feature", "origin/feature")

    assertResult(NEW_BRANCH, -1, "feature", "origin/feature", result)
    assertBranchExists("feature")
    assertPushed(hash, "feature")
  }

  fun test_upstream_is_set_for_new_branch() {
    git("checkout -b feature")
    push("feature", "origin/feature")
    assertUpstream("feature", "origin", "feature")
  }

  fun test_upstream_is_not_modified_if_already_set() {
    push("master", "origin/feature")
    assertUpstream("master", "origin", "master")
  }

  fun test_rejected_push_to_tracked_branch_proposes_to_update() {
    pushCommitFromBro()

    var dialogShown = false
    myDialogManager.onDialog(GitRejectedPushUpdateDialog::class.java, {
      dialogShown = true
      DialogWrapper.CANCEL_EXIT_CODE
    })

    val result = push("master", "origin/master")

    assertTrue("Rejected push dialog wasn't shown", dialogShown)
    assertResult(REJECTED_NO_FF, -1, "master", "origin/master", result)
  }

  fun test_rejected_push_to_other_branch_doesnt_propose_to_update() {
    pushCommitFromBro()
    cd(myRepository)
    git("checkout -b feature")

    var dialogShown = false
    myDialogManager.onDialog(GitRejectedPushUpdateDialog::class.java, {
      dialogShown = true
      DialogWrapper.CANCEL_EXIT_CODE
    })

    val result = push("feature", "origin/master")

    assertFalse("Rejected push dialog shouldn't be shown", dialogShown)
    assertResult(REJECTED_NO_FF, -1, "feature", "origin/master", result)
  }

  fun test_push_is_rejected_too_many_times() {
    pushCommitFromBro()
    cd(myRepository)
    val hash = makeCommit("afile.txt")

    agreeToUpdate(GitRejectedPushUpdateDialog.MERGE_EXIT_CODE)

    refresh()
    val pushSpec = makePushSpec(myRepository, "master", "origin/master")

    val result = object : GitPushOperation(myProject, myPushSupport, singletonMap(myRepository, pushSpec), null, false) {
      override fun update(rootsToUpdate: Collection<GitRepository>,
                          updateMethod: UpdateMethod,
                          checkForRebaseOverMergeProblem: Boolean): GitUpdateResult {
        val updateResult = super.update(rootsToUpdate, updateMethod, checkForRebaseOverMergeProblem)
        try {
          pushCommitFromBro()
        }
        catch (e: IOException) {
          throw RuntimeException(e)
        }

        return updateResult
      }
    }.execute()
    assertResult(REJECTED_NO_FF, -1, "master", "origin/master", GitUpdateResult.SUCCESS, listOf("bro.txt"), result)

    Executor.cd(myParentRepo.path)
    val history = git("log --all --pretty=%H ")
    assertFalse("The commit shouldn't be pushed", history.contains(hash))
  }

  fun test_use_selected_update_method_for_all_consecutive_updates() {
    pushCommitFromBro()
    cd(myRepository)
    makeCommit("afile.txt")

    agreeToUpdate(GitRejectedPushUpdateDialog.REBASE_EXIT_CODE)

    refresh()
    val pushSpec = makePushSpec(myRepository, "master", "origin/master")

    val result = object : GitPushOperation(myProject, myPushSupport, singletonMap(myRepository, pushSpec), null, false) {
      internal var updateHappened: Boolean = false

      override fun update(rootsToUpdate: Collection<GitRepository>,
                          updateMethod: UpdateMethod,
                          checkForRebaseOverMergeProblem: Boolean): GitUpdateResult {
        val updateResult = super.update(rootsToUpdate, updateMethod, checkForRebaseOverMergeProblem)
        try {
          if (!updateHappened) {
            updateHappened = true
            pushCommitFromBro()
          }
        }
        catch (e: IOException) {
          throw RuntimeException(e)
        }

        return updateResult
      }
    }.execute()

    assertResult(SUCCESS, 1, "master", "origin/master", GitUpdateResult.SUCCESS, result.results[myRepository]!!)
    cd(myRepository)
    val commitMessages = StringUtil.splitByLines(log("--pretty=%s"))
    val mergeCommitsInTheLog = ContainerUtil.exists(commitMessages, object : Condition<String> {
      override fun value(s: String): Boolean {
        return s.toLowerCase().contains("merge")
      }
    })
    assertFalse("Unexpected merge commits when rebase method is selected", mergeCommitsInTheLog)
  }

  fun test_force_push() {
    val lostHash = pushCommitFromBro()
    cd(myRepository)
    val hash = makeCommit("anyfile.txt")

    val result = push("master", "origin/master", true)

    assertResult(FORCED, -1, "master", "origin/master", result)

    Executor.cd(myParentRepo.path)
    val history = git("log --all --pretty=%H ")
    assertFalse(history.contains(lostHash))
    assertEquals(hash, StringUtil.splitByLines(history)[0])
  }

  fun test_dont_propose_to_update_if_force_push_is_rejected() {
    var dialogShown = false
    myDialogManager.onDialog(GitRejectedPushUpdateDialog::class.java, {
      dialogShown = true
      DialogWrapper.CANCEL_EXIT_CODE
    })

    val remoteTipAndPushResult = forcePushWithReject()
    assertResult(REJECTED_NO_FF, -1, "master", "origin/master", remoteTipAndPushResult.second)
    assertFalse("Rejected push dialog should not be shown", dialogShown)
    Executor.cd(myParentRepo.path)
    assertEquals("The commit pushed from bro should be the last one", remoteTipAndPushResult.first, last())
  }

  fun test_dont_silently_update_if_force_push_is_rejected() {
    myGitSettings.updateType = UpdateMethod.REBASE
    myGitSettings.setAutoUpdateIfPushRejected(true)

    val remoteTipAndPushResult = forcePushWithReject()

    assertResult(REJECTED_NO_FF, -1, "master", "origin/master", remoteTipAndPushResult.second)
    Executor.cd(myParentRepo.path)
    assertEquals("The commit pushed from bro should be the last one", remoteTipAndPushResult.first, last())
  }

  private fun forcePushWithReject(): Pair<String, GitPushResult> {
    val pushedHash = pushCommitFromBro()
    Executor.cd(myParentRepo)
    git("config receive.denyNonFastForwards true")
    cd(myRepository)
    makeCommit("anyfile.txt")

    val map = singletonMap(myRepository, makePushSpec(myRepository, "master", "origin/master"))
    val result = GitPushOperation(myProject, myPushSupport, map, null, true).execute()
    return Pair.create(pushedHash, result)
  }

  fun test_merge_after_rejected_push() {
    val broHash = pushCommitFromBro()
    cd(myRepository)
    val hash = makeCommit("file.txt")

    agreeToUpdate(GitRejectedPushUpdateDialog.MERGE_EXIT_CODE)

    val result = push("master", "origin/master")

    cd(myRepository)
    val log = git("log -3 --pretty=%H#%s")
    val commits = StringUtil.splitByLines(log)
    val lastCommitMsg = commits[0].split("#".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1]
    assertTrue("The last commit doesn't look like a merge commit: " + lastCommitMsg, lastCommitMsg.contains("Merge"))
    assertEquals(hash, commits[1].split("#".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0])
    assertEquals(broHash, commits[2].split("#".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0])

    assertResult(SUCCESS, 2, "master", "origin/master", GitUpdateResult.SUCCESS, listOf("bro.txt"), result)
  }

  // IDEA-144179
  fun `test don't update if rejected by some custom reason`() {
    cd(myRepository)
    val hash = makeCommit("file.txt")

    val rejectHook = """
      cat <<'EOF'
      remote: Push rejected.
      remote: refs/heads/master: 53d02a63c9cd5c919091b5d9f21381b98a8341be: commit message doesn't match regex: [A-Z][A-Z_0-9]+-[A-Za-z0-9].*
      remote:
      EOF
      exit 1
      """.trimIndent()
    installHook(myParentRepo, "pre-receive", rejectHook)

    myDialogManager.onDialog(GitRejectedPushUpdateDialog::class.java) {
      throw AssertionError("Update shouldn't be proposed")
    }

    val result = push("master", "origin/master")

    assertResult(REJECTED_OTHER, -1, "master", "origin/master", result)
    assertNotPushed(hash)
  }

  private fun assertNotPushed(hash: String) {
    assertEquals("", git("branch -r --contains $hash"))
  }

  fun test_update_with_conflicts_cancels_push() {
    Executor.cd(myBroRepo.path)
    Executor.append("bro.txt", "bro content")
    makeCommit("msg")
    git("push origin master:master")

    cd(myRepository)
    Executor.append("bro.txt", "main content")
    makeCommit("msg")

    agreeToUpdate(GitRejectedPushUpdateDialog.REBASE_EXIT_CODE)
    myVcsHelper.onMerge {}

    val result = push("master", "origin/master")
    assertResult(REJECTED_NO_FF, -1, "master", "origin/master", GitUpdateResult.INCOMPLETE, listOf("bro.txt"), result)
  }

  fun test_push_tags() {
    cd(myRepository)
    git("tag v1")

    refresh()
    val spec = makePushSpec(myRepository, "master", "origin/master")
    val pushResult = GitPushOperation(myProject, myPushSupport, singletonMap(myRepository, spec),
        GitPushTagMode.ALL, false).execute()
    val result = pushResult.results[myRepository]!!
    val pushedTags = result.pushedTags
    assertEquals(1, pushedTags.size)
    assertEquals("refs/tags/v1", pushedTags[0])
  }

  fun test_warn_if_rebasing_over_merge() {
    generateUnpushedMergedCommitProblem()

    var rebaseOverMergeProblemDetected = false
    myDialogManager.onDialog(GitRejectedPushUpdateDialog::class.java, {
      rebaseOverMergeProblemDetected = it.warnsAboutRebaseOverMerge()
      DialogWrapper.CANCEL_EXIT_CODE
    })
    push("master", "origin/master")
    assertTrue(rebaseOverMergeProblemDetected)
  }

  fun test_warn_if_silently_rebasing_over_merge() {
    generateUnpushedMergedCommitProblem()

    myGitSettings.setAutoUpdateIfPushRejected(true)
    myGitSettings.updateType = UpdateMethod.REBASE

    var rebaseOverMergeProblemDetected = false
    myDialogManager.onMessage {
      rebaseOverMergeProblemDetected = it.contains(GitRebaseOverMergeProblem.DESCRIPTION)
      Messages.CANCEL
    }
    push("master", "origin/master")
    assertTrue(rebaseOverMergeProblemDetected)
  }

  fun test_dont_overwrite_rebase_setting_when_chose_to_merge_due_to_unpushed_merge_commits() {
    generateUnpushedMergedCommitProblem()

    myGitSettings.updateType = UpdateMethod.REBASE

    var rebaseOverMergeProblemDetected = false
    myDialogManager.onDialog(GitRejectedPushUpdateDialog::class.java, {
      rebaseOverMergeProblemDetected = it.warnsAboutRebaseOverMerge()
      GitRejectedPushUpdateDialog.MERGE_EXIT_CODE
    })
    push("master", "origin/master")
    assertTrue(rebaseOverMergeProblemDetected)
    assertEquals("Update method was overwritten by temporary update-via-merge decision",
        UpdateMethod.REBASE, myGitSettings.updateType)
  }

  fun test_respect_branch_default_setting_for_rejected_push_dialog() {
    generateUpdateNeeded()
    myGitSettings.updateType = UpdateMethod.BRANCH_DEFAULT
    git("config branch.master.rebase true")

    var defaultActionName = ""
    myDialogManager.onDialog(GitRejectedPushUpdateDialog::class.java, {
      defaultActionName = it.defaultAction.getValue(Action.NAME) as String
      DialogWrapper.CANCEL_EXIT_CODE
    })

    push("master", "origin/master")
    assertTrue("Default action in rejected-push dialog is incorrect: " + defaultActionName,
        defaultActionName.toLowerCase().contains("rebase"))

    git("config branch.master.rebase false")
    push("master", "origin/master")
    assertTrue("Default action in rejected-push dialog is incorrect: " + defaultActionName,
        defaultActionName.toLowerCase().contains("merge"))
  }

  fun test_respect_branch_default_setting_for_silent_update_when_rejected_push() {
    generateUpdateNeeded()
    myGitSettings.updateType = UpdateMethod.BRANCH_DEFAULT
    git("config branch.master.rebase true")
    myGitSettings.setAutoUpdateIfPushRejected(true)

    push("master", "origin/master")
    assertFalse("Unexpected merge commit: rebase should have happened", log("-1 --pretty=%s").toLowerCase().startsWith("merge"))
  }

  // there is no "branch default" choice in the rejected push dialog
  // => simply don't rewrite the setting if the same value is chosen, as was default value initially
  fun test_dont_overwrite_branch_default_setting_when_agree_in_rejected_push_dialog() {
    generateUpdateNeeded()
    myGitSettings.updateType = UpdateMethod.BRANCH_DEFAULT
    git("config branch.master.rebase true")

    myDialogManager.onDialog(GitRejectedPushUpdateDialog::class.java, {
      GitRejectedPushUpdateDialog.REBASE_EXIT_CODE
    })

    push("master", "origin/master")
    assertEquals(UpdateMethod.BRANCH_DEFAULT, myGitSettings.updateType)
  }

  private fun generateUpdateNeeded() {
    pushCommitFromBro()
    cd(myRepository)
    makeCommit("file.txt")
  }

  private fun generateUnpushedMergedCommitProblem() {
    pushCommitFromBro()
    cd(myRepository)
    git("checkout -b branch1")
    makeCommit("branch1.txt")
    git("checkout master")
    makeCommit("master.txt")
    git("merge branch1")
  }

  private fun push(from: String, to: String, force: Boolean = false): GitPushResult {
    refresh()
    val spec = makePushSpec(myRepository, from, to)
    return GitPushOperation(myProject, myPushSupport, singletonMap(myRepository, spec), null, force).execute()
  }

  private fun pushCommitFromBro(): String {
    Executor.cd(myBroRepo.path)
    val hash = makeCommit("bro.txt")
    git("push")
    return hash
  }

  private fun assertResult(type: GitPushRepoResult.Type, pushedCommits: Int, from: String, to: String, actualResult: GitPushResult) {
    assertResult(type, pushedCommits, from, to, null, null, actualResult)
  }

  private fun assertResult(type: GitPushRepoResult.Type, pushedCommits: Int, from: String, to: String,
                           updateResult: GitUpdateResult?,
                           updatedFiles: List<String>?,
                           actualResult: GitPushResult) {
    assertResult(type, pushedCommits, from, to, updateResult, actualResult.results[myRepository]!!)
    UsefulTestCase.assertSameElements("Updated files set is incorrect",
        getUpdatedFiles(actualResult.updatedFiles), ContainerUtil.notNullize(updatedFiles))
  }

  private fun getUpdatedFiles(updatedFiles: UpdatedFiles): Collection<String>? {
    val result = ContainerUtil.newArrayList<String>()
    for (group in updatedFiles.topLevelGroups) {
      result.addAll(getUpdatedFiles(group))
    }
    return result
  }

  private fun getUpdatedFiles(group: FileGroup): Collection<String> {
    val getRelative = object : Function<String, String> {
      override fun `fun`(path: String): String {
        return FileUtil.getRelativePath(File(myProjectPath), File(path))!!
      }
    }
    val result = ContainerUtil.newArrayList<String>()
    result.addAll(ContainerUtil.map(group.files, getRelative))
    for (child in group.children) {
      result.addAll(getUpdatedFiles(child))
    }
    return result
  }

  private fun assertPushed(expectedHash: String, branch: String) {
    Executor.cd(myParentRepo.path)
    val actualHash = git("log -1 --pretty=%H " + branch)
    assertEquals(expectedHash, actualHash)
  }

  private fun assertBranchExists(branch: String) {
    Executor.cd(myParentRepo.path)
    val out = git("branch")
    assertTrue(out.contains(branch))
  }

  private fun assertUpstream(localBranch: String,
                             expectedUpstreamRemote: String,
                             expectedUpstreamBranch: String) {
    val upstreamRemote = GitBranchUtil.stripRefsPrefix(git("config branch.$localBranch.remote"))
    val upstreamBranch = GitBranchUtil.stripRefsPrefix(git("config branch.$localBranch.merge"))
    assertEquals(expectedUpstreamRemote, upstreamRemote)
    assertEquals(expectedUpstreamBranch, upstreamBranch)
  }

}
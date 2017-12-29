/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.Executor
import com.intellij.openapi.vcs.update.FileGroup
import com.intellij.openapi.vcs.update.UpdatedFiles
import com.intellij.testFramework.UsefulTestCase
import com.intellij.util.containers.ContainerUtil
import git4idea.branch.GitBranchUtil
import git4idea.config.GitVersionSpecialty
import git4idea.config.UpdateMethod
import git4idea.push.GitPushRepoResult.Type.*
import git4idea.repo.GitRepository
import git4idea.test.*
import git4idea.update.GitRebaseOverMergeProblem
import git4idea.update.GitUpdateResult
import org.junit.Assume.assumeTrue
import java.io.File
import java.io.IOException
import java.util.Collections.singletonMap
import javax.swing.Action

@SuppressWarnings("StringToUpperCaseOrToLowerCaseWithoutLocale")
class GitPushOperationSingleRepoTest : GitPushOperationBaseTest() {

  private lateinit var repository: GitRepository
  private lateinit var parentRepo: File
  private lateinit var broRepo: File

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()

    val trinity = setupRepositories(projectPath, "parent", "bro")
    parentRepo = trinity.parent
    broRepo = trinity.bro
    repository = trinity.projectRepo

    Executor.cd(projectPath)
    refresh()
    updateRepositories()
  }

  fun `test successful push`() {
    val hash = makeCommit("file.txt")
    val result = push("master", "origin/master")

    assertResult(SUCCESS, 1, "master", "origin/master", result)
    assertPushed(hash, "master")
  }

  fun `test push new branch`() {
    git("checkout -b feature")
    val result = push("feature", "origin/feature")

    assertResult(NEW_BRANCH, -1, "feature", "origin/feature", result)
    assertBranchExists("feature")
  }

  fun `test push new branch with commits`() {
    Executor.touch("feature.txt", "content")
    addCommit("feature commit")
    val hash = last()
    git("checkout -b feature")
    val result = push("feature", "origin/feature")

    assertResult(NEW_BRANCH, -1, "feature", "origin/feature", result)
    assertBranchExists("feature")
    assertPushed(hash, "feature")
  }

  fun `test upstream is set for new branch`() {
    git("checkout -b feature")
    push("feature", "origin/feature")
    assertUpstream("feature", "origin", "feature")
  }

  fun `test upstream is not modified if already set`() {
    push("master", "origin/feature")
    assertUpstream("master", "origin", "master")
  }

  fun `test rejected push to tracked branch proposes to update`() {
    pushCommitFromBro()

    var dialogShown = false
    dialogManager.onDialog(GitRejectedPushUpdateDialog::class.java, {
      dialogShown = true
      DialogWrapper.CANCEL_EXIT_CODE
    })

    val result = push("master", "origin/master")

    assertTrue("Rejected push dialog wasn't shown", dialogShown)
    assertResult(REJECTED_NO_FF, -1, "master", "origin/master", result)
  }

  fun `test rejected push to other branch doesnt propose to update`() {
    pushCommitFromBro()
    cd(repository)
    git("checkout -b feature")

    var dialogShown = false
    dialogManager.onDialog(GitRejectedPushUpdateDialog::class.java, {
      dialogShown = true
      DialogWrapper.CANCEL_EXIT_CODE
    })

    val result = push("feature", "origin/master")

    assertFalse("Rejected push dialog shouldn't be shown", dialogShown)
    assertResult(REJECTED_NO_FF, -1, "feature", "origin/master", result)
  }

  fun `test push is rejected too many times`() {
    pushCommitFromBro()
    cd(repository)
    val hash = makeCommit("afile.txt")

    agreeToUpdate(GitRejectedPushUpdateDialog.MERGE_EXIT_CODE)

    updateRepositories()
    val pushSpec = makePushSpec(repository, "master", "origin/master")

    val result = object : GitPushOperation(project, pushSupport, singletonMap(repository, pushSpec), null, false, false) {
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

    Executor.cd(parentRepo.path)
    val history = git("log --all --pretty=%H ")
    assertFalse("The commit shouldn't be pushed", history.contains(hash))
  }

  fun `test use selected update method for all consecutive updates`() {
    pushCommitFromBro()
    cd(repository)
    makeCommit("afile.txt")

    agreeToUpdate(GitRejectedPushUpdateDialog.REBASE_EXIT_CODE)

    updateRepositories()
    val pushSpec = makePushSpec(repository, "master", "origin/master")

    val result = object : GitPushOperation(project, pushSupport, singletonMap(repository, pushSpec), null, false, false) {
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

    assertResult(SUCCESS, 1, "master", "origin/master", GitUpdateResult.SUCCESS, result.results[repository]!!)
    cd(repository)
    val commitMessages = StringUtil.splitByLines(log("--pretty=%s"))
    val mergeCommitsInTheLog = commitMessages.any { it.toLowerCase().contains("merge") }
    assertFalse("Unexpected merge commits when rebase method is selected", mergeCommitsInTheLog)
  }

  fun `test force push`() {
    val lostHash = pushCommitFromBro()
    cd(repository)
    val hash = makeCommit("anyfile.txt")

    val result = push("master", "origin/master", true)

    assertResult(FORCED, -1, "master", "origin/master", result)

    Executor.cd(parentRepo.path)
    val history = git("log --all --pretty=%H ")
    assertFalse(history.contains(lostHash))
    assertEquals(hash, StringUtil.splitByLines(history)[0])
  }

  fun `test dont propose to update if force push is rejected`() {
    var dialogShown = false
    dialogManager.onDialog(GitRejectedPushUpdateDialog::class.java, {
      dialogShown = true
      DialogWrapper.CANCEL_EXIT_CODE
    })

    val remoteTipAndPushResult = forcePushWithReject()
    assertResult(REJECTED_NO_FF, -1, "master", "origin/master", remoteTipAndPushResult.second)
    assertFalse("Rejected push dialog should not be shown", dialogShown)
    Executor.cd(parentRepo.path)
    assertEquals("The commit pushed from bro should be the last one", remoteTipAndPushResult.first, last())
  }

  fun `test dont silently update if force push is rejected`() {
    settings.updateType = UpdateMethod.REBASE
    settings.setAutoUpdateIfPushRejected(true)

    val remoteTipAndPushResult = forcePushWithReject()

    assertResult(REJECTED_NO_FF, -1, "master", "origin/master", remoteTipAndPushResult.second)
    Executor.cd(parentRepo.path)
    assertEquals("The commit pushed from bro should be the last one", remoteTipAndPushResult.first, last())
  }

  private fun forcePushWithReject(): Pair<String, GitPushResult> {
    val pushedHash = pushCommitFromBro()
    Executor.cd(parentRepo)
    git("config receive.denyNonFastForwards true")
    cd(repository)
    makeCommit("anyfile.txt")

    val map = singletonMap(repository, makePushSpec(repository, "master", "origin/master"))
    val result = GitPushOperation(project, pushSupport, map, null, true, false).execute()
    return Pair.create(pushedHash, result)
  }

  fun `test merge after rejected push`() {
    val broHash = pushCommitFromBro()
    cd(repository)
    val hash = makeCommit("file.txt")

    agreeToUpdate(GitRejectedPushUpdateDialog.MERGE_EXIT_CODE)

    val result = push("master", "origin/master")

    cd(repository)
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
    cd(repository)
    val hash = makeCommit("file.txt")

    val rejectHook = """
      cat <<'EOF'
      remote: Push rejected.
      remote: refs/heads/master: 53d02a63c9cd5c919091b5d9f21381b98a8341be: commit message doesn't match regex: [A-Z][A-Z_0-9]+-[A-Za-z0-9].*
      remote:
      EOF
      exit 1
      """.trimIndent()
    installHook(parentRepo, "pre-receive", rejectHook)

    dialogManager.onDialog(GitRejectedPushUpdateDialog::class.java) {
      throw AssertionError("Update shouldn't be proposed")
    }

    val result = push("master", "origin/master")

    assertResult(REJECTED_OTHER, -1, "master", "origin/master", result)
    assertNotPushed(hash)
  }

  private fun assertNotPushed(hash: String) {
    assertEquals("", git("branch -r --contains $hash"))
  }

  fun `test update with conflicts cancels push`() {
    Executor.cd(broRepo.path)
    Executor.append("bro.txt", "bro content")
    makeCommit("msg")
    git("push origin master:master")

    cd(repository)
    Executor.append("bro.txt", "main content")
    makeCommit("msg")

    agreeToUpdate(GitRejectedPushUpdateDialog.REBASE_EXIT_CODE)
    vcsHelper.onMerge {}

    val result = push("master", "origin/master")
    assertResult(REJECTED_NO_FF, -1, "master", "origin/master", GitUpdateResult.INCOMPLETE, listOf("bro.txt"), result)
  }

  fun `test push tags`() {
    cd(repository)
    git("tag v1")

    updateRepositories()
    val spec = makePushSpec(repository, "master", "origin/master")
    val pushResult = GitPushOperation(project, pushSupport, singletonMap(repository, spec),
                                      GitPushTagMode.ALL, false, false).execute()
    val result = pushResult.results[repository]!!
    val pushedTags = result.pushedTags
    assertEquals(1, pushedTags.size)
    assertEquals("refs/tags/v1", pushedTags[0])
  }

  fun `test skip pre push hook`() {
    assumeTrue("Not testing: pre-push hooks are not supported in ${vcs.version}", GitVersionSpecialty.PRE_PUSH_HOOK.existsIn(vcs.version))

    cd(repository)
    val hash = makeCommit("file.txt")

    val rejectHook = """
      exit 1
      """.trimIndent()
    installHook(File(repository.root.path, ".git"), "pre-push", rejectHook)

    val result = push("master", "origin/master", false, true)

    assertResult(SUCCESS, 1, "master", "origin/master", result)
    assertPushed(hash, "master")
  }

  fun `test warn if rebasing over merge`() {
    generateUnpushedMergedCommitProblem()

    var rebaseOverMergeProblemDetected = false
    dialogManager.onDialog(GitRejectedPushUpdateDialog::class.java, {
      rebaseOverMergeProblemDetected = it.warnsAboutRebaseOverMerge()
      DialogWrapper.CANCEL_EXIT_CODE
    })
    push("master", "origin/master")
    assertTrue(rebaseOverMergeProblemDetected)
  }

  fun `test warn if silently rebasing over merge`() {
    generateUnpushedMergedCommitProblem()

    settings.setAutoUpdateIfPushRejected(true)
    settings.updateType = UpdateMethod.REBASE

    var rebaseOverMergeProblemDetected = false
    dialogManager.onMessage {
      rebaseOverMergeProblemDetected = it.contains(GitRebaseOverMergeProblem.DESCRIPTION)
      Messages.CANCEL
    }
    push("master", "origin/master")
    assertTrue(rebaseOverMergeProblemDetected)
  }

  fun `test dont overwrite rebase setting when chose to merge due to unpushed merge commits`() {
    generateUnpushedMergedCommitProblem()

    settings.updateType = UpdateMethod.REBASE

    var rebaseOverMergeProblemDetected = false
    dialogManager.onDialog(GitRejectedPushUpdateDialog::class.java, {
      rebaseOverMergeProblemDetected = it.warnsAboutRebaseOverMerge()
      GitRejectedPushUpdateDialog.MERGE_EXIT_CODE
    })
    push("master", "origin/master")
    assertTrue(rebaseOverMergeProblemDetected)
    assertEquals("Update method was overwritten by temporary update-via-merge decision",
                 UpdateMethod.REBASE, settings.updateType)
  }

  fun `test respect branch default setting for rejected push dialog`() {
    generateUpdateNeeded()
    settings.updateType = UpdateMethod.BRANCH_DEFAULT
    git("config branch.master.rebase true")

    var defaultActionName = ""
    dialogManager.onDialog(GitRejectedPushUpdateDialog::class.java, {
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

  fun `test respect branch default setting for silent update when rejected push`() {
    generateUpdateNeeded()
    settings.updateType = UpdateMethod.BRANCH_DEFAULT
    git("config branch.master.rebase true")
    settings.setAutoUpdateIfPushRejected(true)

    push("master", "origin/master")
    assertFalse("Unexpected merge commit: rebase should have happened", log("-1 --pretty=%s").toLowerCase().startsWith("merge"))
  }

  // there is no "branch default" choice in the rejected push dialog
  // => simply don't rewrite the setting if the same value is chosen, as was default value initially
  fun `test dont overwrite branch default setting when agree in rejected push dialog`() {
    generateUpdateNeeded()
    settings.updateType = UpdateMethod.BRANCH_DEFAULT
    git("config branch.master.rebase true")

    dialogManager.onDialog(GitRejectedPushUpdateDialog::class.java, {
      GitRejectedPushUpdateDialog.REBASE_EXIT_CODE
    })

    push("master", "origin/master")
    assertEquals(UpdateMethod.BRANCH_DEFAULT, settings.updateType)
  }

  private fun generateUpdateNeeded() {
    pushCommitFromBro()
    cd(repository)
    makeCommit("file.txt")
  }

  private fun generateUnpushedMergedCommitProblem() {
    pushCommitFromBro()
    cd(repository)
    repository.prepareConflict("master", "feature", "branch1.txt")
    git("checkout master")
    git("merge feature", true)
    git("add -u .")
    git("commit -m 'merged with conflicts'")
  }

  private fun push(from: String, to: String, force: Boolean = false, skipHook: Boolean = false): GitPushResult {
    updateRepositories()
    val spec = makePushSpec(repository, from, to)
    return GitPushOperation(project, pushSupport, singletonMap(repository, spec), null, force, skipHook).execute()
  }

  private fun pushCommitFromBro(): String {
    Executor.cd(broRepo.path)
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
    assertResult(type, pushedCommits, from, to, updateResult, actualResult.results[repository]!!)
    UsefulTestCase.assertSameElements("Updated files set is incorrect",
        getUpdatedFiles(actualResult.updatedFiles), ContainerUtil.notNullize(updatedFiles))
  }

  private fun getUpdatedFiles(updatedFiles: UpdatedFiles): Collection<String> {
    val result = ContainerUtil.newArrayList<String>()
    for (group in updatedFiles.topLevelGroups) {
      result.addAll(getUpdatedFiles(group))
    }
    return result
  }

  private fun getUpdatedFiles(group: FileGroup): Collection<String> {
    val result = ContainerUtil.newArrayList<String>()
    result.addAll(group.files.map { FileUtil.getRelativePath(File(projectPath), File(it))!! })
    for (child in group.children) {
      result.addAll(getUpdatedFiles(child))
    }
    return result
  }

  private fun assertPushed(expectedHash: String, branch: String) {
    Executor.cd(parentRepo.path)
    val actualHash = git("log -1 --pretty=%H " + branch)
    assertEquals(expectedHash, actualHash)
  }

  private fun assertBranchExists(branch: String) {
    Executor.cd(parentRepo.path)
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
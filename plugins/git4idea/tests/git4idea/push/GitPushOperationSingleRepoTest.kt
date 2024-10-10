// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.push

import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.options.advanced.AdvancedSettingsImpl
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.Executor.*
import com.intellij.openapi.vcs.update.FileGroup
import com.intellij.openapi.vcs.update.UpdatedFiles
import com.intellij.testFramework.UsefulTestCase
import com.intellij.util.containers.ContainerUtil
import git4idea.GitTag
import git4idea.actions.tag.GitPushTagAction
import git4idea.branch.GitBranchUtil
import git4idea.config.GitVersionSpecialty
import git4idea.config.UpdateMethod
import git4idea.push.GitPushRepoResult.Type.*
import git4idea.push.GitRejectedPushUpdateDialog.Companion.PushRejectedExitCode
import git4idea.repo.GitRepository
import git4idea.test.*
import git4idea.update.GitUpdateResult
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import java.io.File
import java.nio.file.Path
import java.util.Collections.singletonMap

class GitPushOperationSingleRepoTest : GitPushOperationBaseTest() {
  private lateinit var repository: GitRepository
  private lateinit var parentRepo: Path
  private lateinit var broRepo: Path

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()

    val trinity = setupRepositories(projectPath, "parent", "bro")
    parentRepo = trinity.parent
    broRepo = trinity.bro
    repository = trinity.projectRepo

    cd(projectPath)
    refresh()
    updateRepositories()
  }

  @Before
  fun beforeEach() {
    TestDialogManager.setTestDialog(TestDialog.DEFAULT)
  }

  @After
  fun afterEach() {
    TestDialogManager.setTestDialog(TestDialog.DEFAULT)
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
    touch("feature.txt", "content")
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
    TestDialogManager.setTestDialog {
      dialogShown = true
      PushRejectedExitCode.CANCEL.exitCode
    }
    val result = push("master", "origin/master")

    assertTrue("Rejected push dialog wasn't shown", dialogShown)
    assertResult(REJECTED_NO_FF, -1, "master", "origin/master", result)
  }

  fun `test rejected push to other branch doesnt propose to update`() {
    pushCommitFromBro()
    cd(repository)
    git("checkout -b feature")

    var dialogShown = false
    TestDialogManager.setTestDialog {
      dialogShown = true
      PushRejectedExitCode.CANCEL.exitCode
    }

    val result = push("feature", "origin/master")

    assertFalse("Rejected push dialog shouldn't be shown", dialogShown)
    assertResult(REJECTED_NO_FF, -1, "feature", "origin/master", result)
  }

  fun `test push is rejected too many times`() {
    pushCommitFromBro()
    cd(repository)
    val hash = makeCommit("afile.txt")

    TestDialogManager.setTestDialog { PushRejectedExitCode.MERGE.exitCode }

    updateRepositories()
    val pushSpec = makePushSpec(repository, "master", "origin/master")

    val result = object : GitPushOperation(project, pushSupport, singletonMap(repository, pushSpec), null, false, false) {
      override fun update(rootsToUpdate: Collection<GitRepository>,
                          updateMethod: UpdateMethod,
                          checkForRebaseOverMergeProblem: Boolean): GitUpdateResult {
        val updateResult = super.update(rootsToUpdate, updateMethod, checkForRebaseOverMergeProblem)
        pushCommitFromBro()
        return updateResult
      }
    }.execute()
    assertResult(REJECTED_NO_FF, -1, "master", "origin/master", GitUpdateResult.SUCCESS, listOf("bro.txt"), result)

    cd(parentRepo)
    val history = git("log --all --pretty=%H ")
    assertFalse("The commit shouldn't be pushed", history.contains(hash))
  }

  fun `test use selected update method for all consecutive updates`() {
    pushCommitFromBro()
    cd(repository)
    makeCommit("afile.txt")

    TestDialogManager.setTestDialog { PushRejectedExitCode.REBASE.exitCode }

    updateRepositories()
    val pushSpec = makePushSpec(repository, "master", "origin/master")

    val result = object : GitPushOperation(project, pushSupport, singletonMap(repository, pushSpec), null, false, false) {
      var updateHappened: Boolean = false

      override fun update(rootsToUpdate: Collection<GitRepository>,
                          updateMethod: UpdateMethod,
                          checkForRebaseOverMergeProblem: Boolean): GitUpdateResult {
        val updateResult = super.update(rootsToUpdate, updateMethod, checkForRebaseOverMergeProblem)
        if (!updateHappened) {
          updateHappened = true
          pushCommitFromBro()
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

  fun `test force push without lease`() {
    (AdvancedSettings.getInstance() as AdvancedSettingsImpl).setSetting("git.use.push.force.with.lease", false, testRootDisposable)

    val broHash = pushCommitFromBro()

    cd(repository)
    val myHash = makeCommit("anyfile.txt")

    val result = push("master", "origin/master", true)
    assertResult(FORCED, -1, "master", "origin/master", result)

    cd(parentRepo)
    val parentHistory = StringUtil.splitByLines(git("log master --pretty=%H"))
    assertFalse(parentHistory.contains(broHash))
    assertEquals(myHash, parentHistory[0])
  }

  fun `test force push with lease succeeds if remote is on expected position`() {
    assumeForceWithLeaseSupported()

    val broHash = pushCommitFromBro()

    cd(repository)
    val myHash = makeCommit("anyfile.txt")

    git("fetch")

    val result = push("master", "origin/master", true)
    assertResult(FORCED, -1, "master", "origin/master", result)

    cd(parentRepo)
    val parentHistory = StringUtil.splitByLines(git("log master --pretty=%H"))
    assertFalse(parentHistory.contains(broHash))
    assertEquals(myHash, parentHistory[0])
  }

  fun `test force push with lease is rejected if remote has changed`() {
    assumeForceWithLeaseSupported()

    val broHash = pushCommitFromBro()

    cd(repository)
    val myHash = makeCommit("anyfile.txt")

    val result = push("master", "origin/master", true)
    assertResult(REJECTED_STALE_INFO, -1, "master", "origin/master", result)

    cd(parentRepo)
    val parentHistory = StringUtil.splitByLines(git("log master --pretty=%H"))
    assertFalse(parentHistory.contains(myHash))
    assertEquals(broHash, parentHistory[0])
  }

  fun `test force push with lease succeeds for new branch`() {
    assumeForceWithLeaseSupported()

    val broHash = pushCommitFromBro()

    cd(repository)
    val myHash = makeCommit("anyfile.txt")

    val result = push("master", "origin/feature", true)
    assertResult(NEW_BRANCH, -1, "master", "origin/feature", result)

    cd(parentRepo)
    val parentHistory = StringUtil.splitByLines(git("log master --pretty=%H"))
    assertEquals(broHash, parentHistory[0])

    val branchHistory = StringUtil.splitByLines(git("log feature --pretty=%H"))
    assertEquals(myHash, branchHistory[0])
  }

  fun `test force push with lease is rejected for existing branch`() {
    assumeForceWithLeaseSupported()

    val broHash = pushCommitFromBro()

    cd(broRepo)
    git("push origin master:feature")

    cd(repository)
    makeCommit("anyfile.txt")

    val result = push("master", "origin/feature", true)
    assertResult(REJECTED_STALE_INFO, -1, "master", "origin/feature", result)

    cd(parentRepo)
    val parentHistory = StringUtil.splitByLines(git("log master --pretty=%H"))
    assertEquals(broHash, parentHistory[0])

    val branchHistory = StringUtil.splitByLines(git("log feature --pretty=%H"))
    assertEquals(broHash, branchHistory[0])
  }

  fun `test dont propose to update if force push is rejected`() {
    var dialogShown = false
    TestDialogManager.setTestDialog {
      dialogShown = true
      PushRejectedExitCode.CANCEL.exitCode
    }

    val remoteTipAndPushResult = forcePushWithReject(true)
    assertResult(REJECTED_NO_FF, -1, "master", "origin/master", remoteTipAndPushResult.second)
    assertFalse("Rejected push dialog should not be shown", dialogShown)
    cd(parentRepo)
    assertEquals("The commit pushed from bro should be the last one", remoteTipAndPushResult.first, last())
  }

  fun `test dont silently update if force push is rejected`() {
    settings.updateMethod = UpdateMethod.REBASE
    settings.setAutoUpdateIfPushRejected(true)

    val remoteTipAndPushResult = forcePushWithReject(true)

    assertResult(REJECTED_NO_FF, -1, "master", "origin/master", remoteTipAndPushResult.second)
    cd(parentRepo)
    assertEquals("The commit pushed from bro should be the last one", remoteTipAndPushResult.first, last())
  }

  fun `test dont silently update if force with lease push is rejected`() {
    assumeForceWithLeaseSupported()

    settings.updateMethod = UpdateMethod.REBASE
    settings.setAutoUpdateIfPushRejected(true)

    val remoteTipAndPushResult = forcePushWithReject(false)

    assertResult(REJECTED_STALE_INFO, -1, "master", "origin/master", remoteTipAndPushResult.second)
    cd(parentRepo)
    assertEquals("The commit pushed from bro should be the last one", remoteTipAndPushResult.first, last())
  }

  private fun forcePushWithReject(fetchFirst: Boolean): Pair<String, GitPushResult> {
    val pushedHash = pushCommitFromBro()
    cd(parentRepo)
    git("config receive.denyNonFastForwards true")
    cd(repository)
    makeCommit("anyfile.txt")

    if (fetchFirst) git("fetch")

    val map = singletonMap(repository, makePushSpec(repository, "master", "origin/master"))
    val result = GitPushOperation(project, pushSupport, map, null, true, false).execute()
    return Pair.create(pushedHash, result)
  }

  fun `test merge after rejected push`() {
    val broHash = pushCommitFromBro()
    cd(repository)
    val hash = makeCommit("file.txt")

    TestDialogManager.setTestDialog { PushRejectedExitCode.MERGE.exitCode }

    val result = push("master", "origin/master")

    cd(repository)
    val log = git("log -3 --pretty=%H#%s")
    val commits = StringUtil.splitByLines(log)
    val lastCommitMsg = commits[0].split("#".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1]
    assertTrue("The last commit doesn't look like a merge commit: $lastCommitMsg", lastCommitMsg.contains("Merge"))
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

    TestDialogManager.setTestDialog { throw AssertionError("Update shouldn't be proposed") }

    val result = push("master", "origin/master")

    assertResult(REJECTED_OTHER, -1, "master", "origin/master", result)
    assertNotPushed(hash)
  }

  private fun assertNotPushed(hash: String) {
    assertEquals("", git("branch -r --contains $hash"))
  }

  fun `test update with conflicts cancels push`() {
    cd(broRepo)
    append("bro.txt", "bro content")
    makeCommit("msg")
    git("push origin master:master")

    cd(repository)
    append("bro.txt", "main content")
    makeCommit("msg")

    TestDialogManager.setTestDialog { PushRejectedExitCode.REBASE.exitCode }
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

  fun `test push single tag`() {
    cd(repository)
    git("tag v1")

    updateRepositories()
    val spec = GitPushTagAction.preparePushSpec(GitTag("v1"), repository.remotes.first())
    val pushResult = GitPushOperation(project, pushSupport, singletonMap(repository, spec), null, false, false).execute()
    val result = pushResult.results[repository]!!
    val pushedTags = result.pushedTags
    assertEquals(NEW_BRANCH, result.type)
    assertEquals(1, pushedTags.size)
    assertEquals("refs/tags/v1", pushedTags[0])

    val secondPushResult = GitPushOperation(project, pushSupport, singletonMap(repository, spec), null, false, false).execute()
    val secondResult = secondPushResult.results[repository]!!
    assertEmpty(secondResult.pushedTags)
    assertEquals(UP_TO_DATE, secondResult.type)
  }

  fun `test push existing tag`() {
    cd(repository)
    git("tag v1")
    git("push origin refs/tags/v1")
    git("tag --delete v1")
    makeCommit("msg")
    git("tag v1")

    updateRepositories()
    val spec = GitPushTagAction.preparePushSpec(GitTag("v1"), repository.remotes.first())
    val pushResult = GitPushOperation(project, pushSupport, singletonMap(repository, spec), null, false, false).execute()
    val result = pushResult.results[repository]!!
    val pushedTags = result.pushedTags
    assertEquals(REJECTED_OTHER, result.type)
    assertEmpty(pushedTags)
  }

  fun `test push with setting upstream`() {
    push("master", "origin/feature", canChangeUpstream = true)
    assertUpstream("master", "origin", "feature")
    push("master", "origin/feature-1", canChangeUpstream = true)
    assertUpstream("master", "origin", "feature-1")
  }

  fun `test skip pre push hook`() {
    assumeTrue("Not testing: pre-push hooks are not supported in ${vcs.version}", GitVersionSpecialty.PRE_PUSH_HOOK.existsIn(vcs.version))

    cd(repository)
    val hash = makeCommit("file.txt")

    val rejectHook = """
      exit 1
      """.trimIndent()
    installHook(repository.root.toNioPath().resolve(".git"), "pre-push", rejectHook)

    val result = push("master", "origin/master", false, true)

    assertResult(SUCCESS, 1, "master", "origin/master", result)
    assertPushed(hash, "master")
  }

  fun `test respect branch default setting for silent update when rejected push`() {
    generateUpdateNeeded()
    settings.updateMethod = UpdateMethod.BRANCH_DEFAULT
    git("config branch.master.rebase true")
    settings.setAutoUpdateIfPushRejected(true)

    push("master", "origin/master")
    assertFalse("Unexpected merge commit: rebase should have happened", log("-1 --pretty=%s").toLowerCase().startsWith("merge"))
  }

  // there is no "branch default" choice in the rejected push dialog
  // => simply don't rewrite the setting if the same value is chosen, as was default value initially
  fun `test dont overwrite branch default setting when agree in rejected push dialog`() {
    generateUpdateNeeded()
    settings.updateMethod = UpdateMethod.BRANCH_DEFAULT
    git("config branch.master.rebase true")

    TestDialogManager.setTestDialog { PushRejectedExitCode.CANCEL.exitCode }

    push("master", "origin/master")
    assertEquals(UpdateMethod.BRANCH_DEFAULT, settings.updateMethod)
  }

  private fun generateUpdateNeeded() {
    pushCommitFromBro()
    cd(repository)
    makeCommit("file.txt")
  }

  private fun push(from: String, to: String, force: Boolean = false, skipHook: Boolean = false, canChangeUpstream: Boolean = false): GitPushResult {
    updateRepositories()
    refresh()
    updateChangeListManager()

    val spec = makePushSpec(repository, from, to, canChangeUpstream)
    return GitPushOperation(project, pushSupport, singletonMap(repository, spec), null, force, skipHook).execute()
  }

  private fun pushCommitFromBro(): String {
    cd(broRepo)
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
    val result = mutableListOf<String>()
    for (group in updatedFiles.topLevelGroups) {
      result.addAll(getUpdatedFiles(group))
    }
    return result
  }

  private fun getUpdatedFiles(group: FileGroup): Collection<String> {
    val result = mutableListOf<String>()
    result.addAll(group.files.map { FileUtil.getRelativePath(File(projectPath), File(it))!! })
    for (child in group.children) {
      result.addAll(getUpdatedFiles(child))
    }
    return result
  }

  private fun assertPushed(expectedHash: String, branch: String) {
    cd(parentRepo)
    val actualHash = git("log -1 --pretty=%H $branch")
    assertEquals(expectedHash, actualHash)
  }

  private fun assertBranchExists(branch: String) {
    cd(parentRepo)
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

  private fun assumeForceWithLeaseSupported() {
    val version = vcs.version
    assumeTrue("Skipping this version of Git since it doesn't support --force-with-lease and calls --force: $version",
               GitVersionSpecialty.SUPPORTS_FORCE_PUSH_WITH_LEASE.existsIn(version))
  }
}
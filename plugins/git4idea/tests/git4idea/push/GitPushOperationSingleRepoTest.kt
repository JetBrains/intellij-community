// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.push

import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.options.advanced.AdvancedSettingsImpl
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.update.FileGroup
import com.intellij.openapi.vcs.update.UpdatedFiles
import com.intellij.testFramework.junit5.EnableTracingFor
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.disposableFixture
import com.intellij.vcs.test.refresh
import com.intellij.vcs.test.updateChangeListManager
import git4idea.GitTag
import git4idea.actions.tag.GitPushTagAction
import git4idea.branch.GitBranchUtil
import git4idea.config.GitVersionSpecialty
import git4idea.config.UpdateMethod
import git4idea.push.GitPushRepoResult.Type.FORCED
import git4idea.push.GitPushRepoResult.Type.NEW_BRANCH
import git4idea.push.GitPushRepoResult.Type.REJECTED_NO_FF
import git4idea.push.GitPushRepoResult.Type.REJECTED_OTHER
import git4idea.push.GitPushRepoResult.Type.REJECTED_STALE_INFO
import git4idea.push.GitPushRepoResult.Type.SUCCESS
import git4idea.push.GitPushRepoResult.Type.UP_TO_DATE
import git4idea.push.GitRejectedPushUpdateDialog.Companion.PushRejectedExitCode
import git4idea.repo.GitRepository
import git4idea.test.GitPlatformTestContext
import git4idea.test.addCommit
import git4idea.test.cd
import git4idea.test.git
import git4idea.test.installHook
import git4idea.test.last
import git4idea.test.log
import git4idea.test.makeCommit
import git4idea.test.makePushSpec
import git4idea.test.runUnderProgress
import git4idea.update.GitUpdateResult
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.util.Collections.singletonMap
import java.util.Locale

@TestApplication
@EnableTracingFor(categoryClasses = [GitPushOperation::class])
internal class GitPushOperationSingleRepoTest {

  private val fixture = gitPushSingleRepoFixture()
  private val context: GitPushSingleRepoContext get() = fixture.get()
  private val disposableFixture = disposableFixture()

  @Test
  fun `test successful push`(): Unit = with(context) {
    val hash = makeCommit("file.txt")
    val result = push("master", "origin/master")

    assertResult(SUCCESS, 1, "master", "origin/master", result)
    assertPushed(hash, "master")
  }

  @Test
  fun `test push new branch`(): Unit = with(context) {
    git("checkout -b feature")
    val result = push("feature", "origin/feature")

    assertResult(NEW_BRANCH, -1, "feature", "origin/feature", result)
    assertBranchExists("feature")
  }

  @Test
  fun `test push new branch with commits`(): Unit = with(context) {
    touch("feature.txt", "content")
    addCommit("feature commit")
    val hash = last()
    git("checkout -b feature")
    val result = push("feature", "origin/feature")

    assertResult(NEW_BRANCH, -1, "feature", "origin/feature", result)
    assertBranchExists("feature")
    assertPushed(hash, "feature")
  }

  @Test
  fun `test upstream is set for new branch`(): Unit = with(context) {
    git("checkout -b feature")
    push("feature", "origin/feature")
    assertUpstream("feature", "origin", "feature")
  }

  @Test
  fun `test upstream is not modified if already set`(): Unit = with(context) {
    push("master", "origin/feature")
    assertUpstream("master", "origin", "master")
  }

  @Test
  fun `test rejected push to tracked branch proposes to update`(): Unit = with(context) {
    pushCommitFromBro()

    var dialogShown = false
    TestDialogManager.setTestDialog {
      dialogShown = true
      PushRejectedExitCode.CANCEL.exitCode
    }
    val result = push("master", "origin/master")

    assertThat(dialogShown).describedAs("Rejected push dialog wasn't shown").isTrue()
    assertResult(REJECTED_NO_FF, -1, "master", "origin/master", result)
  }

  @Test
  fun `test rejected push to other branch doesnt propose to update`(): Unit = with(context) {
    pushCommitFromBro()
    cd(repository)
    git("checkout -b feature")

    var dialogShown = false
    TestDialogManager.setTestDialog {
      dialogShown = true
      PushRejectedExitCode.CANCEL.exitCode
    }

    val result = push("feature", "origin/master")

    assertThat(dialogShown).describedAs("Rejected push dialog shouldn't be shown").isFalse()
    assertResult(REJECTED_NO_FF, -1, "feature", "origin/master", result)
  }

  @Test
  fun `test push is rejected too many times`(): Unit = with(context) {
    pushCommitFromBro()
    cd(repository)
    val hash = makeCommit("afile.txt")

    TestDialogManager.setTestDialog { PushRejectedExitCode.MERGE.exitCode }

    updateRepositories()
    val pushSpec = makePushSpec(repository, "master", "origin/master")

    val result = runUnderProgress {
      object : GitPushOperation(project, pushSupport, singletonMap(repository, pushSpec), null, false, false) {
        override fun update(rootsToUpdate: Collection<GitRepository>,
                            updateMethod: UpdateMethod,
                            checkForRebaseOverMergeProblem: Boolean): GitUpdateResult {
          val updateResult = super.update(rootsToUpdate, updateMethod, checkForRebaseOverMergeProblem)
          pushCommitFromBro()
          return updateResult
        }
      }.execute()
    }
    assertResult(REJECTED_NO_FF, -1, "master", "origin/master", GitUpdateResult.SUCCESS, listOf("bro.txt"), result)

    cd(parentRepo)
    val history = git("log --all --pretty=%H ")
    assertThat(history).describedAs("The commit shouldn't be pushed").doesNotContain(hash)
  }

  @Test
  fun `test use selected update method for all consecutive updates`(): Unit = with(context) {
    pushCommitFromBro()
    cd(repository)
    makeCommit("afile.txt")

    TestDialogManager.setTestDialog { PushRejectedExitCode.REBASE.exitCode }

    updateRepositories()
    val pushSpec = makePushSpec(repository, "master", "origin/master")

    val result = runUnderProgress {
      object : GitPushOperation(project, pushSupport, singletonMap(repository, pushSpec), null, false, false) {
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
    }

    assertRepoResult(SUCCESS, 1, "master", "origin/master", GitUpdateResult.SUCCESS, result.results[repository]!!)
    cd(repository)
    val commitMessages = StringUtil.splitByLines(repository.log("--pretty=%s"))
    val mergeCommitsInTheLog = commitMessages.any { it.lowercase(Locale.getDefault()).contains("merge") }
    assertThat(mergeCommitsInTheLog).describedAs("Unexpected merge commits when rebase method is selected").isFalse()
  }

  @Test
  fun `test force push without lease`(): Unit = with(context) {
    (AdvancedSettings.getInstance() as AdvancedSettingsImpl)
      .setSetting("git.use.push.force.with.lease", false, disposableFixture.get())

    val broHash = pushCommitFromBro()

    cd(repository)
    val myHash = makeCommit("anyfile.txt")

    val result = push("master", "origin/master", true)
    assertResult(FORCED, -1, "master", "origin/master", result)

    cd(parentRepo)
    val parentHistory = StringUtil.splitByLines(git("log master --pretty=%H"))
    assertThat(parentHistory).doesNotContain(broHash)
    assertThat(parentHistory[0]).isEqualTo(myHash)
  }

  @Test
  fun `test force push with lease succeeds if remote is on expected position`(): Unit = with(context) {
    assumeForceWithLeaseSupported()

    val broHash = pushCommitFromBro()

    cd(repository)
    val myHash = makeCommit("anyfile.txt")

    git("fetch")

    val result = push("master", "origin/master", true)
    assertResult(FORCED, -1, "master", "origin/master", result)

    cd(parentRepo)
    val parentHistory = StringUtil.splitByLines(git("log master --pretty=%H"))
    assertThat(parentHistory).doesNotContain(broHash)
    assertThat(parentHistory[0]).isEqualTo(myHash)
  }

  @Test
  fun `test force push with lease is rejected if remote has changed`(): Unit = with(context) {
    assumeForceWithLeaseSupported()

    val broHash = pushCommitFromBro()

    cd(repository)
    val myHash = makeCommit("anyfile.txt")

    val result = push("master", "origin/master", true)
    assertResult(REJECTED_STALE_INFO, -1, "master", "origin/master", result)

    cd(parentRepo)
    val parentHistory = StringUtil.splitByLines(git("log master --pretty=%H"))
    assertThat(parentHistory).doesNotContain(myHash)
    assertThat(parentHistory[0]).isEqualTo(broHash)
  }

  @Test
  fun `test force push with lease succeeds for new branch`(): Unit = with(context) {
    assumeForceWithLeaseSupported()

    val broHash = pushCommitFromBro()

    cd(repository)
    val myHash = makeCommit("anyfile.txt")

    val result = push("master", "origin/feature", true)
    assertResult(NEW_BRANCH, -1, "master", "origin/feature", result)

    cd(parentRepo)
    val parentHistory = StringUtil.splitByLines(git("log master --pretty=%H"))
    assertThat(parentHistory[0]).isEqualTo(broHash)

    val branchHistory = StringUtil.splitByLines(git("log feature --pretty=%H"))
    assertThat(branchHistory[0]).isEqualTo(myHash)
  }

  @Test
  fun `test force push with lease is rejected for existing branch`(): Unit = with(context) {
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
    assertThat(parentHistory[0]).isEqualTo(broHash)

    val branchHistory = StringUtil.splitByLines(git("log feature --pretty=%H"))
    assertThat(branchHistory[0]).isEqualTo(broHash)
  }

  @Test
  fun `test dont propose to update if force push is rejected`(): Unit = with(context) {
    var dialogShown = false
    TestDialogManager.setTestDialog {
      dialogShown = true
      PushRejectedExitCode.CANCEL.exitCode
    }

    val (pushedHash, pushResult) = forcePushWithReject(true)
    assertResult(REJECTED_NO_FF, -1, "master", "origin/master", pushResult)
    assertThat(dialogShown).describedAs("Rejected push dialog should not be shown").isFalse()
    cd(parentRepo)
    assertThat(last()).describedAs("The commit pushed from bro should be the last one").isEqualTo(pushedHash)
  }

  @Test
  fun `test dont silently update if force push is rejected`(): Unit = with(context) {
    settings.updateMethod = UpdateMethod.REBASE
    settings.setAutoUpdateIfPushRejected(true)

    val (pushedHash, pushResult) = forcePushWithReject(true)

    assertResult(REJECTED_NO_FF, -1, "master", "origin/master", pushResult)
    cd(parentRepo)
    assertThat(last()).describedAs("The commit pushed from bro should be the last one").isEqualTo(pushedHash)
  }

  @Test
  fun `test dont silently update if force with lease push is rejected`(): Unit = with(context) {
    assumeForceWithLeaseSupported()

    settings.updateMethod = UpdateMethod.REBASE
    settings.setAutoUpdateIfPushRejected(true)

    val (pushedHash, pushResult) = forcePushWithReject(false)

    assertResult(REJECTED_STALE_INFO, -1, "master", "origin/master", pushResult)
    cd(parentRepo)
    assertThat(last()).describedAs("The commit pushed from bro should be the last one").isEqualTo(pushedHash)
  }

  @Test
  fun `test merge after rejected push`(): Unit = with(context) {
    val broHash = pushCommitFromBro()
    cd(repository)
    val hash = makeCommit("file.txt")

    TestDialogManager.setTestDialog { PushRejectedExitCode.MERGE.exitCode }

    val result = push("master", "origin/master")

    cd(repository)
    val log = git("log -3 --pretty=%H#%s")
    val commits = StringUtil.splitByLines(log)
    val lastCommitMsg = commits[0].split("#".toRegex()).dropLastWhile { it.isEmpty() }[1]
    assertThat(lastCommitMsg).describedAs("The last commit doesn't look like a merge commit").contains("Merge")
    assertThat(commits[1].split("#".toRegex()).dropLastWhile { it.isEmpty() }[0]).isEqualTo(hash)
    assertThat(commits[2].split("#".toRegex()).dropLastWhile { it.isEmpty() }[0]).isEqualTo(broHash)

    assertResult(SUCCESS, 2, "master", "origin/master", GitUpdateResult.SUCCESS, listOf("bro.txt"), result)
  }

  // IDEA-144179
  @Test
  fun `test don't update if rejected by some custom reason`(): Unit = with(context) {
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

  @Test
  fun `test update with conflicts cancels push`(): Unit = with(context) {
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

  @Test
  fun `test push tags`(): Unit = with(context) {
    cd(repository)
    git("tag v1")

    updateRepositories()
    val spec = makePushSpec(repository, "master", "origin/master")
    val pushResult = runUnderProgress {
      GitPushOperation(project, pushSupport, singletonMap(repository, spec),
                       GitPushTagMode.ALL, false, false).execute()
    }
    val result = pushResult.results[repository]!!
    val pushedTags = result.pushedTags
    assertThat(pushedTags).containsExactly("refs/tags/v1")
  }

  @Test
  fun `test push single tag`(): Unit = with(context) {
    cd(repository)
    git("tag v1")

    updateRepositories()
    val spec = GitPushTagAction.preparePushSpec(GitTag("v1"), repository.remotes.first())
    val pushResult = runUnderProgress {
      GitPushOperation(project, pushSupport, singletonMap(repository, spec), null, false, false).execute()
    }
    val result = pushResult.results[repository]!!
    assertThat(result.type).isEqualTo(NEW_BRANCH)
    assertThat(result.pushedTags).containsExactly("refs/tags/v1")

    val secondPushResult = runUnderProgress {
      GitPushOperation(project, pushSupport, singletonMap(repository, spec), null, false, false).execute()
    }
    val secondResult = secondPushResult.results[repository]!!
    assertThat(secondResult.pushedTags).isEmpty()
    assertThat(secondResult.type).isEqualTo(UP_TO_DATE)
  }

  @Test
  fun `test push existing tag`(): Unit = with(context) {
    cd(repository)
    git("tag v1")
    git("push origin refs/tags/v1")
    git("tag --delete v1")
    makeCommit("msg")
    git("tag v1")

    updateRepositories()
    val spec = GitPushTagAction.preparePushSpec(GitTag("v1"), repository.remotes.first())
    val pushResult = runUnderProgress {
      GitPushOperation(project, pushSupport, singletonMap(repository, spec), null, false, false).execute()
    }
    val result = pushResult.results[repository]!!
    assertThat(result.type).isEqualTo(REJECTED_OTHER)
    assertThat(result.pushedTags).isEmpty()
  }

  @Test
  fun `test push with setting upstream`(): Unit = with(context) {
    push("master", "origin/feature", canChangeUpstream = true)
    assertUpstream("master", "origin", "feature")
    push("master", "origin/feature-1", canChangeUpstream = true)
    assertUpstream("master", "origin", "feature-1")
  }

  @Test
  fun `test skip pre push hook`(): Unit = with(context) {
    assumeTrue(GitVersionSpecialty.PRE_PUSH_HOOK.existsIn(vcs.version)) {
      "Not testing: pre-push hooks are not supported in ${vcs.version}"
    }

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

  @Test
  fun `test respect branch default setting for silent update when rejected push`(): Unit = with(context) {
    generateUpdateNeeded()
    settings.updateMethod = UpdateMethod.BRANCH_DEFAULT
    git("config branch.master.rebase true")
    settings.setAutoUpdateIfPushRejected(true)

    push("master", "origin/master")
    assertThat(repository.log("-1 --pretty=%s").lowercase(Locale.getDefault()))
      .describedAs("Unexpected merge commit: rebase should have happened")
      .doesNotStartWith("merge")
  }

  // there is no "branch default" choice in the rejected push dialog
  // => simply don't rewrite the setting if the same value is chosen, as was default value initially
  @Test
  fun `test dont overwrite branch default setting when agree in rejected push dialog`(): Unit = with(context) {
    generateUpdateNeeded()
    settings.updateMethod = UpdateMethod.BRANCH_DEFAULT
    git("config branch.master.rebase true")

    TestDialogManager.setTestDialog { PushRejectedExitCode.CANCEL.exitCode }

    push("master", "origin/master")
    assertThat(settings.updateMethod).isEqualTo(UpdateMethod.BRANCH_DEFAULT)
  }

  private fun GitPushSingleRepoContext.forcePushWithReject(fetchFirst: Boolean): Pair<String, GitPushResult> {
    val pushedHash = pushCommitFromBro()
    cd(parentRepo)
    git("config receive.denyNonFastForwards true")
    cd(repository)
    makeCommit("anyfile.txt")

    if (fetchFirst) git("fetch")

    val map = singletonMap(repository, makePushSpec(repository, "master", "origin/master"))
    val result = runUnderProgress { GitPushOperation(project, pushSupport, map, null, true, false).execute() }
    return pushedHash to result
  }

  private fun GitPushSingleRepoContext.generateUpdateNeeded() {
    pushCommitFromBro()
    cd(repository)
    makeCommit("file.txt")
  }

  private fun GitPushSingleRepoContext.push(
    from: String,
    to: String,
    force: Boolean = false,
    skipHook: Boolean = false,
    canChangeUpstream: Boolean = false,
  ): GitPushResult {
    updateRepositories()
    refresh()
    updateChangeListManager()

    val spec = makePushSpec(repository, from, to, canChangeUpstream)
    return runUnderProgress {
      GitPushOperation(project, pushSupport, singletonMap(repository, spec), null, force, skipHook).execute()
    }
  }

  private fun GitPushSingleRepoContext.pushCommitFromBro(): String {
    cd(broRepo)
    val hash = makeCommit("bro.txt")
    git("push")
    return hash
  }

  private fun GitPushSingleRepoContext.assertResult(
    type: GitPushRepoResult.Type,
    pushedCommits: Int,
    from: String,
    to: String,
    actualResult: GitPushResult,
  ) {
    assertResult(type, pushedCommits, from, to, null, null, actualResult)
  }

  private fun GitPushSingleRepoContext.assertResult(
    type: GitPushRepoResult.Type,
    pushedCommits: Int,
    from: String,
    to: String,
    updateResult: GitUpdateResult?,
    updatedFiles: List<String>?,
    actualResult: GitPushResult,
  ) {
    assertRepoResult(type, pushedCommits, from, to, updateResult, actualResult.results[repository]!!)
    assertThat(getUpdatedFiles(actualResult.updatedFiles))
      .describedAs("Updated files set is incorrect")
      .containsExactlyInAnyOrderElementsOf(updatedFiles ?: emptyList())
  }

  private fun GitPushSingleRepoContext.getUpdatedFiles(updatedFiles: UpdatedFiles): Collection<String> {
    return updatedFiles.topLevelGroups.flatMap { getUpdatedFiles(it) }
  }

  private fun GitPushSingleRepoContext.getUpdatedFiles(group: FileGroup): Collection<String> {
    val result = mutableListOf<String>()
    result.addAll(group.files.map { FileUtil.getRelativePath(File(projectPath), File(it))!! })
    for (child in group.children) {
      result.addAll(getUpdatedFiles(child))
    }
    return result
  }

  private fun GitPushSingleRepoContext.assertNotPushed(hash: String) {
    assertThat(git("branch -r --contains $hash")).isEmpty()
  }

  private fun GitPushSingleRepoContext.assertPushed(expectedHash: String, branch: String) {
    cd(parentRepo)
    val actualHash = git("log -1 --pretty=%H $branch")
    assertThat(actualHash).isEqualTo(expectedHash)
  }

  private fun GitPushSingleRepoContext.assertBranchExists(branch: String) {
    cd(parentRepo)
    assertThat(git("branch")).contains(branch)
  }

  private fun GitPushSingleRepoContext.assertUpstream(
    localBranch: String,
    expectedUpstreamRemote: String,
    expectedUpstreamBranch: String,
  ) {
    val upstreamRemote = GitBranchUtil.stripRefsPrefix(git("config branch.$localBranch.remote"))
    val upstreamBranch = GitBranchUtil.stripRefsPrefix(git("config branch.$localBranch.merge"))
    assertThat(upstreamRemote).isEqualTo(expectedUpstreamRemote)
    assertThat(upstreamBranch).isEqualTo(expectedUpstreamBranch)
  }

  private fun GitPlatformTestContext.assumeForceWithLeaseSupported() {
    val version = vcs.version
    assumeTrue(GitVersionSpecialty.SUPPORTS_FORCE_PUSH_WITH_LEASE.existsIn(version)) {
      "Skipping this version of Git since it doesn't support --force-with-lease and calls --force: $version"
    }
  }
}

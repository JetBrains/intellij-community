// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.push

import com.intellij.dvcs.push.PushDivergence
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.options.advanced.AdvancedSettingsImpl
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.disposableFixture
import git4idea.branch.GitBranchIncomingOutgoingManager
import git4idea.config.GitIncomingRemoteCheckStrategy
import git4idea.config.GitVcsApplicationSettings
import git4idea.config.GitVcsSettings
import git4idea.test.addCommit
import git4idea.test.cd
import git4idea.test.git
import git4idea.test.last
import git4idea.test.makeCommit
import git4idea.test.makePushSpec
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

@TestApplication
internal class GitPushDivergenceDetectorTest {
  private val fixture = gitPushSingleRepoFixture()
  private val context: GitPushSingleRepoContext get() = fixture.get()
  private val disposableFixture = disposableFixture()

  @Test
  fun `test fast-forward push has no divergence`(): Unit = with(context) {
    makeCommit("file.txt")

    assertThat(detect("master", "origin/master")).isNull()
  }

  @Test
  fun `test push to a new branch has no divergence`(): Unit = with(context) {
    git("checkout -b feature")
    makeCommit("feature.txt")

    assertThat(detect("feature", "origin/feature")).isNull()
  }

  @Test
  fun `test source behind the target has no divergence`(): Unit = with(context) {
    pushCommitFromBro()
    cd(repository)
    git("fetch")

    assertThat(detect("master", "origin/master")).isNull()
  }

  @Test
  fun `test amend after push shows the replaced commit`(): Unit = with(context) {
    val pushed = makeCommit("file.txt")
    git("push origin master")
    git("commit --amend -m amended")

    val divergence = detectDiverged("master", "origin/master")

    assertThat(divergence.remoteCommitCount).isEqualTo(1)
    assertThat(divergence.remoteTip?.asString()).isEqualTo(pushed)
  }

  @Test
  fun `test rebase onto a moved base shows the replaced commits`(): Unit = with(context) {
    git("checkout -b feature")
    makeCommit("feature1.txt")
    makeCommit("feature2.txt")
    git("push -u origin feature")
    git("checkout master")
    makeCommit("master.txt")
    git("push origin master")
    git("checkout feature")
    git("rebase master")

    assertThat(detectDiverged("feature", "origin/feature").remoteCommitCount).isEqualTo(2)
  }

  @Test
  fun `test fetched commit from another clone is counted`(): Unit = with(context) {
    pushCommitFromBro()
    cd(repository)
    makeCommit("mine.txt")
    git("fetch")

    assertThat(detectDiverged("master", "origin/master").remoteCommitCount).isEqualTo(1)
  }

  @Test
  fun `test unfetched commit requires a fetch`(): Unit = with(context) {
    pushCommitFromBro()
    cd(repository)
    makeCommit("mine.txt")
    checkRemoteWithLsRemote()

    assertThat(detect("master", "origin/master")).isInstanceOf(PushDivergence.FetchRequired::class.java)
  }

  @Test
  fun `test unfetched commits hide the outdated remote commits`(): Unit = with(context) {
    makeCommit("file.txt")
    git("push origin master")
    cd(broRepo)
    git("fetch")
    git("merge --ff-only origin/master")
    makeCommit("bro.txt")
    git("push")
    cd(repository)
    git("commit --amend -m amended")
    checkRemoteWithLsRemote()

    assertThat(detect("master", "origin/master")).isInstanceOf(PushDivergence.FetchRequired::class.java)
  }

  @Test
  fun `test fetch of the target counts the unfetched commit`(): Unit = with(context) {
    pushCommitFromBro()
    cd(repository)
    makeCommit("mine.txt")
    checkRemoteWithLsRemote()
    val spec = makePushSpec(repository, "master", "origin/master")

    val error = GitPushDivergenceDetector.getInstance(project).fetchTargetBlocking(repository, spec.target)

    assertThat(error).isNull()
    assertThat(detectDiverged("master", "origin/master").remoteCommitCount).isEqualTo(1)
  }

  @Test
  fun `test unfetched commit that is already in the source has no divergence`(): Unit = with(context) {
    pushCommitFromBro()
    cd(repository)
    checkRemoteWithLsRemote()
    git("fetch origin master:refs/heads/from-bro")
    git("merge --ff-only from-bro")
    makeCommit("mine.txt")

    assertThat(detect("master", "origin/master")).isNull()
  }

  @Test
  fun `test remote check finds an unfetched commit without the background check`(): Unit = with(context) {
    pushCommitFromBro()
    cd(repository)
    makeCommit("mine.txt")

    assertThat(detect("master", "origin/master", checkRemote = true)).isInstanceOf(PushDivergence.FetchRequired::class.java)
  }

  @Test
  fun `test remote check without remote commits has no divergence`(): Unit = with(context) {
    makeCommit("mine.txt")

    assertThat(detect("master", "origin/master", checkRemote = true)).isNull()
  }

  @Test
  fun `test remote check of a deleted remote branch has no divergence`(): Unit = with(context) {
    git("checkout -b feature")
    makeCommit("feature.txt")
    git("push -u origin feature")
    git("commit --amend -m amended")
    cd(broRepo)
    git("push origin --delete feature")
    cd(repository)

    assertThat(detect("feature", "origin/feature", checkRemote = false)).isNotNull()
    assertThat(detect("feature", "origin/feature", checkRemote = true)).isNull()
  }

  @Test
  fun `test check before a push without the dialog fetches the unfetched commits`(): Unit = with(context) {
    val broHash = pushCommitFromBro()
    cd(repository)
    makeCommit("mine.txt")
    disableCredentialHelper()
    updateRepositories()

    val diverged = runBlocking { GitPushDivergenceDetector.getInstance(project).hasDivergedDefaultPush(listOf(repository)) }

    assertThat(diverged).isTrue()
    assertThat(git("rev-parse origin/master")).isEqualTo(broHash)
  }

  @Test
  fun `test check before a push without the dialog allows a fast-forward push`(): Unit = with(context) {
    makeCommit("mine.txt")
    disableCredentialHelper()
    updateRepositories()

    assertThat(runBlocking { GitPushDivergenceDetector.getInstance(project).hasDivergedDefaultPush(listOf(repository)) }).isFalse()
  }

  @Test
  fun `test remote check finds the local result when the remote didnt move`(): Unit = with(context) {
    git("checkout -b feature")
    commit("feature.txt", "Feature")
    git("push -u origin feature")
    git("commit --amend -m Renamed")
    val local = detectDiverged("feature", "origin/feature")

    val checked = assertInstanceOf(PushDivergence.Diverged::class.java, checkRemote("feature", "origin/feature"))

    assertThat(checked.remoteCommitCount).isEqualTo(local.remoteCommitCount)
    assertThat(checked.remoteTip).isEqualTo(local.remoteTip)
  }

  @Test
  fun `test remote check replaces a cached remote position that the remote left`(): Unit = with(context) {
    pushCommitFromBro()
    cd(repository)
    makeCommit("mine.txt")
    checkRemoteWithLsRemote()
    // the commit of the other clone exists locally, but the remote-tracking branch doesn't point to it
    git("fetch --refmap= origin master:refs/heads/from-bro")
    cd(broRepo)
    git("push --force origin HEAD~1:master")
    cd(repository)
    val local = detect("master", "origin/master")

    assertThat(local).isNotNull()
    assertThat(checkRemote("master", "origin/master")).isNull()
  }

  @Test
  fun `test remote check respects the setting to never check the remote`(): Unit = with(context) {
    GitVcsSettings.getInstance(project).setIncomingCommitsCheckStrategy(GitIncomingRemoteCheckStrategy.NONE)
    pushCommitFromBro()
    cd(repository)
    makeCommit("mine.txt")

    assertThat(detect("master", "origin/master", checkRemote = true)).isNull()
  }

  @Test
  fun `test remote check respects the setting to hide the incoming and outgoing commits`(): Unit = with(context) {
    (AdvancedSettings.getInstance() as AdvancedSettingsImpl)
      .setSetting("git.update.incoming.outgoing.info", false, disposableFixture.get())
    pushCommitFromBro()
    cd(repository)
    makeCommit("mine.txt")

    assertThat(detect("master", "origin/master", checkRemote = true)).isNull()
  }

  @Test
  fun `test divergence names the branches for the log`(): Unit = with(context) {
    git("checkout -b feature")
    commit("feature.txt", "Feature")
    git("push -u origin feature")
    git("commit --amend -m Renamed")

    assertThat(detectDiverged("feature", "origin/feature").logBranchNames).containsExactly("feature", "origin/feature")
  }

  @Test
  fun `test force push without lease has no expected remote tip`(): Unit = with(context) {
    (AdvancedSettings.getInstance() as AdvancedSettingsImpl)
      .setSetting("git.use.push.force.with.lease", false, disposableFixture.get())
    makeCommit("file.txt")
    git("push origin master")
    git("commit --amend -m amended")

    val divergence = detect("master", "origin/master")!!

    assertThat(divergence.remoteTip).isNull()
  }

  private fun GitPushSingleRepoContext.detect(from: String, to: String, checkRemote: Boolean = false): PushDivergence? {
    updateRepositories()
    if (checkRemote) disableCredentialHelper()
    val spec = makePushSpec(repository, from, to)
    return GitPushDivergenceDetector.getInstance(project).detectBlocking(repository, spec.source, spec.target, checkRemote)
  }

  private fun GitPushSingleRepoContext.detectDiverged(from: String, to: String): PushDivergence.Diverged =
    assertInstanceOf(PushDivergence.Diverged::class.java, detect(from, to))

  /**
   * Runs the remote check that the push dialog runs after it shows the commits.
   */
  private fun GitPushSingleRepoContext.checkRemote(from: String, to: String): PushDivergence? {
    updateRepositories()
    disableCredentialHelper()
    val spec = makePushSpec(repository, from, to)
    return GitOutgoingCommitsProvider(project).detectDivergence(repository, spec, true)
  }

  private fun disableCredentialHelper() {
    val settings = GitVcsApplicationSettings.getInstance()
    val useCredentialHelper = settings.isUseCredentialHelper
    settings.isUseCredentialHelper = false
    Disposer.register(disposableFixture.get()) { settings.isUseCredentialHelper = useCredentialHelper }
  }

  private fun GitPushSingleRepoContext.checkRemoteWithLsRemote() {
    disableCredentialHelper()
    GitVcsSettings.getInstance(project).setIncomingCommitsCheckStrategy(GitIncomingRemoteCheckStrategy.LS_REMOTE)
    repository.update()
    GitBranchIncomingOutgoingManager.getInstance(project).updateForTests()
  }

  private fun GitPushSingleRepoContext.commit(file: String, message: String): String {
    touch(file, "content of $file")
    addCommit(message)
    return last()
  }

  private fun GitPushSingleRepoContext.pushCommitFromBro(): String {
    cd(broRepo)
    val hash = makeCommit("bro.txt")
    git("push")
    return hash
  }
}

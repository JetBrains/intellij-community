// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo

import com.intellij.dvcs.repo.Repository.State
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import git4idea.GitLocalBranch
import git4idea.GitUtil
import git4idea.branch.GitBranchUtil
import git4idea.config.GitExecutableManager
import git4idea.config.GitVersionSpecialty
import git4idea.test.GitPlatformTestContext
import git4idea.test.GitScenarios.conflict
import git4idea.test.addCommit
import git4idea.test.git
import git4idea.test.gitInit
import git4idea.test.gitPlatformContextFixture
import git4idea.test.last
import git4idea.test.makeCommit
import git4idea.test.registerRepo
import git4idea.test.setupDefaultUsername
import git4idea.test.setupLocalIgnore
import git4idea.test.tac
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

/**
 * [GitRepositoryReaderTest] reads information from the pre-created .git directory from a real project.
 * This one, on the other hand, operates on a live Git repository, putting it to various situations and checking the results.
 */
@TestApplication
abstract class GitRepositoryReaderNewTest(private val usingReftable: Boolean) {
  class UsingReftable : GitRepositoryReaderNewTest(usingReftable = true)
  class UsingPackedRefs : GitRepositoryReaderNewTest(usingReftable = false)

  private val contextFixture = gitPlatformContextFixture()
  private val context: GitPlatformTestContext get() = contextFixture.get()

  private lateinit var repo: GitRepository

  @BeforeEach
  fun setUp() {
    with(context) {
      repo = initRepositoryWithRefFormat(usingReftable)
      cd(projectPath)
    }
  }

  private fun GitPlatformTestContext.initRepositoryWithRefFormat(usingReftable: Boolean): GitRepository {
    Files.createDirectories(projectNioRoot)
    cd(projectNioRoot)

    val version = GitExecutableManager.getInstance().tryGetVersion(project)
    val supportsReftable = version != null && GitVersionSpecialty.INIT_SUPPORTS_REFTABLE_FORMAT.existsIn(version)

    when {
      usingReftable -> {
        assumeTrue(supportsReftable, "Unsupported git version: $version")
        gitInit(project, "--ref-format=reftable")
      }
      supportsReftable -> gitInit(project, "--ref-format=files")
      else -> gitInit(project)
    }
    setupDefaultUsername()
    setupLocalIgnore(projectNioRoot)

    checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(projectNioRoot.resolve(GitUtil.DOT_GIT))) {
      "${GitUtil.DOT_GIT} was not created in $projectPath"
    }
    return registerRepo(project, projectNioRoot)
  }

  // IDEA-152632
  @Test
  fun `test current branch is known during rebase`(): Unit = with(context) {
    makeCommit("file.txt")
    conflict(repo, "feature")
    git("checkout feature")
    git("rebase master", true)

    val state = readState()
    assertThat(state.state).describedAs("State value is incorrect").isEqualTo(State.REBASING)
    val currentBranch = state.currentBranch
    assertThat(currentBranch).describedAs("Current branch should be known during rebase").isNotNull()
    assertThat(currentBranch!!.name).describedAs("Current branch is incorrect").isEqualTo("feature")
  }

  @Test
  fun `test rebase with conflicts while being on detached HEAD`(): Unit = with(context) {
    makeCommit("file.txt")
    conflict(repo, "feature")
    makeCommit("file2.txt")
    git("checkout HEAD^")
    git("rebase feature", true)

    val state = readState()
    assertThat(state.currentBranch).describedAs("Current branch can't be identified for this case").isNull()
    assertThat(state.state).describedAs("State value is incorrect").isEqualTo(State.REBASING)
  }

  // IDEA-124052
  @Test
  fun `test remote reference without remote`(): Unit = with(context) {
    makeCommit("file.txt")
    val invalidRemote = "invalid-remote"
    val invalidRemoteBranch = "master"
    git("update-ref refs/remotes/$invalidRemote/$invalidRemoteBranch HEAD")

    val remoteBranches = readState().remoteBranches.keys
    assertThat(remoteBranches)
      .describedAs("Remote branch not found")
      .anyMatch { it.nameForLocalOperations == "$invalidRemote/$invalidRemoteBranch" }
  }

  // IDEA-134286
  @Test
  fun `test detached HEAD`(): Unit = with(context) {
    val head = moveToDetachedHead()
    val state = readState()
    assertThat(state.state).describedAs("Detached HEAD is not detected").isEqualTo(State.DETACHED)
    assertThat(state.currentRevision).describedAs("Detached HEAD hash is incorrect").isEqualTo(head)
  }

  // IDEA-135966
  @Test
  fun `test no local branches`(): Unit = with(context) {
    val head = moveToDetachedHead()
    git("branch -D master")
    val state = readState()
    assertThat(state.state).describedAs("Detached HEAD is not detected").isEqualTo(State.DETACHED)
    assertThat(state.currentRevision).describedAs("Detached HEAD hash is incorrect").isEqualTo(head)
    assertThat(state.localBranches).describedAs("There should be no local branches").isEmpty()
  }

  @Test
  fun `test tracking remote with complex name`(): Unit = with(context) {
    makeCommit("file.txt")
    git("remote add my/remote http://my.remote.git")
    git("update-ref refs/remotes/my/remote/master HEAD")
    git("config branch.master.remote my/remote")
    git("config branch.master.merge refs/heads/master")
    repo.update()

    val trackInfo = GitBranchUtil.getTrackInfoForBranch(repo, repo.currentBranch!!)!!
    val remote = trackInfo.remote
    assertThat(remote.name).isEqualTo("my/remote")
    assertThat(remote.firstUrl).isEqualTo("http://my.remote.git")
  }

  // IDEA-134412
  @Test
  fun `test fresh repository is on branch`(): Unit = with(context) {
    val currentBranch = readState().currentBranch
    assertThat(currentBranch).describedAs("Current branch shouldn't be null in a fresh repository").isNotNull()
    assertThat(currentBranch!!.name).describedAs("Fresh repository should be on master").isEqualTo("master")
  }

  // IDEA-101222
  @Test
  fun `test non-ascii current branch name`(): Unit = with(context) {
    makeCommit("file.txt")
    val branch = "teslá"
    git("checkout -b $branch")
    val state = readState()
    assertThat(state.currentBranch!!.name).isEqualTo(branch)
  }

  // IDEA-143791
  @Test
  fun `test branches are case-insensitive on case-insensitive systems`(): Unit = with(context) {
    assumeFalse(SystemInfo.isFileSystemCaseSensitive, "case-insensitive FS only")
    assumeFalse(usingReftable, "Reftable branch names are case-sensitive")

    makeCommit("file.txt")
    git("branch UpperCase")
    git("checkout uppercase")

    repo.update()
    assertThat(repo.currentBranchName).isEqualTo("UpperCase")
    assertThat(repo.branches.findBranchByName("uppercase")).isEqualTo(repo.branches.findBranchByName("UpperCase"))
    assertThat(GitLocalBranch("uppercase")).isEqualTo(GitLocalBranch("UpperCase"))
  }

  @Test
  fun `test branches are case-sensitive on case-sensitive systems`(): Unit = with(context) {
    assumeTrue(SystemInfo.isFileSystemCaseSensitive, "Not tested: this test is for case sensitive FS only")

    makeCommit("file.txt")
    git("branch uppercase")
    git("branch UpperCase") // doesn't fail on case-sensitive OS: new branch is created
    git("checkout UpperCase")

    repo.update()
    assertThat(repo.currentBranchName).isEqualTo("UpperCase")
    assertThat(repo.branches.localBranches).hasSize(3)
    assertThat(repo.branches.findBranchByName("uppercase")).isNotEqualTo(repo.branches.findBranchByName("UpperCase"))
    assertThat(GitLocalBranch("uppercase")).isNotEqualTo(GitLocalBranch("UpperCase"))
  }

  @Test
  fun `test non-branch files are ignored`(): Unit = with(context) {
    assumeFalse(usingReftable)

    tac("f.txt")
    assertThat(File(repo.repositoryFiles.refsHeadsFile, "master.lock").createNewFile()).isTrue()

    repo.update()
    assertThat(repo.branches.localBranches.map { it.name }).containsExactlyInAnyOrder("master")
  }

  @Test
  @RegistryKey(key = "git.read.branches.from.disk", value = "true")
  fun `test current branch is known even if deleted`(): Unit = with(context) {
    assumeTrue(!usingReftable && Registry.`is`("git.read.branches.from.disk"))

    makeCommit("file.txt")
    val branch = "feature"
    git("checkout -b $branch")
    rm(".git/refs/heads/$branch")
    val state = readState()
    assertThat(state.currentBranch).isEqualTo(GitLocalBranch(branch))
    assertThat(state.currentRevision).isNull()
  }

  @Test
  fun `test fresh repository`() {
    assertThat(repo.isFresh).isTrue()
    assertThat(repo.currentRevision).isNull()
    assertThat(repo.currentBranch?.name).isEqualTo("master")
  }

  @Test
  fun `test cherry-pick state without CHERRY_PICK_HEAD`(): Unit = with(context) {
    val file = "file.txt"
    prepareStateForApplyChangesTest("cherry-pick", file)

    assertThat(readState().state).isEqualTo(State.GRAFTING)

    makeCommit(file)

    assertThat(readState().state).isEqualTo(State.GRAFTING)
  }

  @Test
  fun `test revert state without REVERT_HEAD`(): Unit = with(context) {
    val file = "file.txt"
    prepareStateForApplyChangesTest("revert", file)

    assertThat(readState().state).isEqualTo(State.REVERTING)

    makeCommit(file)

    assertThat(readState().state).isEqualTo(State.REVERTING)
  }

  private fun GitPlatformTestContext.readState(): GitBranchState {
    val config = GitConfig.read(project, projectNioRoot)
    val reader = GitRepositoryReader(project, repo.repositoryFiles)
    return reader.readState(config.parseRemotes())
  }
}

private fun GitPlatformTestContext.prepareStateForApplyChangesTest(command: String, file: String) {
  makeCommit(file)
  overwrite(file, "new content")
  val commit = addCommit("modified $file 1")
  overwrite(file, "newer content")
  val commit2 = addCommit("modified $file 2")
  git("$command $commit $commit2", true)
}

private fun GitPlatformTestContext.moveToDetachedHead(): String {
  makeCommit("file.txt")
  makeCommit("file.txt")
  git("checkout HEAD^")
  return last()
}

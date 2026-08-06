// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import git4idea.GitStandardLocalBranch
import git4idea.GitWorkingTree
import git4idea.actions.ref.GitSingleRefAction
import git4idea.test.GitSingleRepoContext
import git4idea.test.git
import git4idea.test.gitSingleRepoContextFixture
import git4idea.test.registerRepo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
@RegistryKey("git.enable.working.trees.feature", "true")
internal class GitWorkingTreeCurrentDetectionTest {
  private val contextFixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = contextFixture.get()

  @Test
  fun `test current branch is not reported as checked out in another worktree`(): Unit = with(context) {
    repo.ensureWorkingTreesUpToDateForTests()

    assertThat(repo.workingTreeHolder.getWorkingTrees()).containsExactlyInAnyOrderElementsOf(
      listOf(GitWorkingTree(repo.root.path, repo.currentBranch!!.fullName, true, true))
    )
    assertThat(GitSingleRefAction.getWorkingTreeWithRef(repo.currentBranch!!, repo, skipCurrentWorkingTree = true)).isNull()
  }

  @Test
  fun `test branch in a sibling worktree is reported as checked out elsewhere`(): Unit = with(context) {
    val featurePath = testNioRoot.resolve("feature")
    git("worktree add -b feature $featurePath")
    repo.ensureWorkingTreesUpToDateForTests()

    val trees = repo.workingTreeHolder.getWorkingTrees()
    assertThat(trees).containsExactlyInAnyOrderElementsOf(
      listOf(
        GitWorkingTree(repo.root.path, repo.currentBranch!!.fullName, true, true),
        GitWorkingTree(featurePath.toString(), "refs/heads/feature", false, false),
      )
    )

    val featureTree = trees.single { !it.isMain }
    assertThat(GitSingleRefAction.getWorkingTreeWithRef(GitStandardLocalBranch("feature"), repo, skipCurrentWorkingTree = true))
      .isEqualTo(featureTree)
    assertThat(GitSingleRefAction.getWorkingTreeWithRef(repo.currentBranch!!, repo, skipCurrentWorkingTree = true)).isNull()
  }

  @Test
  fun `test worktree nested inside the main repo directory is detected`(): Unit = with(context) {
    val nestedPath = projectNioRoot.resolve("nested")
    git("worktree add -b nested $nestedPath")
    repo.ensureWorkingTreesUpToDateForTests()

    val trees = repo.workingTreeHolder.getWorkingTrees()
    assertThat(trees).containsExactlyInAnyOrderElementsOf(
      listOf(
        GitWorkingTree(repo.root.path, repo.currentBranch!!.fullName, true, true),
        GitWorkingTree(nestedPath.toString(), "refs/heads/nested", false, false),
      )
    )

    val nestedTree = trees.single { !it.isMain }
    assertThat(GitSingleRefAction.getWorkingTreeWithRef(GitStandardLocalBranch("nested"), repo, skipCurrentWorkingTree = true))
      .isEqualTo(nestedTree)
  }

  @Test
  fun `test from a linked worktree the main branch is reported as checked out elsewhere`(): Unit = with(context) {
    val mainBranch = repo.currentBranch!!
    val featurePath = testNioRoot.resolve("feature")
    git("worktree add -b feature $featurePath")

    val linkedRepo = registerRepo(project, featurePath)
    linkedRepo.ensureWorkingTreesUpToDateForTests()

    // The linked worktree's own (feature) branch must not be considered busy.
    assertThat(GitSingleRefAction.getWorkingTreeWithRef(GitStandardLocalBranch("feature"), linkedRepo, skipCurrentWorkingTree = true))
      .isNull()

    // The main branch is checked out in the (now non-current) main worktree.
    val blocking = GitSingleRefAction.getWorkingTreeWithRef(mainBranch, linkedRepo, skipCurrentWorkingTree = true)
    assertThat(blocking).describedAs("Main branch should be reported as checked out in the main worktree").isNotNull()
    assertThat(blocking!!.isMain).describedAs("The blocking worktree should be the main one").isTrue()
    assertThat(blocking.path.path).isEqualTo(repo.root.path)
  }
}

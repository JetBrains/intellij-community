// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.vcs.test.vcsTestProjectPathFixture
import git4idea.GitWorkingTree
import git4idea.actions.ref.GitSingleRefAction
import git4idea.config.GitSaveChangesPolicy
import git4idea.repo.isSubmodule
import git4idea.test.gitPlatformContextFixture
import git4idea.update.GitSubmoduleProjectContext
import git4idea.update.gitSubmoduleProjectFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
internal class GitSubmoduleWorkingTreeTest {
  private val contextFixture = gitPlatformContextFixture(vcsTestProjectPathFixture(), saveChangesPolicy = GitSaveChangesPolicy.STASH)
    .gitSubmoduleProjectFixture(checkoutSubmoduleBranch = true, updateSubmoduleRepo = true)
  private val context: GitSubmoduleProjectContext get() = contextFixture.get()

  @Test
  fun `test submodule main worktree is recognized as current`(): Unit = with(context) {
    sub.ensureWorkingTreesUpToDateForTests()

    assertThat(sub.workingTreeHolder.getWorkingTrees()).containsExactlyInAnyOrderElementsOf(
      listOf(GitWorkingTree(sub.root.path, sub.currentBranch!!.fullName, true, true))
    )
  }

  @Test
  @RegistryKey("git.enable.working.trees.feature", "true")
  fun `test submodule and its parent are both worktree-capable`(): Unit = with(context) {
    // Worktrees can be created for nested repositories too, so both the main repo and the submodule qualify.
    val capable = GitWorkingTreesService.worktreeCapableRepositories(project)
    assertThat(capable).containsExactlyInAnyOrder(main, sub)

    val status = GitWorkingTreesService.getWorktreeSupportStatus(project)
    assertThat(status)
      .describedAs("A project with a submodule is a multi-repository project")
      .isInstanceOf(GitWorktreeSupportStatus.MultipleRepository::class.java)
    assertThat((status as GitWorktreeSupportStatus.MultipleRepository).repositories).containsExactlyInAnyOrder(main, sub)

    // The tab marks nested repositories; this is the signal it relies on.
    assertThat(sub.isSubmodule()).describedAs("The submodule must be detected as a submodule").isTrue()
    assertThat(main.isSubmodule()).describedAs("The main repository is not a submodule").isFalse()
  }

  @Test
  @RegistryKey("git.enable.working.trees.feature", "true")
  fun `test branch is not reported as checked out in another worktree`(): Unit = with(context) {
    sub.ensureWorkingTreesUpToDateForTests()

    val branch = sub.currentBranch!!
    assertThat(GitSingleRefAction.findCheckedOutWorkingTree(branch, listOf(sub), skipCurrentWorkingTree = true))
      .describedAs("Submodule branch must not be reported as checked out in another worktree")
      .isNull()
  }
}

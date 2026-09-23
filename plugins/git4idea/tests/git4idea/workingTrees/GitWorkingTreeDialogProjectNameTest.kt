// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.vcs.test.refresh
import com.intellij.vcs.test.vcsTestProjectPathFixture
import git4idea.config.GitSaveChangesPolicy
import git4idea.test.git
import git4idea.test.gitPlatformContextFixture
import git4idea.test.registerRepo
import git4idea.update.GitSubmoduleProjectContext
import git4idea.update.createPlainRepo
import git4idea.update.gitSubmoduleProjectFixture
import git4idea.workingTrees.dialog.GitWorkingTreeDialog
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.name

@TestApplication
@RegistryKey("git.enable.working.trees.feature", "true")
internal class GitWorkingTreeDialogProjectNameTest {
  private val contextFixture = gitPlatformContextFixture(vcsTestProjectPathFixture(), saveChangesPolicy = GitSaveChangesPolicy.STASH)
    .gitSubmoduleProjectFixture(checkoutSubmoduleBranch = true, updateSubmoduleRepo = true)
  private val context: GitSubmoduleProjectContext get() = contextFixture.get()

  @Test
  fun `test project name base is the main repository root for the main repository and its working tree`(): Unit = with(context) {
    assertThat(GitWorkingTreeDialog.resolveProjectNameBase(main).name)
      .describedAs("The main repository must suggest its own root name")
      .isEqualTo(main.root.toNioPath().name)

    val mainWorktreePath = testNioRoot.resolve("main-feature")
    cd(main.root.path)
    git("worktree add $mainWorktreePath -b main-feature")
    refresh()
    val mainWorktree = registerRepo(project, mainWorktreePath)
    main.ensureWorkingTreesUpToDateForTests()
    mainWorktree.ensureWorkingTreesUpToDateForTests()

    assertThat(GitWorkingTreeDialog.resolveProjectNameBase(mainWorktree).name)
      .describedAs("A working tree of the main repository must suggest the main repository's root name, not its own")
      .isEqualTo(main.root.toNioPath().name)
  }

  @Test
  fun `test project name base is the submodule root for the submodule and its working tree`(): Unit = with(context) {
    assertThat(GitWorkingTreeDialog.resolveProjectNameBase(sub).name)
      .describedAs("A submodule must suggest its own root name, not the 'modules' git-internal directory")
      .isEqualTo(sub.root.toNioPath().name)

    val subWorktreePath = testNioRoot.resolve("sub-feature")
    cd(sub.root.path)
    git("worktree add $subWorktreePath -b sub-feature")
    refresh()
    val subWorktree = registerRepo(project, subWorktreePath)
    sub.ensureWorkingTreesUpToDateForTests()
    subWorktree.ensureWorkingTreesUpToDateForTests()

    assertThat(GitWorkingTreeDialog.resolveProjectNameBase(subWorktree).name)
      .describedAs("A working tree of the submodule must suggest the submodule's root name, not its own")
      .isEqualTo(sub.root.toNioPath().name)
  }

  @Test
  fun `test project name base is the main repository root even when the main repository is not registered in this project`(): Unit =
    with(context) {
      // Simulates the main repository being open in another project window: only the working tree is
      // registered as a `GitRepository` here, never the main repository itself.
      val standalone = createPlainRepo(project, testNioRoot, "Standalone")
      val standaloneWorktreePath = testNioRoot.resolve("Standalone-feature")
      cd(standalone.local)
      git("worktree add $standaloneWorktreePath -b standalone-feature")
      refresh()
      val standaloneWorktree = registerRepo(project, standaloneWorktreePath)
      standaloneWorktree.ensureWorkingTreesUpToDateForTests()

      assertThat(GitWorkingTreeDialog.resolveProjectNameBase(standaloneWorktree).name)
        .describedAs("A working tree must suggest the main repository's root name even when the main repository is not registered")
        .isEqualTo(standalone.local.name)
    }

  @Test
  fun `test project name base is the submodule's own directory name even when it differs from the submodule's name in gitmodules`(): Unit =
    with(context) {
      val lib = createPlainRepo(project, testNioRoot, "lib")
      cd(main.root.path)
      git("submodule add --name mylib ${lib.remote.invariantSeparatorsPathString} custom-dir")
      git("commit -m 'Added a submodule whose gitmodules name differs from its directory'")
      refresh()

      val customSub = registerRepo(project, main.root.toNioPath().resolve("custom-dir"))
      customSub.ensureWorkingTreesUpToDateForTests()

      assertThat(GitWorkingTreeDialog.resolveProjectNameBase(customSub).name)
        .describedAs("A submodule must suggest its own directory name, not its different name in .gitmodules")
        .isEqualTo("custom-dir")
    }

  @Test
  fun `test project name base is the enclosing directory for a working tree of a bare repository whose git directory is not named dot-git`(): Unit =
    with(context) {
      val upstream = createPlainRepo(project, testNioRoot, "upstream")
      val bareProjectDir = testNioRoot.resolve("bare-project")
      Files.createDirectories(bareProjectDir)
      val bareGitDir = bareProjectDir.resolve(".bare")

      cd(testNioRoot)
      git("clone --bare ${upstream.remote.invariantSeparatorsPathString} ${bareGitDir.invariantSeparatorsPathString}")
      cd(bareGitDir)
      git("worktree add ../feature -b feature")
      refresh()

      val featureWorktree = registerRepo(project, bareProjectDir.resolve("feature"))
      featureWorktree.ensureWorkingTreesUpToDateForTests()

      assertThat(GitWorkingTreeDialog.resolveProjectNameBase(featureWorktree).name)
        .describedAs("A working tree of a bare repository must suggest the enclosing project directory, not the bare git directory's own name")
        .isEqualTo("bare-project")
    }
}

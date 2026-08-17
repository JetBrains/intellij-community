// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import com.intellij.ide.RecentProjectMetaInfo
import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.vcs.git.repo.GitRepositoriesHolder
import git4idea.repo.expectEvent
import git4idea.repo.getAndInit
import git4idea.test.GitSingleRepoContext
import git4idea.test.git
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
internal class GitWorkingTreeRecentProjectsTest {
  private val contextFixture = gitWorkingTreeSingleRepoFixture()
  private val context: GitSingleRepoContext get() = contextFixture.get()

  @Test
  fun `test deleted worktree is removed from recent projects`(): Unit = with(context) {
    val treeRoot = "treeRoot"
    val newWorkingTreeRootPath = testNioRoot.resolve(treeRoot)

    git("worktree add -B tree ../$treeRoot")
    repo.ensureWorkingTreesUpToDateForTests()
    val workingTree = repo.workingTreeHolder.getWorkingTrees().first { it.path.path.endsWith(treeRoot) }

    val recentProjectsManager = RecentProjectsManagerBase.getInstanceEx()
    recentProjectsManager.addRecentPath(workingTree.path.path, RecentProjectMetaInfo())
    assertThat(recentProjectsManager.hasPath(workingTree.path.path)).isTrue()

    try {
      val holder = GitRepositoriesHolder.getAndInit(project)
      holder.expectEvent(
        { GitWorkingTreesService.getInstance(project).deleteWorkingTree(project, workingTree, repo) },
        { event, _ -> event == GitRepositoriesHolder.UpdateType.WORKING_TREES_LOADED }
      )

      assertThat(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(newWorkingTreeRootPath)).isNull()
      assertThat(recentProjectsManager.hasPath(workingTree.path.path)).isFalse()
    }
    finally {
      recentProjectsManager.removePath(workingTree.path.path)
    }
  }
}

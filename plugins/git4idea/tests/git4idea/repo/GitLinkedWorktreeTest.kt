// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.vcs.Executor.cd
import git4idea.GitBranch
import git4idea.GitWorkingTree
import git4idea.test.git
import git4idea.test.initRepo
import git4idea.test.registerRepo
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path

class GitLinkedWorktreeTest : GitRepositoriesFrontendHolderTestBase() {
  private val branchName = "feature"
  private val mainRepoFileName = "mainRepo"
  val mainRepoPath: Path
    get() = testNioRoot.resolve(mainRepoFileName)

  override fun doCreateAndOpenProject(): Project {
    val projectRootPath = getProjectDirOrFile(true)
    return setUpProjectAndWorkingTree(testNioRoot, projectRootPath, mainRepoFileName, branchName)
  }

  override fun createRepository(): GitRepository {
    return registerRepo(project, projectNioRoot)
  }

  fun `test creating a worktree on a main repo`() {
    doTestWorkingTreeCreation(
      mainRepoPath,
      GitWorkingTree(mainRepoPath.toString(), "refs/heads/master", true, false),
      GitWorkingTree(repo.root.path, GitBranch.REFS_HEADS_PREFIX + branchName, false, true),
    )
  }

  companion object {
    fun setUpProjectAndWorkingTree(
      testNioRoot: Path,
      projectRootPath: Path,
      mainRepoRelativePath: String,
      branchName: String,
    ): Project {
      val mainRepoPath = testNioRoot.resolve(mainRepoRelativePath)
      initRepo(null, mainRepoPath, true)

      cd(mainRepoPath)
      git(null, "worktree add -B $branchName ../${projectRootPath.fileName}")
      Files.createDirectories(projectRootPath.resolve(Project.DIRECTORY_STORE_FOLDER))
      return runBlocking {
        ProjectManagerEx.getInstanceEx().openProjectAsync(projectIdentityFile = projectRootPath, options = OpenProjectTask {})!!
      }
    }
  }
}
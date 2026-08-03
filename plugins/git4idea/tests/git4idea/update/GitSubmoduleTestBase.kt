// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.update

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.Executor.cd
import git4idea.config.GitSaveChangesPolicy
import git4idea.test.GitPlatformTest
import git4idea.test.git
import git4idea.test.gitInit
import git4idea.test.setupDefaultUsername
import git4idea.test.tac
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

abstract class GitSubmoduleTestBase : GitPlatformTest() {
  override fun getDefaultSaveChangesPolicy(): GitSaveChangesPolicy = GitSaveChangesPolicy.STASH

  protected fun createPlainRepo(repoName: String): RepositoryAndParent = createPlainRepo(project, testNioRoot, repoName)

  protected fun addSubmodule(superProject: Path, submoduleUrl: Path, relativePath: String? = null): Path =
    addSubmodule(project, superProject, submoduleUrl, relativePath)
}

data class RepositoryAndParent(val name: String, val local: Path, val remote: Path)

private val LOG = logger<GitSubmoduleTestBase>()

internal fun createPlainRepo(project: Project, testRoot: Path, repoName: String): RepositoryAndParent {
  LOG.info("----- creating plain repository $repoName -----")
  cd(testRoot)
  gitInit(project, repoName)
  val repoDir = testRoot.resolve(repoName)
  cd(repoDir)
  setupDefaultUsername(project)
  tac(project, "initial.txt", "initial")
  val parentName = "$repoName.git"
  git(project, "remote add origin $testRoot/$parentName")

  cd(testRoot)
  gitInit(project, "--bare", parentName)
  cd(repoDir)
  git(project, "push -u origin master")
  return RepositoryAndParent(repoName, repoDir, testRoot.resolve(parentName))
}

internal fun addSubmodule(project: Project, superProject: Path, submoduleUrl: Path, relativePath: String? = null): Path {
  LOG.info("----- adding submodule [$submoduleUrl] to [$superProject] ${relativePath?.let { "at $it " } ?: ""}-----")
  cd(superProject)
  git(project, "submodule add ${submoduleUrl.invariantSeparatorsPathString} ${relativePath ?: ""}")
  git(project, "commit -m 'Added submodule lib'")
  git(project, "push origin master")
  val submodule = superProject.resolve(relativePath ?: submoduleUrl.fileName.toString())
  cd(submodule)
  setupDefaultUsername(project)
  return submodule
}

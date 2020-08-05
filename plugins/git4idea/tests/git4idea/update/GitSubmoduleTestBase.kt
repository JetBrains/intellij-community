// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.update

import com.intellij.openapi.vcs.Executor.cd
import com.intellij.util.io.systemIndependentPath
import git4idea.config.GitSaveChangesPolicy
import git4idea.test.GitPlatformTest
import git4idea.test.git
import git4idea.test.setupDefaultUsername
import git4idea.test.tac
import java.nio.file.Path

abstract class GitSubmoduleTestBase : GitPlatformTest() {
  override fun getDefaultSaveChangesPolicy(): GitSaveChangesPolicy = GitSaveChangesPolicy.STASH

  protected fun createPlainRepo(repoName: String): RepositoryAndParent {
    LOG.info("----- creating plain repository $repoName -----")
    cd(testRoot)
    git("init $repoName")
    val repoDir = testNioRoot.resolve(repoName)
    cd(repoDir)
    setupDefaultUsername()
    tac("initial.txt", "initial")
    val parentName = "$repoName.git"
    git("remote add origin ${testNioRoot}/$parentName")

    cd(testRoot)
    git("init --bare $parentName")
    cd(repoDir)
    git("push -u origin master")
    return RepositoryAndParent(repoName, repoDir, testNioRoot.resolve(parentName))
  }

  protected fun addSubmodule(superProject: Path, submoduleUrl: Path, relativePath: String? = null): Path {
    LOG.info("----- adding submodule [$submoduleUrl] to [$superProject] ${relativePath?.let {"at $it "} ?: ""}-----")
    cd(superProject)
    git("submodule add ${submoduleUrl.systemIndependentPath} ${relativePath ?: ""}")
    git("commit -m 'Added submodule lib'")
    git("push origin master")
    val submodule = superProject.resolve(relativePath ?: submoduleUrl.fileName.toString())
    cd(submodule)
    setupDefaultUsername()
    return submodule
  }

  protected data class RepositoryAndParent(val name: String, val local: Path, val remote: Path)
}
/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package git4idea.update

import com.intellij.dvcs.DvcsUtil.getShortRepositoryName
import com.intellij.openapi.util.io.FileUtil.getRelativePath
import com.intellij.openapi.vcs.Executor.cd
import com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile
import git4idea.repo.GitRepository
import git4idea.repo.GitSubmoduleInfo
import git4idea.test.GitPlatformTest
import git4idea.test.git
import git4idea.test.registerRepo
import git4idea.test.tac
import java.io.File

/**
 * Main project with 3 submodules, one of which is a submodule of another.
 * ```
 * project
 *   |.git/
 *   |elder/
 *   |  |.git
 *   |  |grandchild/
 *   |  | |.git
 *   |lib/
 *   |  |younger/
 *   |  | |.git
 * ```
 */
class GitSubmoduleTest : GitPlatformTest() {
  private lateinit var mainRepo: GitRepository
  private lateinit var elderRepo: GitRepository
  private lateinit var youngerRepo: GitRepository
  private lateinit var grandchildRepo: GitRepository

  private lateinit var grandchild: Repos
  private lateinit var elder: Repos
  private lateinit var younger: Repos
  private lateinit var main: Repos

  override fun setUp() {
    super.setUp()
    
    setUpRepositoryStructure()
  }

  fun `test submodules are properly detected`() {
    myGitRepositoryManager.updateAllRepositories()

    assertNoSubmodules(grandchildRepo)
    assertNoSubmodules(youngerRepo)
    assertSubmodules(elderRepo, listOf(grandchildRepo))
    assertSubmodules(mainRepo, listOf(elderRepo, youngerRepo))
  }

  private fun setUpRepositoryStructure() {
    grandchild = createPlainRepo("grandchild")
    younger = createPlainRepo("younger")
    elder = createPlainRepo("elder")
    addSubmodule(elder.local, grandchild.remote)

    // setup project
    mainRepo = createRepository(myProjectPath)
    val parent = prepareRemoteRepo(mainRepo)
    git("push -u origin master")
    main = Repos("parent", File(myProjectPath), parent)

    elderRepo = addSubmoduleInProject(elder.remote, elder.name)
    youngerRepo = addSubmoduleInProject(younger.remote, younger.name, "lib/younger")
    git(mainRepo, "submodule update --init --recursive") // this initializes the grandchild submodule
    grandchildRepo = registerRepo(myProject, "${myProjectPath}/elder/grandchild")
    git(grandchildRepo, "checkout master") // git submodule is initialized in detached HEAD state by default
  }

  private fun addSubmodule(superProject: File, submoduleUrl: File, relativePath: String? = null) {
    cd(superProject)
    git("submodule add ${submoduleUrl.path} ${relativePath ?: ""}")
    git("commit -m 'Added submodule lib'")
    git("push origin master")
  }

  /**
   * Adds the submodule to the given repository, pushes this change to the upstream,
   * and registers the repository as a VCS mapping.
   */
  private fun addSubmoduleInProject(submoduleUrl: File, moduleName: String, relativePath: String? = null): GitRepository {
    addSubmodule(File(myProjectPath), submoduleUrl, relativePath)
    val rootPath = "${myProjectPath}/${relativePath ?: moduleName}"
    return registerRepo(myProject, rootPath)
  }

  private fun createPlainRepo(moduleName: String): Repos {
    cd(myTestRoot)
    git("init $moduleName")
    val child = File(myTestRoot, moduleName)
    cd(child)
    tac("initial.txt", "initial")
    val parent = "$moduleName.git"
    git("remote add origin ${myTestRoot}/$parent")

    cd(myTestRoot)
    git("init --bare $parent")
    cd(child)
    git("push -u origin master")
    return Repos(moduleName, child, File(myTestRoot, parent))
  }

  private fun assertSubmodules(repo: GitRepository, expectedSubmodules: List<GitRepository>) {
    assertSubmodulesInfo(repo, expectedSubmodules)
    assertSameElements("Submodules identified incorrectly for ${getShortRepositoryName(repo)}",
                       myGitRepositoryManager.getDirectSubmodules(repo), expectedSubmodules)
  }

  private fun assertSubmodulesInfo(repo: GitRepository, expectedSubmodules: List<GitRepository>) {
    val expectedInfos = expectedSubmodules.map {
      val url = it.remotes.first().firstUrl!!
      GitSubmoduleInfo(getRelativePath(virtualToIoFile(repo.root), virtualToIoFile(it.root))!!, url)
    }
    assertSameElements("Submodules were read incorrectly for ${getShortRepositoryName(repo)}", repo.submodules, expectedInfos)
  }

  private fun assertNoSubmodules(repo: GitRepository) {
    assertTrue("No submodules expected, but found: ${repo.submodules}", repo.submodules.isEmpty())
  }

  private data class Repos(val name: String, val local: File, val remote: File)
}
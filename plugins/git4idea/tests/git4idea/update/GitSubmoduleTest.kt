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

import com.intellij.dvcs.DvcsUtil.getPushSupport
import com.intellij.dvcs.DvcsUtil.getShortRepositoryName
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.util.io.FileUtil.getRelativePath
import com.intellij.openapi.vcs.Executor.cd
import com.intellij.openapi.vcs.update.UpdatedFiles
import com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile
import git4idea.config.UpdateMethod
import git4idea.push.GitPushOperation
import git4idea.push.GitPushSupport
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import git4idea.repo.GitSubmoduleInfo
import git4idea.test.*
import java.io.File
import java.util.*

/**
 * Main project with 3 submodules, one of which is a submodule of another.
 * ```
 * project
 *   |.git/
 *   |alib/
 *   |  |younger/
 *   |  | |.git
 *   |elder/
 *   |  |.git
 *   |  |grandchild/
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
    myGitRepositoryManager.updateAllRepositories()
  }

  fun `test submodules are properly detected`() {
    assertNoSubmodules(grandchildRepo)
    assertNoSubmodules(youngerRepo)
    assertSubmodules(elderRepo, listOf(grandchildRepo))
    assertSubmodules(mainRepo, listOf(elderRepo, youngerRepo))
  }

  fun `test submodules are updated before superprojects`() {
    val bro = prepareSecondClone()
    commitAndPushFromSecondClone(bro) // remote commit to overcome "nothing to do"

    // hook the merge to watch the order
    val reposInActualOrder = mutableListOf<GitRepository>()
    myGit.mergeListener = {
      reposInActualOrder.add(it)
    }

    val updateProcess = GitUpdateProcess(myProject, EmptyProgressIndicator(), allRepositories(), UpdatedFiles.create(), false, true)
    val result = updateProcess.update(UpdateMethod.MERGE)
    assertEquals("Incorrect update result", GitUpdateResult.SUCCESS, result)
    assertOrder(reposInActualOrder)
  }

  fun `test dependency comparator`() {
    val comparator = GitRepositoryManager.DEPENDENCY_COMPARATOR
    infix operator fun GitRepository.compareTo(other: GitRepository) = comparator.compare(this, other)

    //Expected: grandchild <- younger <- elder <- main

    assertTrue(grandchildRepo < elderRepo)
    assertTrue(grandchildRepo < mainRepo)
    assertTrue("grandchild must be < youngerRepo to conform transitivity", grandchildRepo < youngerRepo)

    assertTrue(elderRepo < mainRepo)
    assertTrue("repos of the same level of submodularity must be compared by path", elderRepo > youngerRepo)
    assertTrue(mainRepo > youngerRepo)

    assertOrderedEquals(allRepositories().sortedWith(comparator), orderedRepositories())
  }

  fun `test submodules are pushed before superprojects`() {
    allRepositories().forEach {
      cd(it)
      tac("f.txt")
    }

    val pushSpecs = allRepositories().map {
      it to makePushSpec(it, "master", "origin/master")
    }.toMap()

    val reposInActualOrder = mutableListOf<GitRepository>()
    myGit.pushListener = {
      reposInActualOrder.add(it)
    }

    GitPushOperation(myProject, getPushSupport(myVcs) as GitPushSupport, pushSpecs, null, false, false).execute()
    assertOrder(reposInActualOrder)
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
    youngerRepo = addSubmoduleInProject(younger.remote, younger.name, "alib/younger")
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

  // second clone of the whole project with submodules
  private fun prepareSecondClone(): File {
    cd(myTestRoot)
    git("clone --recurse-submodules parent.git bro")
    val bro = File(myTestRoot, "bro")
    return bro
  }

  private fun commitAndPushFromSecondClone(bro: File) {
    listOf(grandchild.local, elder.local, younger.local, bro).forEach {
      cd(it)
      tacp("g.txt")
    }
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

  private fun assertOrder(reposInActualOrder: List<GitRepository>) {
    assertOrderedEquals("Repositories were processed in incorrect order", reposInActualOrder, orderedRepositories())
  }

  private fun allRepositories(): List<GitRepository> {
    val list = mutableListOf(grandchildRepo, elderRepo, youngerRepo, mainRepo)
    Collections.shuffle(list)
    return list
  }

  private fun orderedRepositories() = listOf(grandchildRepo, youngerRepo, elderRepo, mainRepo)

  private data class Repos(val name: String, val local: File, val remote: File)
}

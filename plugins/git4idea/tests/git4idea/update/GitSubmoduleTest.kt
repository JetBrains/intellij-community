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
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtil.getRelativePath
import com.intellij.openapi.vcs.Executor.cd
import com.intellij.openapi.vcs.update.UpdatedFiles
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile
import git4idea.config.GitVersion
import git4idea.config.UpdateMethod
import git4idea.push.GitPushOperation
import git4idea.push.GitPushSupport
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import git4idea.repo.GitSubmoduleInfo
import git4idea.test.*
import org.junit.Assume.assumeTrue
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
    repositoryManager.updateAllRepositories()
  }

  fun `test submodules are properly detected`() {
    assertNoSubmodules(grandchildRepo)
    assertNoSubmodules(youngerRepo)
    assertSubmodules(elderRepo, listOf(grandchildRepo))
    assertSubmodules(mainRepo, listOf(elderRepo, youngerRepo))
  }

  fun `test submodules are updated before superprojects`() {
    assumeTrue("Not testing: no --recurse-submodules flag in ${vcs.version}", vcs.version.isLaterOrEqual(GitVersion(1, 7, 4, 0)))

    val bro = prepareSecondClone()
    commitAndPushFromSecondClone(bro) // remote commit to overcome "nothing to do"

    // hook the merge to watch the order
    val reposInActualOrder = mutableListOf<GitRepository>()
    git.mergeListener = {
      reposInActualOrder.add(it)
    }

    val updateProcess = GitUpdateProcess(project, EmptyProgressIndicator(), allRepositories(), UpdatedFiles.create(), false, true)
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
    git.pushListener = {
      reposInActualOrder.add(it)
    }

    GitPushOperation(project, getPushSupport(vcs) as GitPushSupport, pushSpecs, null, false, false).execute()
    assertOrder(reposInActualOrder)
  }

  private fun setUpRepositoryStructure() {
    grandchild = createPlainRepo("grandchild")
    younger = createPlainRepo("younger")
    elder = createPlainRepo("elder")
    addSubmodule(elder.local, grandchild.remote)

    // setup project
    mainRepo = createRepository(projectPath)
    val parent = prepareRemoteRepo(mainRepo)
    git("push -u origin master")
    main = Repos("parent", File(projectPath), parent)

    elderRepo = addSubmoduleInProject(elder.remote, elder.name)
    youngerRepo = addSubmoduleInProject(younger.remote, younger.name, "alib/younger")
    mainRepo.git("submodule update --init --recursive") // this initializes the grandchild submodule
    grandchildRepo = registerRepo(project, "${projectPath}/elder/grandchild")
    cd(grandchildRepo)
    setupDefaultUsername()
    grandchildRepo.git("checkout master") // git submodule is initialized in detached HEAD state by default
  }

  private fun addSubmodule(superProject: File, submoduleUrl: File, relativePath: String? = null) {
    cd(superProject)
    git("submodule add ${FileUtil.toSystemIndependentName(submoduleUrl.path)} ${relativePath ?: ""}")
    git("commit -m 'Added submodule lib'")
    git("push origin master")
  }

  /**
   * Adds the submodule to the given repository, pushes this change to the upstream,
   * and registers the repository as a VCS mapping.
   */
  private fun addSubmoduleInProject(submoduleUrl: File, moduleName: String, relativePath: String? = null): GitRepository {
    addSubmodule(File(projectPath), submoduleUrl, relativePath)
    val rootPath = "${projectPath}/${relativePath ?: moduleName}"
    cd(rootPath)
    refresh(LocalFileSystem.getInstance().refreshAndFindFileByPath(rootPath)!!)
    setupDefaultUsername()
    return registerRepo(project, rootPath)
  }

  private fun createPlainRepo(moduleName: String): Repos {
    cd(testRoot)
    git("init $moduleName")
    val child = File(testRoot, moduleName)
    cd(child)
    setupDefaultUsername()
    tac("initial.txt", "initial")
    val parent = "$moduleName.git"
    git("remote add origin ${testRoot}/$parent")

    cd(testRoot)
    git("init --bare $parent")
    cd(child)
    git("push -u origin master")
    return Repos(moduleName, child, File(testRoot, parent))
  }

  // second clone of the whole project with submodules
  private fun prepareSecondClone(): File {
    cd(testRoot)
    git("clone --recurse-submodules parent.git bro")
    val broDir = File(testRoot, "bro")
    cd(broDir)
    setupDefaultUsername()
    return broDir
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
                       repositoryManager.getDirectSubmodules(repo), expectedSubmodules)
  }

  private fun assertSubmodulesInfo(repo: GitRepository, expectedSubmodules: List<GitRepository>) {
    val expectedInfos = expectedSubmodules.map {
      val url = it.remotes.first().firstUrl!!
      GitSubmoduleInfo(FileUtil.toSystemIndependentName(getRelativePath(virtualToIoFile(repo.root), virtualToIoFile(it.root))!!), url)
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

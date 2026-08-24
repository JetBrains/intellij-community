// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.update

import com.intellij.dvcs.DvcsUtil.getPushSupport
import com.intellij.dvcs.DvcsUtil.getShortRepositoryName
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtil.getRelativePath
import com.intellij.openapi.vcs.Executor
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.vcs.test.refresh
import git4idea.config.GitSaveChangesPolicy
import git4idea.push.GitPushOperation
import git4idea.push.GitPushSupport
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import git4idea.repo.GitSubmoduleInfo
import git4idea.repo.getDirectSubmodules
import git4idea.test.GitPlatformTestContext
import git4idea.test.cd
import git4idea.test.createRepository
import git4idea.test.git
import git4idea.test.gitPlatformContextFixture
import git4idea.test.makePushSpec
import git4idea.test.prepareRemoteRepo
import git4idea.test.registerRepo
import git4idea.test.runUnderProgress
import git4idea.test.setupDefaultUsername
import git4idea.test.tac
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.util.Collections

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
@TestApplication
internal class GitComplexSubmoduleTest {
  private val contextFixture = gitPlatformContextFixture(saveChangesPolicy = GitSaveChangesPolicy.STASH)
  private val context: GitPlatformTestContext get() = contextFixture.get()

  private lateinit var mainRepo: GitRepository
  private lateinit var elderRepo: GitRepository
  private lateinit var youngerRepo: GitRepository
  private lateinit var grandchildRepo: GitRepository

  private lateinit var grandchild: RepositoryAndParent
  private lateinit var elder: RepositoryAndParent
  private lateinit var younger: RepositoryAndParent
  private lateinit var main: RepositoryAndParent

  @BeforeEach
  fun setUpRepositoryStructure() {
    with(context) {
      // create separate git local and remote repositories outside of the project
      grandchild = createPlainRepo(project, testNioRoot, "grandchild")
      younger = createPlainRepo(project, testNioRoot, "younger")
      elder = createPlainRepo(project, testNioRoot, "elder")
      addSubmodule(project, elder.local, grandchild.remote)

      // setup project
      mainRepo = createRepository(project, projectPath)
      val parent = prepareRemoteRepo(mainRepo)
      git("push -u origin master")
      main = RepositoryAndParent("parent", projectNioRoot, parent)

      elderRepo = addSubmoduleInProject(elder.remote, elder.name)
      youngerRepo = addSubmoduleInProject(younger.remote, younger.name, "alib/younger")
      mainRepo.git("submodule update --init --recursive") // this initializes the grandchild submodule
      grandchildRepo = registerRepo(project, projectNioRoot.resolve("elder/grandchild"))
      cd(grandchildRepo)
      setupDefaultUsername()
      grandchildRepo.git("checkout master") // git submodule is initialized in detached HEAD state by default
    }
  }

  /**
   * Adds the submodule to the given repository, pushes this change to the upstream,
   * and registers the repository as a VCS mapping.
   */
  private fun GitPlatformTestContext.addSubmoduleInProject(submoduleUrl: Path, moduleName: String, relativePath: String? = null): GitRepository {
    addSubmodule(project, projectNioRoot, submoduleUrl, relativePath)
    val rootPath = projectNioRoot.resolve(relativePath ?: moduleName)
    Executor.cd(rootPath)
    refresh(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(rootPath)!!)
    setupDefaultUsername()
    return registerRepo(project, rootPath)
  }

  @Test
  fun `test submodules are properly detected`() {
    assertNoSubmodules(grandchildRepo)
    assertNoSubmodules(youngerRepo)
    assertSubmodules(elderRepo, listOf(grandchildRepo))
    assertSubmodules(mainRepo, listOf(elderRepo, youngerRepo))
  }

  @Test
  fun `test dependency comparator`() {
    val comparator = GitRepositoryManager.DEPENDENCY_COMPARATOR
    infix operator fun GitRepository.compareTo(other: GitRepository) = comparator.compare(this, other)

    //Expected: grandchild <- younger <- elder <- main

    assertThat(grandchildRepo < elderRepo).isTrue()
    assertThat(grandchildRepo < mainRepo).isTrue()
    assertThat(grandchildRepo < youngerRepo).describedAs("grandchild must be < youngerRepo to conform transitivity").isTrue()

    assertThat(elderRepo < mainRepo).isTrue()
    assertThat(elderRepo > youngerRepo).describedAs("repos of the same level of submodularity must be compared by path").isTrue()
    assertThat(mainRepo > youngerRepo).isTrue()

    assertThat(allRepositories().sortedWith(comparator)).containsExactlyElementsOf(orderedRepositories())
  }

  @Test
  fun `test submodules are pushed before superprojects`(): Unit = with(context) {
    allRepositories().forEach {
      cd(it)
      tac("f.txt")
    }

    val pushSpecs = allRepositories().associateWith { makePushSpec(it, "master", "origin/master") }

    val reposInActualOrder = mutableListOf<GitRepository>()
    git.pushListener = {
      reposInActualOrder.add(it)
    }

    runUnderProgress {
      GitPushOperation(project, getPushSupport(vcs) as GitPushSupport, pushSpecs, null, false, false).execute()
    }
    assertThat(reposInActualOrder)
      .describedAs("Repositories were processed in incorrect order")
      .containsExactlyElementsOf(orderedRepositories())
  }

  private fun assertSubmodules(repo: GitRepository, expectedSubmodules: List<GitRepository>) {
    assertSubmodulesInfo(repo, expectedSubmodules)
    assertThat(repo.getDirectSubmodules())
      .describedAs("Submodules identified incorrectly for ${getShortRepositoryName(repo)}")
      .containsExactlyInAnyOrderElementsOf(expectedSubmodules)
  }

  private fun assertSubmodulesInfo(repo: GitRepository, expectedSubmodules: List<GitRepository>) {
    val expectedInfos = expectedSubmodules.map {
      val url = it.remotes.first().firstUrl!!
      GitSubmoduleInfo(FileUtil.toSystemIndependentName(getRelativePath(virtualToIoFile(repo.root), virtualToIoFile(it.root))!!), url)
    }
    assertThat(repo.submodules)
      .describedAs("Submodules were read incorrectly for ${getShortRepositoryName(repo)}")
      .containsExactlyInAnyOrderElementsOf(expectedInfos)
  }

  private fun assertNoSubmodules(repo: GitRepository) {
    assertThat(repo.submodules).describedAs("No submodules expected").isEmpty()
  }

  private fun allRepositories(): List<GitRepository> {
    val list = mutableListOf(grandchildRepo, elderRepo, youngerRepo, mainRepo)
    Collections.shuffle(list)
    return list
  }

  private fun orderedRepositories() = listOf(grandchildRepo, youngerRepo, elderRepo, mainRepo)
}
/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package git4idea.test

import com.intellij.openapi.components.service
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.AbstractVcsHelper
import com.intellij.openapi.vcs.Executor
import com.intellij.openapi.vcs.Executor.cd
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.VcsShowConfirmationOption
import com.intellij.testFramework.vcs.AbstractVcsTestCase
import com.intellij.vcs.log.VcsFullCommitDetails
import com.intellij.vcs.log.impl.VcsLogUtil
import com.intellij.vcs.test.VcsPlatformTest
import com.intellij.vcs.test.overrideService
import git4idea.DialogManager
import git4idea.GitUtil
import git4idea.GitVcs
import git4idea.commands.Git
import git4idea.commands.GitHandler
import git4idea.config.GitVcsSettings
import git4idea.log.GitLogProvider
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import java.io.File

abstract class GitPlatformTest : VcsPlatformTest() {

  protected lateinit var myGitRepositoryManager: GitRepositoryManager
  protected lateinit var myGitSettings: GitVcsSettings
  protected lateinit var myGit: TestGitImpl
  protected lateinit var myVcs: GitVcs
  protected lateinit var myDialogManager: TestDialogManager
  protected lateinit var vcsHelper: MockVcsHelper
  protected lateinit var logProvider: GitLogProvider

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()

    myGitSettings = GitVcsSettings.getInstance(myProject)
    myGitSettings.appSettings.setPathToGit(gitExecutable())

    myDialogManager = service<DialogManager>() as TestDialogManager
    vcsHelper = overrideService<AbstractVcsHelper, MockVcsHelper>(myProject)

    myGitRepositoryManager = GitUtil.getRepositoryManager(myProject)
    myGit = overrideService<Git, TestGitImpl>()
    myVcs = GitVcs.getInstance(myProject)!!
    myVcs.doActivate()

    logProvider = findGitLogProvider(myProject)

    assumeSupportedGitVersion(myVcs)
    addSilently()
    removeSilently()
  }

  @Throws(Exception::class)
  override fun tearDown() {
    try {
      if (wasInit { myDialogManager }) myDialogManager.cleanup()
      if (wasInit { myGit }) myGit.reset()
      if (wasInit { myGitSettings }) myGitSettings.appSettings.setPathToGit(null)
    }
    finally {
      super.tearDown()
    }
  }

  override fun getDebugLogCategories(): Collection<String> {
    return super.getDebugLogCategories().plus(listOf("#" + Executor::class.java.name,
                                                     "#" + GitHandler::class.java.name,
                                                     "#output." + GitHandler::class.java.name))
  }

  protected open fun createRepository(rootDir: String): GitRepository {
    return createRepository(myProject, rootDir)
  }

  /**
   * Clones the given source repository into a bare parent.git and adds the remote origin.
   */
  protected fun prepareRemoteRepo(source: GitRepository, target: File = File(myTestRoot, "parent.git")): File {
    cd(myTestRoot)
    git("clone --bare '${source.root.path}' ${target.path}")
    cd(source)
    git("remote add origin '${target.path}'")
    return target
  }

  /**
   * Creates 3 repositories: a bare "parent" repository, and two clones of it.
   *
   * One of the clones - "bro" - is outside of the project.
   * Another one is inside the project, is registered as a Git root, and is represented by [GitRepository].
   *
   * Parent and bro are created just inside the [testRoot](myTestRoot).
   * The main clone is created at [repoRoot], which is assumed to be inside the project.
   */
  protected fun setupRepositories(repoRoot: String, parentName: String, broName: String): ReposTrinity {
    val parentRepo = createParentRepo(parentName)
    val broRepo = createBroRepo(broName, parentRepo)

    val repository = createRepository(myProject, repoRoot)
    cd(repository)
    git("remote add origin " + parentRepo.path)
    git("push --set-upstream origin master:master")

    Executor.cd(broRepo.path)
    git("pull")

    return ReposTrinity(repository, parentRepo, broRepo)
  }

  private fun createParentRepo(parentName: String): File {
    Executor.cd(myTestRoot)
    git("init --bare $parentName.git")
    return File(myTestRoot, parentName + ".git")
  }

  private fun createBroRepo(broName: String, parentRepo: File): File {
    Executor.cd(myTestRoot)
    git("clone " + parentRepo.name + " " + broName)
    return File(myTestRoot, broName)
  }

  protected fun doActionSilently(op: VcsConfiguration.StandardConfirmation) {
    AbstractVcsTestCase.setStandardConfirmation(myProject, GitVcs.NAME, op, VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY)
  }

  protected fun addSilently() {
    doActionSilently(VcsConfiguration.StandardConfirmation.ADD)
  }

  protected fun removeSilently() {
    doActionSilently(VcsConfiguration.StandardConfirmation.REMOVE)
  }

  protected fun installHook(gitDir: File, hookName: String, hookContent: String) {
    val hookFile = File(gitDir, "hooks/$hookName")
    FileUtil.writeToFile(hookFile, hookContent)
    hookFile.setExecutable(true, false)
  }

  protected fun readDetails(hashes: List<String>): List<VcsFullCommitDetails> = VcsLogUtil.getDetails(logProvider, myProjectRoot, hashes)

  protected fun readDetails(hash: String) = readDetails(listOf(hash)).first()

  protected fun `do nothing on merge`() {
    vcsHelper.onMerge{}
  }

  protected fun `mark as resolved on merge`() {
    vcsHelper.onMerge { git("add -u .") }
  }

  protected fun `assert merge dialog was shown`() {
    assertTrue("Merge dialog was not shown", vcsHelper.mergeDialogWasShown())
  }

  protected fun `assert commit dialog was shown`() {
    assertTrue("Commit dialog was not shown", vcsHelper.commitDialogWasShown())
  }

  protected data class ReposTrinity(val projectRepo: GitRepository, val parent: File, val bro: File)

}

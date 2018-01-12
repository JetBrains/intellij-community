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
import com.intellij.testFramework.RunAll
import com.intellij.testFramework.vcs.AbstractVcsTestCase
import com.intellij.util.ThrowableRunnable
import com.intellij.vcs.log.VcsFullCommitDetails
import com.intellij.vcs.log.util.VcsLogUtil
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
import git4idea.test.GitPlatformTest.ConfigScope.GLOBAL
import git4idea.test.GitPlatformTest.ConfigScope.SYSTEM
import java.io.File

abstract class GitPlatformTest : VcsPlatformTest() {

  protected lateinit var repositoryManager: GitRepositoryManager
  protected lateinit var settings: GitVcsSettings
  protected lateinit var git: TestGitImpl
  protected lateinit var vcs: GitVcs
  protected lateinit var dialogManager: TestDialogManager
  protected lateinit var vcsHelper: MockVcsHelper
  protected lateinit var logProvider: GitLogProvider

  private lateinit var credentialHelpers: Map<ConfigScope, List<String>>

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()

    dialogManager = service<DialogManager>() as TestDialogManager
    vcsHelper = overrideService<AbstractVcsHelper, MockVcsHelper>(project)

    repositoryManager = GitUtil.getRepositoryManager(project)
    git = overrideService<Git, TestGitImpl>()
    vcs = GitVcs.getInstance(project)
    vcs.doActivate()

    settings = GitVcsSettings.getInstance(project)
    settings.appSettings.setPathToGit(gitExecutable())
    vcs.checkVersion()

    logProvider = findGitLogProvider(project)

    assumeSupportedGitVersion(vcs)
    addSilently()
    removeSilently()

    credentialHelpers = if (hasRemoteGitOperation()) readAndResetCredentialHelpers() else emptyMap()
  }

  @Throws(Exception::class)
  override fun tearDown() {
    RunAll()
      .append(ThrowableRunnable { restoreCredentialHelpers() })
      .append(ThrowableRunnable { if (wasInit { dialogManager }) dialogManager.cleanup() })
      .append(ThrowableRunnable { if (wasInit { git }) git.reset() })
      .append(ThrowableRunnable { if (wasInit { settings }) settings.appSettings.setPathToGit(null) })
      .append(ThrowableRunnable { super.tearDown() })
      .run()
  }

  override fun getDebugLogCategories(): Collection<String> {
    return super.getDebugLogCategories().plus(listOf("#" + Executor::class.java.name,
                                                     "#git4idea",
                                                     "#output." + GitHandler::class.java.name))
  }

  protected open fun hasRemoteGitOperation() = false

  protected open fun createRepository(rootDir: String): GitRepository {
    return createRepository(project, rootDir)
  }

  /**
   * Clones the given source repository into a bare parent.git and adds the remote origin.
   */
  protected fun prepareRemoteRepo(source: GitRepository, target: File = File(testRoot, "parent.git")): File {
    cd(testRoot)
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

    val repository = createRepository(project, repoRoot)
    cd(repository)
    git("remote add origin " + parentRepo.path)
    git("push --set-upstream origin master:master")

    Executor.cd(broRepo.path)
    git("pull")

    return ReposTrinity(repository, parentRepo, broRepo)
  }

  private fun createParentRepo(parentName: String): File {
    Executor.cd(testRoot)
    git("init --bare $parentName.git")
    return File(testRoot, parentName + ".git")
  }

  protected fun createBroRepo(broName: String, parentRepo: File): File {
    Executor.cd(testRoot)
    git("clone " + parentRepo.name + " " + broName)
    cd(broName)
    setupDefaultUsername(project)
    return File(testRoot, broName)
  }

  private fun doActionSilently(op: VcsConfiguration.StandardConfirmation) {
    AbstractVcsTestCase.setStandardConfirmation(project, GitVcs.NAME, op, VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY)
  }

  private fun addSilently() {
    doActionSilently(VcsConfiguration.StandardConfirmation.ADD)
  }

  private fun removeSilently() {
    doActionSilently(VcsConfiguration.StandardConfirmation.REMOVE)
  }

  protected fun installHook(gitDir: File, hookName: String, hookContent: String) {
    val hookFile = File(gitDir, "hooks/$hookName")
    FileUtil.writeToFile(hookFile, hookContent)
    hookFile.setExecutable(true, false)
  }

  private fun readAndResetCredentialHelpers(): Map<ConfigScope, List<String>> {
    val system = readAndResetCredentialHelper(SYSTEM)
    val global = readAndResetCredentialHelper(GLOBAL)
    return mapOf(SYSTEM to system, GLOBAL to global)
  }

  private fun readAndResetCredentialHelper(scope: ConfigScope): List<String> {
    val values = git("config ${scope.param()} --get-all -z credential.helper", true).split("\u0000").filter{it.isNotBlank()}
    git("config ${scope.param()} --unset-all credential.helper", true)
    return values
  }

  private fun restoreCredentialHelpers() {
    credentialHelpers.forEach { scope, values ->
      values.forEach { git("config --add ${scope.param()} credential.helper ${it}", true) }
    }
  }

  protected fun readDetails(hashes: List<String>): List<VcsFullCommitDetails> = VcsLogUtil.getDetails(logProvider, projectRoot, hashes)

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


  private enum class ConfigScope {
    SYSTEM,
    GLOBAL;

    fun param() = "--${name.toLowerCase()}"
  }
}

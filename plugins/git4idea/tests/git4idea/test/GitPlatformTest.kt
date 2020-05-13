// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.test

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.AbstractVcsHelper
import com.intellij.openapi.vcs.Executor
import com.intellij.openapi.vcs.Executor.cd
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.VcsShowConfirmationOption
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.testFramework.RunAll
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.vcs.AbstractVcsTestCase
import com.intellij.util.ThrowableRunnable
import com.intellij.vcs.log.VcsFullCommitDetails
import com.intellij.vcs.log.util.VcsLogUtil
import com.intellij.vcs.test.VcsPlatformTest
import git4idea.DialogManager
import git4idea.GitUtil
import git4idea.GitVcs
import git4idea.commands.Git
import git4idea.commands.GitHandler
import git4idea.config.GitExecutableManager
import git4idea.config.GitVcsApplicationSettings
import git4idea.config.GitVcsSettings
import git4idea.config.GitSaveChangesPolicy
import git4idea.log.GitLogProvider
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import git4idea.test.GitPlatformTest.ConfigScope.GLOBAL
import git4idea.test.GitPlatformTest.ConfigScope.SYSTEM
import java.io.File

abstract class GitPlatformTest : VcsPlatformTest() {

  protected lateinit var repositoryManager: GitRepositoryManager
  protected lateinit var settings: GitVcsSettings
  protected lateinit var appSettings: GitVcsApplicationSettings
  protected lateinit var git: TestGitImpl
  protected lateinit var vcs: GitVcs
  protected lateinit var commitContext: CommitContext
  protected lateinit var dialogManager: TestDialogManager
  protected lateinit var vcsHelper: MockVcsHelper
  protected lateinit var logProvider: GitLogProvider

  private lateinit var credentialHelpers: Map<ConfigScope, List<String>>
  private var globalSslVerify: Boolean? = null

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()

    dialogManager = service<DialogManager>() as TestDialogManager
    vcsHelper = MockVcsHelper(myProject)
    project.replaceService(AbstractVcsHelper::class.java, vcsHelper, testRootDisposable)

    repositoryManager = GitUtil.getRepositoryManager(project)
    git = TestGitImpl()
    ApplicationManager.getApplication().replaceService(Git::class.java, git, testRootDisposable)
    vcs = GitVcs.getInstance(project)
    vcs.doActivate()
    commitContext = CommitContext()

    settings = GitVcsSettings.getInstance(project)
    appSettings = GitVcsApplicationSettings.getInstance()
    appSettings.setPathToGit(gitExecutable())
    GitExecutableManager.getInstance().testGitExecutableVersionValid(project)

    logProvider = findGitLogProvider(project)

    assumeSupportedGitVersion(vcs)
    addSilently()
    removeSilently()
    overrideDefaultSaveChangesPolicy()

    credentialHelpers = if (hasRemoteGitOperation()) readAndResetCredentialHelpers() else emptyMap()
    globalSslVerify = if (hasRemoteGitOperation()) readAndDisableSslVerifyGlobally() else null
  }

  @Throws(Exception::class)
  override fun tearDown() {
    RunAll()
      .append(ThrowableRunnable { restoreCredentialHelpers() })
      .append(ThrowableRunnable { restoreGlobalSslVerify() })
      .append(ThrowableRunnable { if (::dialogManager.isInitialized) dialogManager.cleanup() })
      .append(ThrowableRunnable { if (::git.isInitialized) git.reset() })
      .append(ThrowableRunnable { if (::settings.isInitialized) settings.appSettings.setPathToGit(null) })
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

  protected open fun getDefaultSaveChangesPolicy() : GitSaveChangesPolicy = GitSaveChangesPolicy.SHELVE

  private fun overrideDefaultSaveChangesPolicy() {
    settings.saveChangesPolicy = getDefaultSaveChangesPolicy()
  }

  /**
   * Clones the given source repository into a bare parent.git and adds the remote origin.
   */
  protected fun prepareRemoteRepo(source: GitRepository, target: File = File(testRoot, "parent.git"), remoteName: String = "origin"): File {
    cd(testRoot)
    git("clone --bare '${source.root.path}' ${target.path}")
    cd(source)
    git("remote add ${remoteName} '${target.path}'")
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

    cd(broRepo.path)
    git("pull")

    return ReposTrinity(repository, parentRepo, broRepo)
  }

  private fun createParentRepo(parentName: String): File {
    cd(testRoot)
    git("init --bare $parentName.git")
    return File(testRoot, "$parentName.git")
  }

  protected fun createBroRepo(broName: String, parentRepo: File): File {
    cd(testRoot)
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
    val values = git("config ${scope.param()} --get-all -z credential.helper", true).split("\u0000").filter { it.isNotBlank() }
    git("config ${scope.param()} --unset-all credential.helper", true)
    return values
  }

  private fun restoreCredentialHelpers() {
    credentialHelpers.forEach { (scope, values) ->
      values.forEach { git("config --add ${scope.param()} credential.helper ${it}", true) }
    }
  }

  private fun readAndDisableSslVerifyGlobally(): Boolean? {
    val value = git("config --global --get-all -z http.sslVerify", true)
        .split("\u0000")
        .singleOrNull { it.isNotBlank() }
        ?.toBoolean()
    git("config --global http.sslVerify false", true)
    return value
  }

  private fun restoreGlobalSslVerify() {
    if (globalSslVerify != null) {
      git("config --global http.sslVerify ${globalSslVerify}", true)
    }
    else {
      git("config --global --unset http.sslVerify", true)
    }
  }

  protected fun readDetails(hashes: List<String>): List<VcsFullCommitDetails> = VcsLogUtil.getDetails(logProvider, projectRoot, hashes)

  protected fun readDetails(hash: String) = readDetails(listOf(hash)).first()

  protected fun commit(changes: Collection<Change>) {
    val exceptions = vcs.checkinEnvironment!!.commit(changes.toList(), "comment", commitContext, mutableSetOf())
    exceptions?.forEach { fail("Exception during executing the commit: " + it.message) }
    updateChangeListManager()
  }

  protected fun `do nothing on merge`() {
    vcsHelper.onMerge {}
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

  protected fun assertNoChanges() {
    changeListManager.assertNoChanges()
  }

  protected fun assertChanges(changes: ChangesBuilder.() -> Unit): List<Change> {
    return changeListManager.assertChanges(changes)
  }

  protected fun assertChangesWithRefresh(changes: ChangesBuilder.() -> Unit): List<Change> {
    VcsDirtyScopeManager.getInstance(project).markEverythingDirty()
    changeListManager.ensureUpToDate()
    return changeListManager.assertChanges(changes)
  }

  protected data class ReposTrinity(val projectRepo: GitRepository, val parent: File, val bro: File)


  private enum class ConfigScope {
    SYSTEM,
    GLOBAL;

    fun param() = "--${name.toLowerCase()}"
  }
}

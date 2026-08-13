// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.test

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.AbstractVcsHelper
import com.intellij.openapi.vcs.Executor
import com.intellij.openapi.vcs.Executor.cd
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsShowConfirmationOption
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vcs.ex.PartialLocalLineStatusTracker
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.vcs.AbstractVcsTestCase
import com.intellij.util.ui.UIUtil
import com.intellij.vcs.log.VcsFullCommitDetails
import com.intellij.vcs.log.util.VcsLogUtil
import com.intellij.vcs.test.VcsPlatformTest
import com.intellij.vcs.test.VcsPlatformTestContext
import git4idea.DialogManager
import git4idea.GitUtil
import git4idea.GitVcs
import git4idea.commands.Git
import git4idea.commands.GitHandler
import git4idea.config.GitExecutableManager
import git4idea.config.GitSaveChangesPolicy
import git4idea.config.GitVcsApplicationSettings
import git4idea.config.GitVcsSettings
import git4idea.config.GitVersion
import git4idea.log.GitLogProvider
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import git4idea.test.GitPlatformTest.ConfigScope.GLOBAL
import git4idea.test.GitPlatformTest.ConfigScope.SYSTEM
import java.nio.file.Path
import java.util.Locale

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

  override fun initApplication() {
    super.initApplication()
    Registry.get("git.use.env.from.project.context").setValue(false)
  }

  override fun tearDown() {
    runAll(
      { restoreCredentialHelpers() },
      { restoreGlobalSslVerify() },
      { if (::dialogManager.isInitialized) dialogManager.cleanup() },
      { if (::git.isInitialized) git.reset() },
      { if (::settings.isInitialized) appSettings.setPathToGit(null) },
      { super.tearDown() }
    )
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
  protected fun prepareRemoteRepo(source: GitRepository, target: Path = testNioRoot.resolve("parent.git"), remoteName: String = "origin"): Path =
    prepareRemoteRepo(project, testNioRoot, source, target, remoteName)

  /**
   * Creates 3 repositories: a bare "parent" repository, and two clones of it.
   *
   * One of the clones - "bro" - is outside of the project.
   * Another one is inside the project, is registered as a Git root, and is represented by [GitRepository].
   *
   * Parent and bro are created just inside the [testRoot](myTestRoot).
   * The main clone is created at [repoRoot], which is assumed to be inside the project.
   */
  protected fun setupRepositories(repoRoot: String, parentName: String, broName: String): ReposTrinity =
    setupRepositories(project, testNioRoot, repoRoot, parentName, broName)

  protected fun createBroRepo(broName: String, parentRepo: Path): Path = createBroRepo(project, testNioRoot, broName, parentRepo)

  private fun doActionSilently(op: VcsConfiguration.StandardConfirmation) {
    AbstractVcsTestCase.setStandardConfirmation(project, GitVcs.NAME, op, VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY)
  }

  private fun addSilently() {
    doActionSilently(VcsConfiguration.StandardConfirmation.ADD)
  }

  private fun removeSilently() {
    doActionSilently(VcsConfiguration.StandardConfirmation.REMOVE)
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

  protected fun readDetails(vararg hashes: String): List<VcsFullCommitDetails> = VcsLogUtil.getDetails(logProvider, projectRoot, hashes.asList())

  protected fun commit(changes: Collection<Change>, commitMessage: String = "comment") {
    val exceptions = tryCommit(changes, commitMessage)
    exceptions?.forEach { fail("Exception during executing the commit: " + it.message) }
  }

  protected fun tryCommit(changes: Collection<Change>, commitMessage: String = "comment"): List<VcsException>? {
    val exceptions = vcs.checkinEnvironment!!.commit(changes.toList(), commitMessage, commitContext, mutableSetOf())
    updateChangeListManager()
    return exceptions
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

  /**
   * There are small differences between 'recursive' (old) and 'ort' (new) merge algorithms.
   */
  protected fun gitUsingOrtMergeAlg(): Boolean {
    return vcs.version.isLaterOrEqual(GitVersion(2, 34, 0, 0))
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

  protected fun updateUntrackedFiles(repo: GitRepository) {
    repo.untrackedFilesHolder.invalidate()
    runBlockingMaybeCancellable {
      repo.untrackedFilesHolder.awaitNotBusy()
    }
  }

  private enum class ConfigScope {
    SYSTEM,
    GLOBAL;

    fun param() = "--${name.lowercase(Locale.getDefault())}"
  }

  protected fun withPartialTracker(file: VirtualFile, newContent: String? = null, task: (Document, PartialLocalLineStatusTracker) -> Unit) {
    invokeAndWaitIfNeeded {
      val lstm = LineStatusTrackerManager.getInstance(project) as LineStatusTrackerManager

      val document = runReadAction { FileDocumentManager.getInstance().getDocument(file)!! }

      if (newContent != null) {
        runWriteAction {
          FileDocumentManager.getInstance().getDocument(file)!!.setText(newContent)
        }

        changeListManager.waitUntilRefreshed()
        UIUtil.dispatchAllInvocationEvents() // ensure `fileStatusesChanged` events are fired
      }

      lstm.requestTrackerFor(document, this)
      try {
        val tracker = lstm.getLineStatusTracker(file) as PartialLocalLineStatusTracker
        lstm.waitUntilBaseContentsLoaded()

        task(document, tracker)
      }
      finally {
        lstm.releaseTrackerFor(document, this)
      }
    }
  }
}

data class ReposTrinity(val projectRepo: GitRepository, val parent: Path, val bro: Path)

/**
 * Writes an executable Git hook into `<gitDir>/hooks/<hookName>`.
 */
internal fun installHook(gitDir: Path, hookName: String, hookContent: String) {
  val hookFile = gitDir.resolve("hooks/$hookName").toFile()
  hookFile.writeText(hookContent)
  hookFile.setExecutable(true, false)
}

/**
 * Clones the given source repository into a bare parent.git and adds the remote origin.
 */
internal fun prepareRemoteRepo(
  project: Project,
  testRoot: Path,
  source: GitRepository,
  target: Path = testRoot.resolve("parent.git"),
  remoteName: String = "origin",
): Path {
  cd(testRoot)
  git(project, "clone --bare '${source.root.path}' $target")
  cd(source)
  git(project, "remote add $remoteName '$target'")
  return target
}

/**
 * Creates 3 repositories: a bare "parent" repository, and two clones of it.
 *
 * One of the clones - "bro" - is outside of the project.
 * Another one is inside the project, is registered as a Git root, and is represented by [GitRepository].
 *
 * Parent and bro are created just inside the [testRoot].
 * The main clone is created at [repoRoot], which is assumed to be inside the project.
 */
internal fun setupRepositories(
  project: Project,
  testRoot: Path,
  repoRoot: String,
  parentName: String,
  broName: String,
): ReposTrinity {
  val parentRepo = createParentRepo(project, testRoot, parentName)
  val broRepo = createBroRepo(project, testRoot, broName, parentRepo)

  val repository = createRepository(project, repoRoot)
  cd(repository)
  git(project, "remote add origin $parentRepo")
  git(project, "push --set-upstream origin master:master")

  cd(broRepo)
  git(project, "pull")

  return ReposTrinity(repository, parentRepo, broRepo)
}

private fun createParentRepo(project: Project, testRoot: Path, parentName: String): Path {
  cd(testRoot)
  gitInit(project, "--bare", "$parentName.git")
  return testRoot.resolve("$parentName.git")
}

internal fun createBroRepo(project: Project, testRoot: Path, broName: String, parentRepo: Path): Path {
  cd(testRoot)
  git(project, "clone ${parentRepo.fileName} $broName")
  cd(broName)
  setupDefaultUsername(project)
  return testRoot.resolve(broName)
}

internal fun VcsPlatformTestContext.prepareRemoteRepo(
  source: GitRepository,
  target: Path = testNioRoot.resolve("parent.git"),
  remoteName: String = "origin",
): Path = prepareRemoteRepo(project, testNioRoot, source, target, remoteName)

internal fun VcsPlatformTestContext.setupRepositories(repoRoot: String, parentName: String, broName: String): ReposTrinity =
  setupRepositories(project, testNioRoot, repoRoot, parentName, broName)

internal fun VcsPlatformTestContext.createBroRepo(broName: String, parentRepo: Path): Path =
  createBroRepo(project, testNioRoot, broName, parentRepo)

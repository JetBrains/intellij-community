// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.test

import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.AbstractVcsHelper
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
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.registryKeyFixture
import com.intellij.testFramework.junit5.fixture.replacedServiceFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.testFramework.vcs.AbstractVcsTestCase
import com.intellij.util.application
import com.intellij.util.ui.UIUtil
import com.intellij.vcs.test.VcsPlatformTestContext
import com.intellij.vcs.test.updateChangeListManager
import git4idea.DialogManager
import git4idea.GitUtil
import git4idea.GitVcs
import git4idea.commands.Git
import git4idea.config.*
import git4idea.log.GitLogProvider
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import org.junit.jupiter.api.Assertions.fail
import java.util.*

interface GitPlatformTestContext : VcsPlatformTestContext {
  val repositoryManager: GitRepositoryManager
  val settings: GitVcsSettings
  val git: TestGitImpl
  val vcs: GitVcs
  val commitContext: CommitContext
  val dialogManager: TestDialogManager
  val vcsHelper: MockVcsHelper
  val logProvider: GitLogProvider
}

fun TestFixture<VcsPlatformTestContext>.gitPlatformFixture(
  projectFixture: TestFixture<Project>,
  defaultSaveChangesPolicy: GitSaveChangesPolicy,
  hasRemoteGitOperation: Boolean,
  initCommitContext: CommitContext.() -> Unit = {}
): TestFixture<GitPlatformTestContext> = testFixture { context ->
  with(init()) {
    registryKeyFixture("git.use.env.from.project.context") { setValue(false) }.init()
    val dialogManager = service<DialogManager>() as TestDialogManager
    val vcsHelper = projectFixture.replacedServiceFixture(AbstractVcsHelper::class.java) {
      MockVcsHelper(project)
    }.init()
    val repositoryManager = GitUtil.getRepositoryManager(project)
    val git = application.replacedServiceFixture(Git::class.java) {
      TestGitImpl()
    }.init()
    val vcs = GitVcs.getInstance(project).apply {
      doActivate()
    }
    val commitContext = CommitContext()
    initCommitContext(commitContext)

    val settings = GitVcsSettings.getInstance(project).apply {
      saveChangesPolicy = defaultSaveChangesPolicy
    }
    val appSettings = GitVcsApplicationSettings.getInstance().apply {
      setPathToGit(gitExecutable(context.eel))
    }
    GitExecutableManager.getInstance().testGitExecutableVersionValid(project)

    val logProvider = findGitLogProvider(project)

    assumeSupportedGitVersion(vcs)
    addSilently()
    removeSilently()

    val credentialHelpers = if (hasRemoteGitOperation) readAndResetCredentialHelpers() else emptyMap()
    val globalSslVerify = if (hasRemoteGitOperation) readAndDisableSslVerifyGlobally() else null

    val result = object : GitPlatformTestContext, VcsPlatformTestContext by this {
      override val repositoryManager = repositoryManager
      override val settings = settings
      override val git = git
      override val vcs = vcs
      override val commitContext = commitContext
      override val dialogManager = dialogManager
      override val vcsHelper = vcsHelper
      override val logProvider = logProvider
    }
    initialized(result) {
      runAll(
        { restoreCredentialHelpers(credentialHelpers) },
        { restoreGlobalSslVerify(globalSslVerify) },
        { dialogManager.cleanup() },
        { git.reset() },
        { appSettings.setPathToGit(null) }
      )
    }
  }
}

fun GitPlatformTestContext.updateUntrackedFiles(repo: GitRepository) {
  repo.untrackedFilesHolder.invalidate()
  runBlockingMaybeCancellable {
    repo.untrackedFilesHolder.awaitNotBusy()
  }
}

fun GitPlatformTestContext.commit(changes: Collection<Change>, commitMessage: String = "comment") {
  val exceptions = tryCommit(changes, commitMessage)
  exceptions?.forEach { fail("Exception during executing the commit: " + it.message) }
}

fun GitPlatformTestContext.tryCommit(changes: Collection<Change>, commitMessage: String = "comment"): List<VcsException>? {
  val exceptions = vcs.checkinEnvironment!!.commit(changes.toList(), commitMessage, commitContext, mutableSetOf())
  updateChangeListManager()
  return exceptions
}

fun GitPlatformTestContext.assertNoChanges() {
  changeListManager.assertNoChanges()
}

fun GitPlatformTestContext.assertChanges(changes: ChangesBuilder.() -> Unit): List<Change> {
  return changeListManager.assertChanges(changes)
}

fun GitPlatformTestContext.assertChangesWithRefresh(changes: ChangesBuilder.() -> Unit): List<Change> {
  VcsDirtyScopeManager.getInstance(project).markEverythingDirty()
  changeListManager.ensureUpToDate()
  return changeListManager.assertChanges(changes)
}

fun GitPlatformTestContext.withPartialTracker(file: VirtualFile, newContent: String? = null, task: (Document, PartialLocalLineStatusTracker) -> Unit) {
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

/**
 * There are small differences between 'recursive' (old) and 'ort' (new) merge algorithms.
 */
fun GitPlatformTestContext.gitUsingOrtMergeAlg(): Boolean {
  return vcs.version.isLaterOrEqual(GitVersion(2, 34, 0, 0))
}

fun VcsPlatformTestContext.git(command: String, ignoreNonZeroExitCode: Boolean = false) = git(project, command, ignoreNonZeroExitCode)

private fun VcsPlatformTestContext.addSilently() {
  doActionSilently(VcsConfiguration.StandardConfirmation.ADD)
}

private fun VcsPlatformTestContext.removeSilently() {
  doActionSilently(VcsConfiguration.StandardConfirmation.REMOVE)
}

private fun VcsPlatformTestContext.doActionSilently(op: VcsConfiguration.StandardConfirmation) {
  AbstractVcsTestCase.setStandardConfirmation(project, GitVcs.NAME, op, VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY)
}

private fun VcsPlatformTestContext.restoreCredentialHelpers(credentialHelpers: Map<ConfigScope, List<String>>) {
  credentialHelpers.forEach { (scope, values) ->
    values.forEach { git("config --add ${scope.param()} credential.helper ${it}", true) }
  }
}

private fun VcsPlatformTestContext.restoreGlobalSslVerify(globalSslVerify: Boolean? = null) {
  if (globalSslVerify != null) {
    git("config --global http.sslVerify ${globalSslVerify}", true)
  }
  else {
    git("config --global --unset http.sslVerify", true)
  }
}

private fun VcsPlatformTestContext.readAndDisableSslVerifyGlobally(): Boolean? {
  val value = git("config --global --get-all -z http.sslVerify", true)
    .split("\u0000")
    .singleOrNull { it.isNotBlank() }
    ?.toBoolean()
  git("config --global http.sslVerify false", true)
  return value
}

private fun VcsPlatformTestContext.readAndResetCredentialHelpers(): Map<ConfigScope, List<String>> {
  val system = readAndResetCredentialHelper(ConfigScope.SYSTEM)
  val global = readAndResetCredentialHelper(ConfigScope.GLOBAL)
  return mapOf(ConfigScope.SYSTEM to system, ConfigScope.GLOBAL to global)
}

private fun VcsPlatformTestContext.readAndResetCredentialHelper(scope: ConfigScope): List<String> {
  val values = git("config ${scope.param()} --get-all -z credential.helper", true).split("\u0000").filter { it.isNotBlank() }
  git("config ${scope.param()} --unset-all credential.helper", true)
  return values
}

private enum class ConfigScope {
  SYSTEM,
  GLOBAL;

  fun param() = "--${name.lowercase(Locale.getDefault())}"
}

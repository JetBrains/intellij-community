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

import com.intellij.ide.highlighter.ProjectFileType
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.*
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestCase
import com.intellij.testFramework.TestLoggerFactory
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.testFramework.vcs.AbstractVcsTestCase
import com.intellij.util.ArrayUtil
import git4idea.DialogManager
import git4idea.GitPlatformFacade
import git4idea.GitUtil
import git4idea.GitVcs
import git4idea.commands.Git
import git4idea.commands.GitHandler
import git4idea.config.GitVcsSettings
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import java.io.File
import java.util.*

abstract class GitPlatformTest : PlatformTestCase() {

  protected val LOG = Logger.getInstance(GitPlatformTest::class.java)

  protected lateinit var myProjectRoot: VirtualFile
  protected lateinit var myProjectPath: String
  protected lateinit var myGitRepositoryManager: GitRepositoryManager
  protected lateinit var myGitSettings: GitVcsSettings
  protected lateinit var myPlatformFacade: GitPlatformFacade
  protected lateinit var myGit: TestGitImpl
  protected lateinit var myVcs: GitVcs
  protected lateinit var myDialogManager: TestDialogManager
  protected lateinit var myVcsNotifier: TestVcsNotifier

  protected lateinit var myTestRoot: File

  private lateinit var myTestStartedIndicator: String

  @Throws(Exception::class)
  override fun setUp() {
    myTestRoot = File(FileUtil.getTempDirectory(), "testRoot")
    PlatformTestCase.myFilesToDelete.add(myTestRoot)

    checkTestRootIsEmpty(myTestRoot)

    runInEdtAndWait { super@GitPlatformTest.setUp() }

    myTestStartedIndicator = enableDebugLogging()

    myProjectRoot = myProject.baseDir
    myProjectPath = myProjectRoot.path

    myGitSettings = GitVcsSettings.getInstance(myProject)
    myGitSettings.appSettings.pathToGit = GitExecutor.PathHolder.GIT_EXECUTABLE

    myDialogManager = ServiceManager.getService(DialogManager::class.java) as TestDialogManager
    myVcsNotifier = ServiceManager.getService(myProject, VcsNotifier::class.java) as TestVcsNotifier

    myGitRepositoryManager = GitUtil.getRepositoryManager(myProject)
    myPlatformFacade = ServiceManager.getService(myProject, GitPlatformFacade::class.java)
    myGit = GitTestUtil.overrideService(Git::class.java, TestGitImpl::class.java)
    myVcs = GitVcs.getInstance(myProject)!!
    myVcs.doActivate()

    GitTestUtil.assumeSupportedGitVersion(myVcs)
    addSilently()
    removeSilently()
  }

  @Throws(Exception::class)
  override fun tearDown() {
    try {
      if (wasInit { myDialogManager }) { myDialogManager.cleanup() }
      if (wasInit { myVcsNotifier }) { myVcsNotifier.cleanup() }
      myGit.reset()
    }
    finally {
      try {
        runInEdtAndWait { super@GitPlatformTest.tearDown() }
      }
      finally {
        if (myAssertionsInTestDetected) {
          TestLoggerFactory.dumpLogToStdout(myTestStartedIndicator)
        }
      }
    }
  }

  override fun getIprFile(): File {
    val projectRoot = File(myTestRoot, "project")
    return FileUtil.createTempFile(projectRoot, name + "_", ProjectFileType.DOT_DEFAULT_EXTENSION)
  }

  override fun setUpModule() {
    // we don't need a module in Git tests
  }

  override fun isRunInEdt(): Boolean {
    return false
  }

  override fun getTestName(lowercaseFirstLetter: Boolean): String {
    var name = super.getTestName(lowercaseFirstLetter)
    name = StringUtil.shortenTextWithEllipsis(name.trim { it <= ' ' }.replace(" ", "_"), 12, 6, "_")
    if (name.startsWith("_")) {
      name = name.substring(1)
    }
    return name
  }

  inline fun wasInit(f: () -> Unit): Boolean {
    try {
      f()
    }
    catch(e: UninitializedPropertyAccessException) {
      return false
    }
    return true
  }

  private fun enableDebugLogging(): String {
    val commonCategories = ArrayList(Arrays.asList("#" + Executor::class.java.name,
                                                   "#" + GitHandler::class.java.name,
                                                   GitHandler::class.java.name))
    commonCategories.addAll(getDebugLogCategories())
    TestLoggerFactory.enableDebugLogging(myTestRootDisposable, *ArrayUtil.toStringArray(commonCategories))
    val testStartedIndicator = createTestStartedIndicator()
    LOG.info(testStartedIndicator)
    return testStartedIndicator
  }

  protected open fun getDebugLogCategories(): Collection<String> = emptyList()

  private fun createTestStartedIndicator(): String {
    return "Starting " + javaClass.name + "." + getTestName(false) + Math.random()
  }

  protected open fun createRepository(rootDir: String): GitRepository {
    return GitTestUtil.createRepository(myProject, rootDir)
  }

  /**
   * Clones the given source repository into a bare parent.git and adds the remote origin.
   */
  protected fun prepareRemoteRepo(source: GitRepository) {
    val target = "parent.git"
    val targetName = "origin"
    Executor.cd(myProjectRoot)
    GitExecutor.git("clone --bare '%s' %s", source.root.path, target)
    GitExecutor.cd(source)
    GitExecutor.git("remote add %s '%s'", targetName, "$myProjectRoot/$target")
  }

  protected open fun refresh() {
    VfsUtil.markDirtyAndRefresh(false, true, false, myProjectRoot)
  }

  protected fun doActionSilently(op: VcsConfiguration.StandardConfirmation) {
    AbstractVcsTestCase.setStandardConfirmation(myProject, GitVcs.NAME, op, VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY)
  }

  protected fun updateChangeListManager() {
    val changeListManager = ChangeListManager.getInstance(myProject)
    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty()
    changeListManager.ensureUpToDate(false)
  }

  protected fun addSilently() {
    doActionSilently(VcsConfiguration.StandardConfirmation.ADD)
  }

  protected fun removeSilently() {
    doActionSilently(VcsConfiguration.StandardConfirmation.REMOVE)
  }

  private fun checkTestRootIsEmpty(testRoot: File) {
    val files = testRoot.listFiles()
    if (files != null && files.size > 0) {
      LOG.warn("Test root was not cleaned up during some previous test run. " + "testRoot: " + testRoot +
                   ", files: " + Arrays.toString(files))
      for (file in files) {
        LOG.assertTrue(FileUtil.delete(file))
      }
    }
  }

  protected fun installHook(gitDir: File, hookName: String, hookContent: String) {
    val hookFile = File(gitDir, "hooks/$hookName")
    FileUtil.writeToFile(hookFile, hookContent)
    hookFile.setExecutable(true, false)
  }

  protected fun assertSuccessfulNotification(title: String, message: String) {
    GitTestUtil.assertNotification(NotificationType.INFORMATION, title, message, myVcsNotifier.lastNotification)
  }

  protected fun assertSuccessfulNotification(message: String) {
    assertSuccessfulNotification("Rebase Successful", message)
  }

  protected fun assertWarningNotification(title: String, message: String) {
    GitTestUtil.assertNotification(NotificationType.WARNING, title, message, myVcsNotifier.lastNotification)
  }

  protected fun assertErrorNotification(title: String, message: String) : Notification {
    val notification = myVcsNotifier.lastNotification
    assertNotNull("No notification was shown", notification)
    GitTestUtil.assertNotification(NotificationType.ERROR, title, message, notification)
    return notification
  }

  protected fun assertNoNotification() {
    val notification = myVcsNotifier.lastNotification
    if (notification != null) {
      fail("No notification is expected here, but this one was shown: ${notification.title}/${notification.content}");
    }
  }
}

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

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.*
import com.intellij.testFramework.vcs.AbstractVcsTestCase
import com.intellij.vcs.test.VcsPlatformTest
import git4idea.DialogManager
import git4idea.GitUtil
import git4idea.GitVcs
import git4idea.commands.Git
import git4idea.commands.GitHandler
import git4idea.config.GitVcsSettings
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import java.io.File

abstract class GitPlatformTest : VcsPlatformTest() {

  protected lateinit var myGitRepositoryManager: GitRepositoryManager
  protected lateinit var myGitSettings: GitVcsSettings
  protected lateinit var myGit: TestGitImpl
  protected lateinit var myVcs: GitVcs
  protected lateinit var myDialogManager: TestDialogManager
  protected lateinit var myVcsNotifier: TestVcsNotifier

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()

    myGitSettings = GitVcsSettings.getInstance(myProject)
    myGitSettings.appSettings.pathToGit = GitExecutor.PathHolder.GIT_EXECUTABLE

    myDialogManager = ServiceManager.getService(DialogManager::class.java) as TestDialogManager
    myVcsNotifier = ServiceManager.getService(myProject, VcsNotifier::class.java) as TestVcsNotifier

    myGitRepositoryManager = GitUtil.getRepositoryManager(myProject)
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
      super.tearDown()
    }
  }

  override fun getDebugLogCategories(): Collection<String> {
    return super.getDebugLogCategories().plus(listOf("#" + Executor::class.java.name,
                                                     "#" + GitHandler::class.java.name,
                                                     "#output." + GitHandler::class.java.name))
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

  protected fun assertSuccessfulNotification(title: String, message: String) : Notification {
    return GitTestUtil.assertNotification(NotificationType.INFORMATION, title, message, myVcsNotifier.lastNotification)
  }

  protected fun assertSuccessfulNotification(message: String) : Notification {
    return assertSuccessfulNotification("", message)
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

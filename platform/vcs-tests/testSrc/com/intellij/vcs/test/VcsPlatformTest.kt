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
package com.intellij.vcs.test

import com.intellij.ide.highlighter.ProjectFileType
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.TestVcsNotifier
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestCase
import com.intellij.testFramework.RunAll
import com.intellij.testFramework.TestLoggerFactory
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.ArrayUtil
import com.intellij.util.ThrowableRunnable
import java.io.File
import java.util.*
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

abstract class VcsPlatformTest : PlatformTestCase() {

  protected lateinit var myTestRoot: File
  protected lateinit var myTestRootFile: VirtualFile
  protected lateinit var myProjectRoot: VirtualFile
  protected lateinit var myProjectPath: String

  private lateinit var myTestStartedIndicator: String
  private val asyncTasks = mutableSetOf<AsyncTask>()

  protected lateinit var changeListManager: ChangeListManagerImpl
  protected lateinit var vcsManager: ProjectLevelVcsManagerImpl
  protected lateinit var myVcsNotifier: TestVcsNotifier

  @Throws(Exception::class)
  override fun setUp() {
    myTestRoot = File(FileUtil.getTempDirectory(), "testRoot")
    PlatformTestCase.myFilesToDelete.add(myTestRoot)
    checkTestRootIsEmpty(myTestRoot)

    runInEdtAndWait { super@VcsPlatformTest.setUp() }
    myTestRootFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(myTestRoot)!!
    refresh()

    myTestStartedIndicator = enableDebugLogging()

    myProjectRoot = myProject.baseDir
    myProjectPath = myProjectRoot.path

    changeListManager = ChangeListManager.getInstance(myProject) as ChangeListManagerImpl
    vcsManager = ProjectLevelVcsManager.getInstance(myProject) as ProjectLevelVcsManagerImpl

    myVcsNotifier = overrideService<VcsNotifier, TestVcsNotifier>(myProject)
    myVcsNotifier = myProject.service<VcsNotifier>() as TestVcsNotifier
  }

  @Throws(Exception::class)
  override fun tearDown() {
    RunAll()
      .append(ThrowableRunnable { if (wasInit { myVcsNotifier }) myVcsNotifier.cleanup() })
      .append(ThrowableRunnable { waitForPendingTasks() })
      .append(ThrowableRunnable { if (myAssertionsInTestDetected) TestLoggerFactory.dumpLogToStdout(myTestStartedIndicator) })
      .append(ThrowableRunnable { clearFields(this) })
      .append(ThrowableRunnable { runInEdtAndWait { super@VcsPlatformTest.tearDown() } })
      .run()
  }

  /**
   * Returns log categories which will be switched to DEBUG level.
   * Implementations must add theirs categories to the ones from super class,
   * not to erase log categories from the super class.
   * (e.g. by calling `super.getDebugLogCategories().plus(additionalCategories)`.
   */
  protected open fun getDebugLogCategories(): Collection<String> = emptyList()

  override fun getIprFile(): File {
    val projectRoot = File(myTestRoot, "project")
    return FileUtil.createTempFile(projectRoot, name + "_", ProjectFileType.DOT_DEFAULT_EXTENSION)
  }

  override fun setUpModule() {
    // we don't need a module in Git tests
  }

  override fun runInDispatchThread(): Boolean {
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

  protected inline fun wasInit(f: () -> Unit): Boolean {
    try {
      f()
    }
    catch(e: UninitializedPropertyAccessException) {
      return false
    }
    return true
  }

  protected open fun refresh() {
    VfsUtil.markDirtyAndRefresh(false, true, false, myTestRootFile)
  }

  protected fun updateChangeListManager() {
    val changeListManager = ChangeListManager.getInstance(myProject)
    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty()
    changeListManager.ensureUpToDate(false)
  }

  protected fun waitForPendingTasks() {
    for ((name, indicator, future) in asyncTasks) {
      if (!future.isDone) {
        LOG.error("Task $name didn't finish within the test")
        indicator.cancel()
        future.get(10, TimeUnit.SECONDS)
      }
    }
  }

  protected fun executeOnPooledThread(runnable: () -> Unit){
    val indicator = EmptyProgressIndicator()
    val future = ApplicationManager.getApplication().executeOnPooledThread {
      ProgressManager.getInstance().executeProcessUnderProgress({ runnable() }, indicator)
    }
    asyncTasks.add(AsyncTask(super.getTestName(false), indicator, future))
  }

  private fun checkTestRootIsEmpty(testRoot: File) {
    val files = testRoot.listFiles()
    if (files != null && files.isNotEmpty()) {
      LOG.warn("Test root was not cleaned up during some previous test run. " + "testRoot: " + testRoot +
          ", files: " + Arrays.toString(files))
      for (file in files) {
        LOG.assertTrue(FileUtil.delete(file))
      }
    }
  }

  private fun enableDebugLogging(): String {
    TestLoggerFactory.enableDebugLogging(testRootDisposable, *ArrayUtil.toStringArray(getDebugLogCategories()))
    val testStartedIndicator = createTestStartedIndicator()
    LOG.info(testStartedIndicator)
    return testStartedIndicator
  }

  private fun createTestStartedIndicator(): String {
    return "Starting " + javaClass.name + "." + super.getTestName(false) + Math.random()
  }


  protected fun assertSuccessfulNotification(title: String, message: String) : Notification {
    return assertNotification(NotificationType.INFORMATION, title, message, myVcsNotifier.lastNotification)
  }

  protected fun assertSuccessfulNotification(message: String) : Notification {
    return assertSuccessfulNotification("", message)
  }

  protected fun assertWarningNotification(title: String, message: String) {
    assertNotification(NotificationType.WARNING, title, message, myVcsNotifier.lastNotification)
  }

  protected fun assertErrorNotification(title: String, message: String) : Notification {
    val notification = myVcsNotifier.lastNotification
    assertNotNull("No notification was shown", notification)
    assertNotification(NotificationType.ERROR, title, message, notification)
    return notification
  }

  protected fun assertNoNotification() {
    val notification = myVcsNotifier.lastNotification
    if (notification != null) {
      fail("No notification is expected here, but this one was shown: ${notification.title}/${notification.content}")
    }
  }

  data class AsyncTask(val name: String, val indicator: ProgressIndicator, val future: Future<*>)
}
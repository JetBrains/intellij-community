// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.test

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.Executor.cd
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.TestVcsNotifier
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl
import com.intellij.openapi.vcs.impl.VcsInitialization
import com.intellij.openapi.vcs.impl.projectlevelman.NewMappings
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.*
import com.intellij.util.ArrayUtil
import com.intellij.util.ThrowableRunnable
import com.intellij.util.io.createDirectories
import com.intellij.vfs.AsyncVfsEventsPostProcessorImpl
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

abstract class VcsPlatformTest : HeavyPlatformTestCase() {
  protected lateinit var testRoot: File
  protected lateinit var testRootFile: VirtualFile
  protected lateinit var projectRoot: VirtualFile
  protected lateinit var projectPath: String

  private lateinit var testStartedIndicator: String
  private val asyncTasks = mutableSetOf<AsyncTask>()

  protected lateinit var changeListManager: ChangeListManagerImpl
  protected lateinit var vcsManager: ProjectLevelVcsManagerImpl
  protected lateinit var vcsNotifier: TestVcsNotifier

  @Throws(Exception::class)
  override fun setUp() {
    runInEdtAndWait {
      super.setUp()
    }

    // HeavyPlatformTestCase creates dir for each test
    testRoot = Paths.get(FileUtilRt.getTempDirectory()).toFile()

    testRootFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(testRoot)!!
    refresh()

    testStartedIndicator = enableDebugLogging()

    val baseDir = Paths.get(project.basePath!!)
    baseDir.createDirectories()
    projectRoot = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(baseDir)!!
    projectPath = projectRoot.path

    changeListManager = ChangeListManagerImpl.getInstanceImpl(project)
    vcsManager = ProjectLevelVcsManager.getInstance(project) as ProjectLevelVcsManagerImpl
    vcsManager.waitForInitialized()

    vcsNotifier = TestVcsNotifier(myProject)
    project.replaceService(VcsNotifier::class.java, vcsNotifier, testRootDisposable)
    cd(testRoot)
  }

  override fun tearDown() {
    RunAll()
      .append(ThrowableRunnable { selfTearDownRunnable() })
      .append(ThrowableRunnable { clearFields(this) })
      .append(ThrowableRunnable { runInEdtAndWait { super@VcsPlatformTest.tearDown() } })
      .run()
  }

  private fun selfTearDownRunnable() {
    var tearDownErrorDetected = false
    try {
      RunAll()
        .append(ThrowableRunnable { AsyncVfsEventsPostProcessorImpl.waitEventsProcessed() })
        .append(ThrowableRunnable { changeListManager.waitEverythingDoneInTestMode() })
        .append(ThrowableRunnable { if (::vcsNotifier.isInitialized) vcsNotifier.cleanup() })
        .append(ThrowableRunnable { waitForPendingTasks() })
        .run()
    }
    catch (e: Throwable) {
      tearDownErrorDetected = true
      throw e
    }
    finally {
      if (myAssertionsInTestDetected || tearDownErrorDetected) {
        TestLoggerFactory.dumpLogToStdout(testStartedIndicator)
      }
    }
  }

  /**
   * Returns log categories which will be switched to DEBUG level.
   * Implementations must add theirs categories to the ones from super class,
   * not to erase log categories from the super class.
   * (e.g. by calling `super.getDebugLogCategories().plus(additionalCategories)`.
   */
  protected open fun getDebugLogCategories(): Collection<String> = mutableListOf(
    "#" + UsefulTestCase::class.java.name,
    "#" + NewMappings::class.java.name,
    "#" + VcsInitialization::class.java.name)

  override fun getProjectDirOrFile(): Path {
    return Paths.get(FileUtil.getTempDirectory(), "project")
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

  @JvmOverloads
  protected open fun refresh(dir: VirtualFile = testRootFile) {
    VfsUtil.markDirtyAndRefresh(false, true, false, dir)
  }

  protected fun updateChangeListManager() {
    VcsDirtyScopeManager.getInstance(project).markEverythingDirty()
    changeListManager.ensureUpToDate()
  }

  private fun waitForPendingTasks() {
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
    return assertNotification(NotificationType.INFORMATION, title, message, vcsNotifier.lastNotification)
  }

  protected fun assertSuccessfulNotification(message: String) : Notification {
    return assertSuccessfulNotification("", message)
  }

  protected fun assertWarningNotification(title: String, message: String) {
    assertNotification(NotificationType.WARNING, title, message, vcsNotifier.lastNotification)
  }

  protected fun assertErrorNotification(title: String, message: String) : Notification {
    val notification = vcsNotifier.lastNotification
    assertNotNull("No notification was shown", notification)
    assertNotification(NotificationType.ERROR, title, message, notification)
    return notification
  }

  protected fun assertNoNotification() {
    val notification = vcsNotifier.lastNotification
    if (notification != null) {
      fail("No notification is expected here, but this one was shown: ${notification.title}/${notification.content}")
    }
  }

  protected fun assertNoErrorNotification() {
    vcsNotifier.notifications.find { it.type == NotificationType.ERROR }?.let { notification ->
      fail("No error notification is expected here, but this one was shown: ${notification.title}/${notification.content}")
    }
  }

  data class AsyncTask(val name: String, val indicator: ProgressIndicator, val future: Future<*>)
}
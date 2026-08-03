// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.test

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.ExecutorContext
import com.intellij.openapi.vcs.ExecutorContextImpl
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.TestVcsNotifier
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.project.stateStore
import com.intellij.testFramework.HeavyTestHelper
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.replacedServiceFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.vfs.AsyncVfsEventsPostProcessorImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.fail
import java.nio.file.Path
import kotlin.io.path.createDirectories

interface VcsPlatformTestContext : ExecutorContext {
  val project: Project
  val projectRoot: VirtualFile
  val projectNioRoot: Path
  val projectPath: String

  /**
   * Directory which contains the project directory.
   *
   * Use it for repositories, clones, bare parent repositories and linked working trees which have to live
   * outside of the project. It is only meaningful if the project path comes from [vcsTestProjectPathFixture]
   * or [projectPathFixture]: only then it is a dedicated directory which is removed together with the fixture.
   */
  val testNioRoot: Path
  val changeListManager: ChangeListManagerImpl
  val vcsManager: ProjectLevelVcsManagerImpl
  val vcsNotifier: TestVcsNotifier
}

/**
 * Temp directory which plays the role of the test root: the project is expected to be created in its subdirectory,
 * see [projectPathFixture]. The whole directory is removed recursively during tear down.
 */
fun vcsTestRootFixture(): TestFixture<Path> = tempPathFixture(prefix = "vcsTestRoot")

/**
 * Path of the project directory inside the test root, which becomes [VcsPlatformTestContext.testNioRoot].
 *
 * Pass `createDirectory = false` if the directory has to be created by the test setup itself,
 * e.g. by `git worktree add`.
 */
fun TestFixture<Path>.projectPathFixture(name: String = "project", createDirectory: Boolean = true): TestFixture<Path> = testFixture {
  val projectPath = init().resolve(name)
  if (createDirectory) {
    withContext(Dispatchers.IO) {
      projectPath.createDirectories()
    }
  }
  initialized(projectPath) {
    // removed together with the test root
  }
}

/**
 * Path of the project directory in a dedicated test root, mirroring the layout of [VcsPlatformTest].
 */
fun vcsTestProjectPathFixture(): TestFixture<Path> = vcsTestRootFixture().projectPathFixture()

fun TestFixture<Project>.vcsPlatformFixture(): TestFixture<VcsPlatformTestContext> = testFixture {
  val project = init()

  // TODO adapt here fine logging level adjustment from VcsPlatformTest
  //VfsUtil.markDirtyAndRefresh(false, true, false, testRoot)
  //enableDebugLogging()

  val changeListManager = ChangeListManagerImpl.getInstanceImpl(project)
  val vcsManager = (ProjectLevelVcsManager.getInstance(project) as ProjectLevelVcsManagerImpl).apply {
    waitForInitialized()
  }
  val vcsNotifier = replacedServiceFixture(VcsNotifier::class.java) {
    TestVcsNotifier(project)
  }.init()
  val projectNioRoot = project.stateStore.projectBasePath
  val projectRoot = HeavyTestHelper.getOrCreateProjectBaseDir(project)
  val projectPath = FileUtil.toSystemIndependentName(projectNioRoot.toString())
  val executor = ExecutorContextImpl(projectNioRoot)

  val context = object : VcsPlatformTestContext, ExecutorContext by executor {
    override val project = project
    override val projectRoot = projectRoot
    override val projectNioRoot = projectNioRoot
    override val projectPath: String = projectPath
    override val testNioRoot: Path = projectNioRoot.parent
    override val changeListManager = changeListManager
    override val vcsManager = vcsManager
    override val vcsNotifier = vcsNotifier
  }
  initialized(context) {
    selfTearDownRunnable(context)
  }
}

private fun selfTearDownRunnable(vcsPlatformTestContext: VcsPlatformTestContext) {
  runAll(
    { AsyncVfsEventsPostProcessorImpl.waitEventsProcessed() },
    { vcsPlatformTestContext.changeListManager.waitEverythingDoneAndStopInTestMode() },
    { vcsPlatformTestContext.vcsNotifier.cleanup() }
  )
}

fun VcsPlatformTestContext.updateChangeListManager() {
  VcsDirtyScopeManager.getInstance(project).markEverythingDirty()
  changeListManager.ensureUpToDate()
}

/**
 * Refreshes the given directory recursively, [testNioRoot] by default.
 */
fun VcsPlatformTestContext.refresh(dir: Path = testNioRoot) {
  val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(dir) ?: return
  refresh(virtualFile)
}

fun refresh(dir: VirtualFile) {
  dir.refresh(false, true)
}

fun VcsPlatformTestContext.assertSuccessfulNotification(title: String = "", message: String, actions: List<String>? = null): Notification {
  return assertHasNotification(NotificationType.INFORMATION, title, message, actions, vcsNotifier.notifications)
}

fun VcsPlatformTestContext.assertSuccessfulNotification(message: String, actions: List<String>? = null): Notification {
  return assertSuccessfulNotification("", message, actions)
}

fun VcsPlatformTestContext.assertWarningNotification(title: String, message: String): Notification {
  return assertHasNotification(NotificationType.WARNING, title, message, vcsNotifier.notifications)
}

fun VcsPlatformTestContext.assertErrorNotification(title: String, message: String, actions: List<String>? = null): Notification {
  return assertHasNotification(NotificationType.ERROR, title, message, actions, vcsNotifier.notifications)
}

fun VcsPlatformTestContext.assertNoNotification() {
  val notification = vcsNotifier.lastNotification
  if (notification != null) {
    fail<Nothing>("No notification is expected here, but this one was shown: ${notification.title}/${notification.content}")
  }
}

fun VcsPlatformTestContext.assertNoErrorNotification() {
  vcsNotifier.notifications.find { it.type == NotificationType.ERROR }?.let { notification ->
    fail<Nothing>("No error notification is expected here, but this one was shown: ${notification.title}/${notification.content}")
  }
}

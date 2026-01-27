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
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.project.stateStore
import com.intellij.testFramework.HeavyTestHelper
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.replacedServiceFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.vfs.AsyncVfsEventsPostProcessorImpl
import org.junit.jupiter.api.Assertions.fail
import java.nio.file.Path

interface VcsPlatformTestContext : ExecutorContext {
  val project: Project
  val projectRoot: VirtualFile
  val projectNioRoot: Path
  val projectPath: String
  val changeListManager: ChangeListManagerImpl
  val vcsManager: ProjectLevelVcsManagerImpl
  val vcsNotifier: TestVcsNotifier
}

fun TestFixture<Project>.vcsPlatformFixture(): TestFixture<VcsPlatformTestContext> = testFixture {
  val project = init()

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

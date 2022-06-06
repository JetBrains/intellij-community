// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.externalSystem.importing.ExternalSystemSetupProjectTestCase.Companion.openProjectFrom
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import com.intellij.openapi.externalSystem.service.remote.ExternalSystemProgressNotificationManagerImpl
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.externalSystem.util.findOrCreateDirectory
import com.intellij.openapi.externalSystem.util.forceCloseProject
import com.intellij.openapi.externalSystem.util.runWriteActionAndGet
import com.intellij.openapi.externalSystem.util.runWriteActionAndWait
import com.intellij.openapi.module.Module
import com.intellij.openapi.observable.operations.CompoundParallelOperationTrace
import com.intellij.openapi.observable.operations.ObservableOperationTrace
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RunAll
import com.intellij.testFramework.fixtures.BareTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.SdkTestFixture
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.ThrowableRunnable
import com.intellij.util.ui.UIUtil
import org.gradle.util.GradleVersion
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.plugins.gradle.testFramework.*
import org.jetbrains.plugins.gradle.testFramework.util.generateWrapper
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.waitForProjectReload
import java.nio.file.Path
import java.util.concurrent.TimeUnit

internal class GradleTestFixtureImpl(
  private val projectName: String,
  private val _gradleVersion: GradleVersion,
  private val configureProject: FileTestFixture.Builder.() -> Unit
) : GradleTestFixture {

  private lateinit var bareFixture: BareTestFixture
  private lateinit var sdkFixture: SdkTestFixture
  private lateinit var fileFixture: FileTestFixture

  private lateinit var fixtureRoot: VirtualFile
  private lateinit var _projectRoot: VirtualFile
  private lateinit var project: Project

  private lateinit var projectOperations: ObservableOperationTrace

  override fun getProject(): Project = project
  override fun getModule(): Module = project.modules.single { it.name == project.name }
  override fun getTestRootDisposable(): Disposable = bareFixture.testRootDisposable

  override val gradleVersion: GradleVersion get() = _gradleVersion
  override val projectRoot: VirtualFile get() = _projectRoot

  override fun isModified() = fileFixture.isModified()
  override fun snapshot(relativePath: String) = fileFixture.snapshot(relativePath)
  override fun rollback(relativePath: String) = fileFixture.rollback(relativePath)
  override fun suppressErrors(isSuppressedErrors: Boolean) = fileFixture.suppressErrors(isSuppressedErrors)
  override fun addIllegalOperationError(message: String) = fileFixture.addIllegalOperationError(message)

  override fun setUp() {
    bareFixture = IdeaTestFixtureFactory.getFixtureFactory()
      .createBareFixture()
    bareFixture.setUp()

    sdkFixture = GradleTestFixtureFactory.getFixtureFactory()
      .createGradleJvmTestFixture(gradleVersion)
    sdkFixture.setUp()

    fixtureRoot = createFixtureRoot()
    _projectRoot = createProjectRoot()

    fileFixture = GradleTestFixtureFactory.getFixtureFactory()
      .createFileTestFixture(projectRoot) {
        configureProject()
        withFiles { generateWrapper(projectRoot, gradleVersion) }
        withFiles { createProjectCaches() }
      }
    fileFixture.setUp()

    projectOperations = getTaskExecutionOperation()
    installProjectReloadWatcher()

    project = openProjectFrom(projectRoot)
  }

  override fun tearDown() {
    RunAll(
      ThrowableRunnable { project.forceCloseProject(save = false) },
      ThrowableRunnable { projectOperations.waitForOperation() },
      ThrowableRunnable { fileFixture.tearDown() },
      ThrowableRunnable { sdkFixture.tearDown() },
      ThrowableRunnable { bareFixture.tearDown() },
      ThrowableRunnable { ExternalSystemProgressNotificationManagerImpl.assertListenersReleased() },
      ThrowableRunnable { ExternalSystemProgressNotificationManagerImpl.cleanupListeners() }
    ).run()
  }

  private fun createFixtureRoot(): VirtualFile {
    val fileSystem = LocalFileSystem.getInstance()
    val systemPath = Path.of(PathManager.getSystemPath())
    val systemDirectory = fileSystem.findOrCreateDirectory(systemPath)
    val fixtureRoot = "_GradleTestFixture/$gradleVersion"
    VfsRootAccess.allowRootAccess(testRootDisposable, systemDirectory.path + "/$fixtureRoot")
    return runWriteActionAndGet {
      systemDirectory.findOrCreateDirectory(fixtureRoot)
    }
  }

  private fun createProjectRoot(): VirtualFile {
    return runWriteActionAndGet {
      fixtureRoot.findOrCreateDirectory(projectName)
    }
  }

  private fun createProjectCaches() {
    waitForProjectReload {
      openProjectFrom(projectRoot)
    }.forceCloseProject(save = true)
  }

  private fun getTaskExecutionOperation(): ObservableOperationTrace {
    val operationTrace = CompoundParallelOperationTrace<Any>()

    val listener = object : ExternalSystemTaskNotificationListenerAdapter() {
      override fun onStart(id: ExternalSystemTaskId, workingDir: String?) {
        operationTrace.startTask(id)
      }

      override fun onEnd(id: ExternalSystemTaskId) {
        operationTrace.finishTask(id)
      }
    }

    val progressManager = ExternalSystemProgressNotificationManager.getInstance()
    progressManager.addNotificationListener(listener, testRootDisposable)

    return operationTrace
  }

  private fun installProjectReloadWatcher() {
    val reloadListener = object : ExternalSystemTaskNotificationListenerAdapter() {
      override fun onStart(id: ExternalSystemTaskId, workingDir: String?) {
        if (id.type == ExternalSystemTaskType.RESOLVE_PROJECT) {
          addIllegalOperationError("Unexpected project reload: $workingDir")
        }
      }
    }
    val progressManager = ExternalSystemProgressNotificationManager.getInstance()
    progressManager.addNotificationListener(reloadListener, testRootDisposable)
  }

  override fun reloadProject() {
    if (fileFixture.isModified()) {
      addIllegalOperationError("Unexpected reload with modified project files")
    }
    fileFixture.withSuppressedErrors {
      waitForProjectReload {
        ExternalSystemUtil.refreshProject(
          projectRoot.path,
          ImportSpecBuilder(project, GradleConstants.SYSTEM_ID)
        )
      }
      runWriteActionAndWait {
        projectRoot.refresh(false, true)
      }
      runInEdtAndWait {
        UIUtil.dispatchAllInvocationEvents()
      }
    }
  }

  companion object {
    fun ObservableOperationTrace.waitForOperation() {
      val promise = AsyncPromise<Nothing?>()
      afterOperation { promise.setResult(null) }
      if (isOperationCompleted()) {
        promise.setResult(null)
      }
      PlatformTestUtil.waitForPromise<Nothing?>(promise, TimeUnit.MINUTES.toMillis(1))
    }
  }
}
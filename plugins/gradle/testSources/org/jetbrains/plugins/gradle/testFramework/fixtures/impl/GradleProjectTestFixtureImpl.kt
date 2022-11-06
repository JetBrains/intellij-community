// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.fixtures.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.externalSystem.util.refreshAndWait
import com.intellij.openapi.module.Module
import com.intellij.openapi.observable.operation.core.AtomicOperationTrace
import com.intellij.openapi.observable.operation.core.ObservableOperationTrace
import com.intellij.openapi.observable.operation.core.withCompletedOperation
import com.intellij.openapi.observable.util.getPromise
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.closeProjectAsync
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.fixtures.SdkTestFixture
import com.intellij.testFramework.observable.waitForPromise
import com.intellij.testFramework.openProjectAsync
import kotlinx.coroutines.runBlocking
import org.gradle.util.GradleVersion
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.plugins.gradle.testFramework.fixtures.FileTestFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleProjectTestFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleTestFixtureFactory
import org.jetbrains.plugins.gradle.testFramework.util.generateWrapper
import org.jetbrains.plugins.gradle.testFramework.util.openProjectAsyncAndWait
import org.jetbrains.plugins.gradle.testFramework.util.withSuppressedErrors
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.waitForProjectReload
import java.util.concurrent.TimeUnit

internal class GradleProjectTestFixtureImpl private constructor(
  override val projectName: String,
  override val gradleVersion: GradleVersion,
  private val sdkFixture: SdkTestFixture,
  override val fileFixture: FileTestFixture
) : GradleProjectTestFixture {

  private lateinit var _project: Project

  private lateinit var testDisposable: Disposable

  private val projectOperations = AtomicOperationTrace()

  override val project: Project get() = _project
  override val module: Module get() = project.modules.single { it.name == project.name }

  constructor(
    projectName: String,
    gradleVersion: GradleVersion,
    configureProject: FileTestFixture.Builder.() -> Unit
  ) : this(
    projectName, gradleVersion,
    GradleTestFixtureFactory.getFixtureFactory().createGradleJvmTestFixture(gradleVersion),
    GradleTestFixtureFactory.getFixtureFactory().createFileTestFixture("GradleTestFixture/$gradleVersion/$projectName") {
      configureProject()
      excludeFiles(".gradle", "build")
      withFiles { generateWrapper(it, gradleVersion) }
      withFiles { runBlocking { createProjectCaches(it) } }
    }
  )

  override fun setUp() {
    testDisposable = Disposer.newDisposable()

    sdkFixture.setUp()
    fileFixture.setUp()

    installTaskExecutionWatcher()
    installProjectReloadWatcher()

    _project = runBlocking { openProjectAsync(fileFixture.root) }
  }

  override fun tearDown() {
    runAll(
      { fileFixture.root.refreshAndWait() },
      { projectOperations.waitForOperation() },
      { if (_project.isInitialized) runBlocking { _project.closeProjectAsync() } },
      { Disposer.dispose(testDisposable) },
      { fileFixture.tearDown() },
      { sdkFixture.tearDown() }
    )
  }

  private fun installTaskExecutionWatcher() {
    val listener = object : ExternalSystemTaskNotificationListenerAdapter() {
      override fun onStart(id: ExternalSystemTaskId, workingDir: String?) {
        projectOperations.traceStart()
      }

      override fun onEnd(id: ExternalSystemTaskId) {
        projectOperations.traceFinish()
      }
    }

    val progressManager = ExternalSystemProgressNotificationManager.getInstance()
    progressManager.addNotificationListener(listener, testDisposable)
  }

  private fun installProjectReloadWatcher() {
    val reloadListener = object : ExternalSystemTaskNotificationListenerAdapter() {
      override fun onStart(id: ExternalSystemTaskId, workingDir: String?) {
        if (workingDir == fileFixture.root.path) {
          if (id.type == ExternalSystemTaskType.RESOLVE_PROJECT) {
            fileFixture.addIllegalOperationError("Unexpected project reload: $workingDir")
          }
        }
      }
    }
    val progressManager = ExternalSystemProgressNotificationManager.getInstance()
    progressManager.addNotificationListener(reloadListener, testDisposable)
  }

  override fun reloadProject() {
    if (fileFixture.isModified()) {
      fileFixture.addIllegalOperationError("Unexpected reload with modified project files")
    }
    fileFixture.withSuppressedErrors {
      waitForProjectReload {
        ExternalSystemUtil.refreshProject(
          fileFixture.root.path,
          ImportSpecBuilder(project, GradleConstants.SYSTEM_ID)
        )
      }
      fileFixture.root.refreshAndWait()
    }
  }

  companion object {

    fun ObservableOperationTrace.waitForOperation() {
      getPromise(null, ::withCompletedOperation).waitForPromise()
    }

    private suspend fun createProjectCaches(projectRoot: VirtualFile) {
      val project = openProjectAsyncAndWait(projectRoot)
      try {
        projectRoot.refreshAndWait()
      }
      finally {
        project.closeProjectAsync(save = true)
      }
    }
  }
}

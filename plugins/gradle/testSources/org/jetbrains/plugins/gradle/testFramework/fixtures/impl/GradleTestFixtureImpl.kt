// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.fixtures.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.importing.ExternalSystemSetupProjectTestCase.Companion.openProjectFrom
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import com.intellij.openapi.externalSystem.util.*
import com.intellij.openapi.module.Module
import com.intellij.openapi.observable.operations.CompoundParallelOperationTrace
import com.intellij.openapi.observable.operations.ObservableOperationTrace
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RunAll
import com.intellij.testFramework.fixtures.SdkTestFixture
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.ThrowableRunnable
import com.intellij.util.ui.UIUtil
import org.gradle.util.GradleVersion
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.plugins.gradle.testFramework.fixtures.FileTestFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleTestFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleTestFixtureFactory
import org.jetbrains.plugins.gradle.testFramework.util.generateWrapper
import org.jetbrains.plugins.gradle.testFramework.util.withSuppressedErrors
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.waitForProjectReload
import java.util.concurrent.TimeUnit

internal class GradleTestFixtureImpl private constructor(
  override val projectName: String,
  override val gradleVersion: GradleVersion,
  private val sdkFixture: SdkTestFixture,
  override val fileFixture: FileTestFixture
) : GradleTestFixture {

  private lateinit var _project: Project

  private lateinit var testDisposable: Disposable

  private val projectOperations = CompoundParallelOperationTrace<Any>()

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
      withFiles { generateWrapper(it, gradleVersion) }
      withFiles { createProjectCaches(it) }
    }
  )

  override fun setUp() {
    testDisposable = Disposer.newDisposable()

    sdkFixture.setUp()
    fileFixture.setUp()

    installTaskExecutionWatcher()
    installProjectReloadWatcher()

    _project = openProjectFrom(fileFixture.root)
  }

  override fun tearDown() {
    RunAll(
      ThrowableRunnable { projectOperations.waitForOperation() },
      ThrowableRunnable { project.forceCloseProject(save = false) },
      ThrowableRunnable { Disposer.dispose(testDisposable) },
      ThrowableRunnable { fileFixture.tearDown() },
      ThrowableRunnable { sdkFixture.tearDown() },
    ).run()
  }

  private fun installTaskExecutionWatcher() {
    val listener = object : ExternalSystemTaskNotificationListenerAdapter() {
      override fun onStart(id: ExternalSystemTaskId, workingDir: String?) {
        projectOperations.startTask(id)
      }

      override fun onEnd(id: ExternalSystemTaskId) {
        projectOperations.finishTask(id)
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
      runWriteActionAndWait {
        fileFixture.root.refresh(false, true)
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
      runInEdtAndWait {
        PlatformTestUtil.waitForPromise<Nothing?>(promise, TimeUnit.MINUTES.toMillis(1))
      }
    }

    private fun createProjectCaches(projectRoot: VirtualFile) {
      waitForProjectReload {
        openProjectFrom(projectRoot)
      }.forceCloseProject(save = true)
    }
  }
}
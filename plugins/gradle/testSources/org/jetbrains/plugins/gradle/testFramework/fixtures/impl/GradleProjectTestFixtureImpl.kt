// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.fixtures.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.observable.operation.core.AtomicOperationTrace
import com.intellij.openapi.observable.operation.core.whenOperationStarted
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.closeOpenedProjectsIfFailAsync
import com.intellij.testFramework.closeProjectAsync
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.fixtures.SdkTestFixture
import com.intellij.testFramework.observable.operation.core.waitForOperationCompletionAndPumpEdt
import com.intellij.testFramework.openProjectAsync
import kotlinx.coroutines.runBlocking
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.service.project.wizard.util.generateGradleWrapper
import org.jetbrains.plugins.gradle.testFramework.fixtures.FileTestFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleProjectTestFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleTestFixtureFactory
import org.jetbrains.plugins.gradle.testFramework.util.refreshAndWait
import org.jetbrains.plugins.gradle.util.awaitProjectReload
import org.jetbrains.plugins.gradle.util.getGradleReloadOperation
import kotlin.time.Duration.Companion.minutes

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
      withFiles { generateGradleWrapper(it.toNioPath(), gradleVersion) }
      withFiles { createProjectCaches(it) }
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
      { runBlocking { fileFixture.root.refreshAndWait() } },
      { projectOperations.waitForOperationCompletionAndPumpEdt(1.minutes) },
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
    val operation = getGradleReloadOperation(fileFixture.root.path, testDisposable)
    operation.whenOperationStarted {
      fileFixture.addIllegalOperationError("Unexpected project reload: $operation")
    }
  }

  companion object {

    private suspend fun createProjectCaches(projectRoot: VirtualFile) {
      val project = closeOpenedProjectsIfFailAsync {
        awaitProjectReload {
          openProjectAsync(projectRoot)
        }
      }
      try {
        projectRoot.refreshAndWait()
      }
      finally {
        project.closeProjectAsync(save = true)
      }
    }
  }
}

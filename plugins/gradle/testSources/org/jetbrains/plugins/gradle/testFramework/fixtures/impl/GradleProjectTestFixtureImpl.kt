// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.fixtures.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.module.Module
import com.intellij.openapi.observable.operation.OperationExecutionStatus
import com.intellij.openapi.observable.operation.core.whenOperationStarted
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.externalSystem.testFramework.ExternalSystemImportingTestCase
import com.intellij.testFramework.closeOpenedProjectsIfFailAsync
import com.intellij.testFramework.closeProjectAsync
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.openProjectAsync
import com.intellij.testFramework.useProjectAsync
import com.intellij.util.indexing.FileBasedIndexEx
import com.intellij.workspaceModel.ide.impl.WorkspaceModelCacheImpl
import kotlinx.coroutines.runBlocking
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.fixtures.FileTestFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleProjectTestFixture
import org.jetbrains.plugins.gradle.testFramework.util.awaitGradleOpenProjectConfiguration
import org.jetbrains.plugins.gradle.testFramework.util.refreshAndAwait
import org.jetbrains.plugins.gradle.testFramework.util.withGradleWrapper
import org.jetbrains.plugins.gradle.tooling.JavaVersionRestriction
import org.jetbrains.plugins.gradle.util.getGradleProjectReloadOperation
import org.jetbrains.plugins.gradle.util.whenExternalSystemTaskFinished

internal class GradleProjectTestFixtureImpl(
  override val projectName: String,
  override val gradleVersion: GradleVersion,
  private val javaVersionRestriction: JavaVersionRestriction,
  private val configureProject: FileTestFixture.Builder.() -> Unit
) : GradleProjectTestFixture {

  override lateinit var gradleJvmFixture: GradleJvmTestFixture
    private set

  override lateinit var fileFixture: FileTestFixture
    private set

  private var _testDisposable: Disposable? = null
  private val testDisposable: Disposable
    get() = requireNotNull(_testDisposable) {
      "Gradle fixture wasn't setup. Please use [GradleBaseTestCase.test] function inside your tests."
    }

  private var _project: Project? = null
  override val project: Project
    get() = requireNotNull(_project) {
      "Gradle fixture wasn't setup. Please use [GradleBaseTestCase.test] function inside your tests."
    }

  override val module: Module
    get() = project.modules.single { it.name == project.name }

  override fun setUp() {
    _testDisposable = Disposer.newDisposable()

    WorkspaceModelCacheImpl.forceEnableCaching(testDisposable)
    ExternalSystemImportingTestCase.installExecutionOutputPrinter(testDisposable)

    gradleJvmFixture = GradleJvmTestFixture(gradleVersion, javaVersionRestriction)
    gradleJvmFixture.setUp()

    gradleJvmFixture.withProjectSettingsConfigurator {
      fileFixture = FileTestFixtureImpl("GradleTestFixture/$gradleVersion/$projectName") {
        configureProject()
        excludeFiles(".gradle", "build")
        withGradleWrapper(gradleVersion)
        withFiles { createProjectCaches(it) }
      }
      fileFixture.setUp()
    }

    installGradleProjectReloadWatcher()

    _project = runBlocking {
      awaitGradleOpenProjectConfiguration {
        openProjectAsync(fileFixture.root)
      }
    }
  }

  override fun tearDown() {
    runAll(
      { ApplicationManager.getApplication().serviceIfCreated<FileBasedIndexEx>()?.waitUntilIndicesAreInitialized() },
      { runBlocking { fileFixture.root.refreshAndAwait() } },
      { runBlocking { _project?.closeProjectAsync() } },
      { _project = null },
      { fileFixture.tearDown() },
      { gradleJvmFixture.tearDown() },
      { _testDisposable?.let { Disposer.dispose(it) } },
      { _testDisposable = null }
    )
  }

  private fun installGradleProjectReloadWatcher() {
    val operation = getGradleProjectReloadOperation(testDisposable) { id, _ -> id.findProject() == project }
    operation.whenOperationStarted {
      fileFixture.addIllegalOperationError("Unexpected project reload: $operation")
    }
  }

  companion object {

    private suspend fun createProjectCaches(projectRoot: VirtualFile) {
      closeOpenedProjectsIfFailAsync {
        assertExecutionStatusIsSuccess {
          awaitGradleOpenProjectConfiguration {
            openProjectAsync(projectRoot)
          }
        }
      }.useProjectAsync(save = true) {
        projectRoot.refreshAndAwait()
      }
    }

    private suspend fun <R> assertExecutionStatusIsSuccess(action: suspend () -> R): R {
      Disposer.newDisposable().use { disposable ->
        var status: OperationExecutionStatus? = null
        whenExternalSystemTaskFinished(disposable) { _, newStatus ->
          status = newStatus
        }
        val result = action()
        when (val status = status) {
          null -> throw AssertionError("Execution isn't completed")
          is OperationExecutionStatus.Failure -> throw AssertionError("Execution is failed", status.cause)
          is OperationExecutionStatus.Cancel -> throw AssertionError("Execution is cancelled")
          is OperationExecutionStatus.Success -> {}
        }
        return result
      }
    }
  }
}

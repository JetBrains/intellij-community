// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.testFramework.fixtures.impl

import com.intellij.gradle.testFramework.fixtures.FileTestFixture
import com.intellij.gradle.testFramework.fixtures.GradleProjectTestFixture
import com.intellij.gradle.testFramework.jvm.JavaVersionRestriction
import com.intellij.gradle.testFramework.util.refreshAndAwait
import com.intellij.gradle.testFramework.util.withGradleWrapper
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.module.Module
import com.intellij.openapi.observable.operation.core.whenOperationStarted
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.findOrCreateDirectory
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.externalSystem.testFramework.ExternalSystemImportingTestCase
import com.intellij.platform.externalSystem.testFramework.ExternalSystemTestObservation.awaitOpenProjectActivity
import com.intellij.testFramework.closeProjectAsync
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.openProjectAsync
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexEx
import com.intellij.workspaceModel.ide.impl.WorkspaceModelCacheImpl
import kotlinx.coroutines.runBlocking
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.service.execution.gradleUserHomeDir
import org.jetbrains.plugins.gradle.util.getGradleProjectReloadOperation

internal class GradleProjectTestFixtureImpl(
  private val fixtureName: String,
  override val projectName: String,
  override val gradleVersion: GradleVersion,
  private val javaVersionRestriction: JavaVersionRestriction,
  private val configureProject: FileTestFixture.Builder.() -> Unit,
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

  override val mainModule: Module
    get() {
      return project.modules.single {
        it.name == "${project.name}.main"
      }
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
      fileFixture = FileTestFixtureImpl("GradleTestFixture/$gradleVersion/$fixtureName", projectName) {
        val eelDescriptor = fileFixture.projectRoot.toNioPath().getEelDescriptor()
        val gradleUserHome = gradleUserHomeDir(eelDescriptor).findOrCreateDirectory().toRealPath()
        VfsRootAccess.allowRootAccess(testDisposable, gradleUserHome.toString())

        configureProject()
        excludeFilePatterns("glob:**/.gradle{,/**}", "glob:**/build{,/**}")
        withGradleWrapper(gradleVersion)
        withFiles { createGradleProjectCaches(it) }
      }
      fileFixture.setUp()
    }

    installGradleProjectReloadWatcher()

    _project = runBlocking {
      awaitOpenProjectActivity {
        openProjectAsync(fileFixture.projectRoot)
      }
    }
  }

  override fun tearDown() {
    runAll(
      {
        val index = ApplicationManager.getApplication().serviceIfCreated<FileBasedIndex>() as? FileBasedIndexEx
        index?.waitUntilIndicesAreInitialized()
      },
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

}

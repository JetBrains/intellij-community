// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing.syncAction

import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.common.runAll
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import java.util.concurrent.atomic.AtomicReference

abstract class GradlePhasedSyncTestCase : GradleProjectResolverTestCase() {

  private lateinit var testDisposable: Disposable

  private lateinit var syncErrorHandler: AtomicReference<(String, String?) -> Unit>
  private val defaultSyncErrorHandler = { errorMessage: String, errorDetails: String? ->
    super.handleImportFailure(errorMessage, errorDetails)
  }

  override fun setUp() {
    super.setUp()

    testDisposable = Disposer.newDisposable()
    addProjectResolverExtension(TestProjectResolverExtension::class.java, testDisposable)

    syncErrorHandler = AtomicReference(defaultSyncErrorHandler)
  }

  override fun tearDown() {
    runAll(
      { Disposer.dispose(testDisposable) },
      { super.tearDown() }
    )
  }

  fun importProject(errorHandler: (String, String?) -> Unit) {
    syncErrorHandler.set(errorHandler)
    try {
      importProject()
    }
    finally {
      syncErrorHandler.set(defaultSyncErrorHandler)
    }
  }

  override fun handleImportFailure(errorMessage: String, errorDetails: String?) {
    syncErrorHandler.get()(errorMessage, errorDetails)
  }

  fun addModelProviders(parentDisposable: Disposable, vararg modelProviders: ProjectImportModelProvider) {
    TestProjectResolverService.getInstance(myProject)
      .addModelProviders(parentDisposable, *modelProviders)
  }

  fun addModelProviders(parentDisposable: Disposable, modelProviders: Collection<ProjectImportModelProvider>) {
    TestProjectResolverService.getInstance(myProject)
      .addModelProviders(parentDisposable, modelProviders)
  }

  fun whenPhaseCompleted(parentDisposable: Disposable, action: (ProjectResolverContext, GradleModelFetchPhase) -> Unit) {
    TestProjectResolverService.getInstance(myProject)
      .whenPhaseCompleted(parentDisposable, action)
  }

  fun whenProjectLoaded(parentDisposable: Disposable, action: (ProjectResolverContext) -> Unit) {
    TestProjectResolverService.getInstance(myProject)
      .whenProjectLoaded(parentDisposable, action)
  }

  fun whenBuildCompleted(parentDisposable: Disposable, action: (ProjectResolverContext) -> Unit) {
    TestProjectResolverService.getInstance(myProject)
      .whenBuildCompleted(parentDisposable, action)
  }
}
// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing.syncAction

import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase.Dynamic.Companion.asSyncPhase
import java.util.concurrent.atomic.AtomicReference

abstract class GradlePhasedSyncTestCase : GradleProjectResolverTestCase() {

  private lateinit var syncErrorHandler: AtomicReference<(String, String?) -> Unit>
  private val defaultSyncErrorHandler = { errorMessage: String, errorDetails: String? ->
    super.handleImportFailure(errorMessage, errorDetails)
  }

  override fun setUp() {
    super.setUp()

    syncErrorHandler = AtomicReference(defaultSyncErrorHandler)
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

  companion object {

    val DEFAULT_MODEL_FETCH_PHASES: List<GradleModelFetchPhase> = listOf(
      GradleModelFetchPhase.PROJECT_LOADED_PHASE,
      GradleModelFetchPhase.PROJECT_MODEL_PHASE,
      GradleModelFetchPhase.PROJECT_SOURCE_SET_PHASE,
      GradleModelFetchPhase.PROJECT_SOURCE_SET_DEPENDENCY_PHASE,
      GradleModelFetchPhase.ADDITIONAL_MODEL_PHASE
    )

    val DEFAULT_SYNC_PHASES: List<GradleSyncPhase> = listOf(
      GradleSyncPhase.INITIAL_PHASE,
      GradleSyncPhase.DECLARATIVE_PHASE,
      GradleModelFetchPhase.PROJECT_LOADED_PHASE.asSyncPhase(),
      GradleSyncPhase.PROJECT_MODEL_PHASE,
      GradleSyncPhase.SOURCE_SET_MODEL_PHASE,
      GradleSyncPhase.DEPENDENCY_MODEL_PHASE,
      GradleSyncPhase.ADDITIONAL_MODEL_PHASE
    )
  }
}
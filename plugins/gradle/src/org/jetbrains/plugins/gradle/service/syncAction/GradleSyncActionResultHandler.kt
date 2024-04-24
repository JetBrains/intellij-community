// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.syncAction

import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase
import com.intellij.openapi.externalSystem.autolink.forEachExtensionSafeAsync
import com.intellij.openapi.externalSystem.util.ExternalSystemTelemetryUtil
import com.intellij.openapi.progress.checkCanceled
import com.intellij.platform.diagnostic.telemetry.helpers.use
import org.jetbrains.plugins.gradle.service.project.DefaultProjectResolverContext
import org.jetbrains.plugins.gradle.util.GradleConstants

class GradleSyncActionResultHandler(
  private val resolverContext: DefaultProjectResolverContext
) {

  private val telemetry = ExternalSystemTelemetryUtil.getTracer(GradleConstants.SYSTEM_ID)

  suspend fun onModelFetchPhaseCompleted(phase: GradleModelFetchPhase) {
    telemetry.spanBuilder(phase.name).use {
      GradleSyncContributor.EP_NAME.forEachExtensionSafeAsync { extension ->
        checkCanceled()
        telemetry.spanBuilder(extension.name).use {
          extension.onModelFetchPhaseCompleted(resolverContext, phase)
        }
      }
    }
  }

  suspend fun onModelFetchCompleted() {
    telemetry.spanBuilder("MODEL_FETCH_COMPLETED").use {
      GradleSyncContributor.EP_NAME.forEachExtensionSafeAsync { extension ->
        checkCanceled()
        telemetry.spanBuilder(extension.name).use {
          extension.onModelFetchCompleted(resolverContext)
        }
      }
    }
  }

  suspend fun onModelFetchFailed(exception: Throwable) {
    telemetry.spanBuilder("MODEL_FETCH_FAILED").use { span ->
      GradleSyncContributor.EP_NAME.forEachExtensionSafeAsync { extension ->
        checkCanceled()
        telemetry.spanBuilder(extension.name).use {
          span.setAttribute("exception", exception.javaClass.name)
          span.setAttribute("exception-message", exception.message ?: "null")
          extension.onModelFetchFailed(resolverContext, exception)
        }
      }
    }
  }

  suspend fun onProjectLoadedActionCompleted() {
    telemetry.spanBuilder("PROJECT_LOADED_ACTION").use {
      GradleSyncContributor.EP_NAME.forEachExtensionSafeAsync { extension ->
        checkCanceled()
        telemetry.spanBuilder(extension.name).use {
          extension.onProjectLoadedActionCompleted(resolverContext)
        }
      }
    }
  }
}
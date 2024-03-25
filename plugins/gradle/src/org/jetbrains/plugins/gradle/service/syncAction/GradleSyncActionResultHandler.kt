// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.syncAction

import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase
import com.intellij.openapi.externalSystem.util.ExternalSystemTelemetryUtil
import com.intellij.platform.diagnostic.telemetry.helpers.runWithSpan
import org.jetbrains.plugins.gradle.service.project.DefaultProjectResolverContext
import org.jetbrains.plugins.gradle.util.GradleConstants

class GradleSyncActionResultHandler(
  private val resolverContext: DefaultProjectResolverContext
) {

  private val telemetry = ExternalSystemTelemetryUtil.getTracer(GradleConstants.SYSTEM_ID)

  fun onModelFetchPhaseCompleted(phase: GradleModelFetchPhase) {
    runWithSpan(telemetry, phase.name) {
      GradleSyncContributor.EP_NAME.forEachExtensionSafe { extension ->
        resolverContext.checkCancelled()
        runWithSpan(telemetry, extension.name) {
          extension.onModelFetchPhaseCompleted(resolverContext, phase)
        }
      }
    }
  }

  fun onModelFetchCompleted() {
    runWithSpan(telemetry, "MODEL_FETCH_COMPLETED") {
      GradleSyncContributor.EP_NAME.forEachExtensionSafe { extension ->
        resolverContext.checkCancelled()
        runWithSpan(telemetry, extension.name) {
          extension.onModelFetchCompleted(resolverContext)
        }
      }
    }
  }

  fun onModelFetchFailed(exception: Throwable) {
    runWithSpan(telemetry, "MODEL_FETCH_FAILED") { span ->
      span.setAttribute("exception", exception.message ?: exception.javaClass.name)
      GradleSyncContributor.EP_NAME.forEachExtensionSafe { extension ->
        resolverContext.checkCancelled()
        runWithSpan(telemetry, extension.name) {
          extension.onModelFetchFailed(resolverContext, exception)
        }
      }
    }
  }

  fun onProjectLoadedActionCompleted() {
    runWithSpan(telemetry, "PROJECT_LOADED_ACTION") {
      GradleSyncContributor.EP_NAME.forEachExtensionSafe { extension ->
        resolverContext.checkCancelled()
        runWithSpan(telemetry, extension.name) {
          extension.onProjectLoadedActionCompleted(resolverContext)
        }
      }
    }
  }
}
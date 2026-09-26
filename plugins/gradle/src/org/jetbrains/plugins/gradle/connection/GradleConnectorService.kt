// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.connection

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.gradle.tooling.ProjectConnection
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionContext

@Internal
interface GradleConnectorService {

  fun getKnownGradleUserHomes(): Set<String>

  fun <R> withGradleConnection(context: GradleExecutionContext, function: (ProjectConnection) -> R): R

  companion object {

    @Internal
    const val DISABLE_STOP_OLD_IDLE_DAEMONS_KEY: String = "idea.gradle.disableStopIdleDaemonsOnProjectClose"

    @Internal
    const val USE_PRODUCTION_DISPOSE_FOR_TESTS_KEY: String = "gradle.connector.useProductionDisposeForTests"

    @Internal
    const val USE_PRODUCTION_TTL_FOR_TESTS_KEY: String = "gradle.connector.useExternalSystemRemoteProcessIdleTtlForTests"

    @JvmStatic
    fun getInstance(project: Project): GradleConnectorService {
      return project.service<GradleConnectorService>()
    }
  }
}
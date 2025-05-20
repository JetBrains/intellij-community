// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.connection

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.util.ExternalSystemTelemetryUtil
import com.intellij.platform.diagnostic.telemetry.helpers.use
import org.gradle.tooling.CancellationToken
import org.gradle.tooling.ProjectConnection
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.connection.GradleConnectorServiceImpl.ConnectorParams
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.util.function.Function

@ApiStatus.Internal
internal class DefaultGradleConnectorService : GradleConnectorService {

  override fun getKnownGradleUserHomes(): Set<String> {
    return emptySet()
  }

  override fun <R> withGradleConnection(
    projectPath: String,
    taskId: ExternalSystemTaskId?,
    executionSettings: GradleExecutionSettings?,
    listener: ExternalSystemTaskNotificationListener?,
    cancellationToken: CancellationToken?,
    function: Function<ProjectConnection, R>,
  ): R {
    return ExternalSystemTelemetryUtil.getTracer(GradleConstants.SYSTEM_ID)
      .spanBuilder("DefaultGradleConnection")
      .use {
        val connectionParams = ConnectorParams(projectPath, executionSettings)
        val connector = GradleConnectorServiceImpl.createConnector(connectionParams, taskId, listener)
        val connection = connector.connect()
        connection.use(function::apply)
      }
  }
}
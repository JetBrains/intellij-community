// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.execution.telemetry

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.diagnostic.telemetry.OtlpConfiguration
import com.intellij.platform.diagnostic.telemetry.impl.agent.AgentConfiguration
import com.intellij.platform.diagnostic.telemetry.impl.agent.TelemetryAgentProvider
import com.intellij.platform.diagnostic.telemetry.impl.agent.TelemetryAgentResolver
import com.intellij.platform.diagnostic.telemetry.rt.context.TelemetryContext
import org.gradle.tooling.LongRunningOperation
import org.gradle.tooling.model.build.BuildEnvironment
import org.jetbrains.plugins.gradle.service.project.GradleExecutionHelperExtension
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings
import org.jetbrains.plugins.gradle.util.GradleConstants

class GradleTelemetryAgentProvidingExecutionHelperExtension : GradleExecutionHelperExtension {

  override fun prepareForExecution(
    id: ExternalSystemTaskId,
    operation: LongRunningOperation,
    settings: GradleExecutionSettings,
    buildEnvironment: BuildEnvironment?,
  ) {
    if (!Registry.`is`("gradle.daemon.opentelemetry.agent.enabled", false)) {
      return
    }
    val traceEndpoint = OtlpConfiguration.getTraceEndpointURI() ?: return
    val agentLocation = TelemetryAgentResolver.getAgentLocation() ?: return
    val configuration = AgentConfiguration.forService(
      serviceName = GradleConstants.GRADLE_NAME,
      context = TelemetryContext.current(),
      traceEndpoint = traceEndpoint,
      agentLocation = agentLocation,
      settings = AgentConfiguration.Settings.withoutMetrics()
    )
    val jvmArgs = TelemetryAgentProvider.getJvmArgs(configuration)
    operation.addJvmArguments(jvmArgs)
  }
}
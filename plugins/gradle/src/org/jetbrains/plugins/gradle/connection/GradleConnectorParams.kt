// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.connection

import com.intellij.openapi.externalSystem.service.execution.ExternalSystemExecutionAware.Companion.getEnvironmentConfigurationProvider
import com.intellij.openapi.externalSystem.service.execution.TargetEnvironmentConfigurationProvider
import com.intellij.task.RunConfigurationTaskState
import org.jetbrains.plugins.gradle.settings.DistributionType
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings

internal data class ConnectorParams(
  val projectPath: String,
  val serviceDirectory: String?,
  val distributionType: DistributionType?,
  val gradleHome: String?,
  val javaHome: String?,
  val wrapperPropertyFile: String?,
  val verboseProcessing: Boolean?,
  val ttlMs: Int?,
  val environmentConfigurationProvider: TargetEnvironmentConfigurationProvider?,
  val taskState: RunConfigurationTaskState?,
) {
  constructor(projectPath: String, executionSettings: GradleExecutionSettings?) : this(
    projectPath,
    executionSettings?.serviceDirectory,
    executionSettings?.distributionType,
    executionSettings?.gradleHome,
    executionSettings?.javaHome,
    executionSettings?.wrapperPropertyFile,
    executionSettings?.isVerboseProcessing,
    executionSettings?.remoteProcessIdleTtlInMs?.toInt(),
    executionSettings?.getEnvironmentConfigurationProvider(),
    executionSettings?.getUserData(RunConfigurationTaskState.KEY)
  )
}
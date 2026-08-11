// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.connection

import com.intellij.execution.target.value.TargetValue
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.util.application
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.internal.consumer.DefaultGradleConnector
import org.jetbrains.concurrency.resolvedPromise
import org.jetbrains.plugins.gradle.connection.GradleConnectorService.Companion.USE_PRODUCTION_TTL_FOR_TESTS_KEY
import org.jetbrains.plugins.gradle.execution.target.TargetGradleConnector
import org.jetbrains.plugins.gradle.execution.target.maybeConvertToRemote
import org.jetbrains.plugins.gradle.service.project.DistributionFactoryExt
import org.jetbrains.plugins.gradle.settings.DistributionType
import java.io.File
import java.util.concurrent.TimeUnit

internal object GradleConnectorFactory {

  /**
   * Must stay strictly below ThreadLeakTracker's per-thread wait timeout to let Gradle daemon helper threads exit before leak checks fail.
   */
  private const val TTL_FOR_TESTS = 5_000

  /**
   * Some longer running tests require a longer idle time.
   */
  private val USE_PRODUCTION_TTL_FOR_TESTS: Boolean get() = java.lang.Boolean.getBoolean(USE_PRODUCTION_TTL_FOR_TESTS_KEY)

  fun createConnector(
    connectorParams: ConnectorParams,
    taskId: ExternalSystemTaskId?,
    listener: ExternalSystemTaskNotificationListener?,
  ): GradleConnector {
    val connector: GradleConnector
    if (connectorParams.environmentConfigurationProvider != null) {
      connector = TargetGradleConnector(connectorParams.environmentConfigurationProvider, taskId, listener, connectorParams.taskState)
    }
    else {
      connector = GradleConnector.newConnector()
    }
    val projectDir = File(connectorParams.projectPath)
    // GradleExecutionSettings.serviceDirectory contains the path in IDE "host" system file format
    val localPathToGradleUserHome = connectorParams.serviceDirectory

    if (connectorParams.distributionType == DistributionType.LOCAL) {
      // GradleExecutionSettings.gradleHome contains the path in IDE "host" system file format for compatibility reasons
      val localPathToGradleHome = connectorParams.gradleHome
      if (localPathToGradleHome != null) {
        if (connector is TargetGradleConnector) {
          val targetPathMapper = connectorParams.environmentConfigurationProvider?.pathMapper
          val targetGradleHomePath = targetPathMapper.maybeConvertToRemote(localPathToGradleHome)
          // can not use system dependant java.io.File to pass the value to target Gradle runner i.e. GradleConnector#useInstallation(java.io.File)
          connector.useInstallation(TargetValue.create(localPathToGradleHome, resolvedPromise(targetGradleHomePath)))
        }
        else {
          connector.useInstallation(File(localPathToGradleHome))
        }
      }
    }
    else if (connectorParams.distributionType == DistributionType.WRAPPED) {
      if (connectorParams.wrapperPropertyFile != null) {
        DistributionFactoryExt.setWrappedDistribution(connector, connectorParams.wrapperPropertyFile)
      }
    }

    // Setup Grade user home if necessary
    if (localPathToGradleUserHome != null) {
      if (connector is TargetGradleConnector) {
        val targetPathMapper = connectorParams.environmentConfigurationProvider?.pathMapper
        val targetGradleUserHomePath = targetPathMapper.maybeConvertToRemote(localPathToGradleUserHome)
        // can not use system dependant java.io.File to pass the value to target Gradle runner i.e. GradleConnector#useInstallation(java.io.File)
        connector.useGradleUserHomeDir(TargetValue.create(localPathToGradleUserHome, resolvedPromise(targetGradleUserHomePath)))
      }
      else {
        connector.useGradleUserHomeDir(File(localPathToGradleUserHome))
      }
    }
    // Setup logging if necessary
    if (connectorParams.verboseProcessing == true && connector is DefaultGradleConnector) {
      connector.setVerboseLogging(true)
    }
    // Use a short daemon TTL during test execution.
    val ttl = if (!USE_PRODUCTION_TTL_FOR_TESTS && application.isUnitTestMode) TTL_FOR_TESTS else connectorParams.ttlMs ?: -1
    if (ttl > 0 && connector is DefaultGradleConnector) {
      connector.daemonMaxIdleTime(ttl, TimeUnit.MILLISECONDS)
    }

    connector.forProjectDirectory(projectDir)
    return connector
  }
}

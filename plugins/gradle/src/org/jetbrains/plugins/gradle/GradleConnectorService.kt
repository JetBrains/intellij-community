// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle

import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemExecutionAware.Companion.getEnvironmentConfigurationProvider
import com.intellij.openapi.externalSystem.service.execution.TargetEnvironmentConfigurationProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import org.gradle.tooling.CancellationToken
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.internal.consumer.DefaultGradleConnector
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.execution.target.TargetGradleConnector
import org.jetbrains.plugins.gradle.internal.daemon.GradleDaemonServices
import org.jetbrains.plugins.gradle.service.project.DistributionFactoryExt
import org.jetbrains.plugins.gradle.settings.DistributionType
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.function.Function

/**
 * @author Vladislav.Soroka
 */
@ApiStatus.Internal
@Service
internal class GradleConnectorService(@Suppress("UNUSED_PARAMETER") project: Project) : Disposable {
  private val connectorsMap = ConcurrentHashMap<String, GradleProjectConnection>()

  override fun dispose() {
    if (ApplicationManager.getApplication().isUnitTestMode) return
    disconnectGradleConnections()
    stopIdleDaemonsOfOldVersions()
  }

  private fun stopIdleDaemonsOfOldVersions() {
    if (DISABLE_STOP_OLD_IDLE_DAEMONS) return
    try {
      if (ProjectUtil.getOpenProjects().isEmpty()) {
        val gradleVersion_6_5 = GradleVersion.version("6.5")
        val idleDaemons = GradleDaemonServices.getDaemonsStatus().filter {
          it.status.toLowerCase() == "idle" &&
          GradleVersion.version(it.version) < gradleVersion_6_5
        }
        if (idleDaemons.isNotEmpty()) {
          GradleDaemonServices.stopDaemons(idleDaemons)
        }
      }
    }
    catch (e: Exception) {
      LOG.warn("Failed to stop Gradle daemons during project close", e)
    }
  }

  private fun disconnectGradleConnections() {
    connectorsMap.values.forEach(GradleProjectConnection::disconnect)
    connectorsMap.clear()
  }

  private fun getConnection(connectorParams: ConnectorParams,
                            taskId: ExternalSystemTaskId?,
                            listener: ExternalSystemTaskNotificationListener?): ProjectConnection {
    return connectorsMap.compute(connectorParams.projectPath) { _, conn ->
      if (conn != null) {
        if (canBeReused(conn, connectorParams)) return@compute conn
        else {
          // close obsolete connection, can not disconnect the connector here - it may cause build cancel for the new connection operations
          val unwrappedConnection = conn.connection as NonClosableConnection
          unwrappedConnection.delegate.close()
        }
      }
      val newConnector = createConnector(connectorParams, taskId, listener)
      val newConnection = newConnector.connect()
      check(newConnection != null) {
        "Can't create connection to the target project via gradle tooling api. Project path: '${connectorParams.projectPath}'"
      }

      val wrappedConnection = NonClosableConnection(newConnection)
      return@compute GradleProjectConnection(connectorParams, newConnector, wrappedConnection)
    }!!.connection
  }

  private fun canBeReused(projectConnection: GradleProjectConnection, connectorParams: ConnectorParams): Boolean {
    // don't cache connections for not-yet-installed gradle versions
    if (connectorParams.gradleHome == null) return false
    // don't cache TargetProjectConnection as it doesn't support it yet
    if (projectConnection.connector is TargetGradleConnector) return false
    return connectorParams == projectConnection.params
  }

  private class GradleProjectConnection(val params: ConnectorParams, val connector: GradleConnector, val connection: ProjectConnection) {
    fun disconnect() {
      try {
        connector.disconnect()
      }
      catch (e: Exception) {
        LOG.warn("Failed to disconnect Gradle connector during project close. Project path: '${params.projectPath}'", e)
      }
    }
  }

  private class NonClosableConnection(val delegate: ProjectConnection) : ProjectConnection by delegate {
    override fun close() {
      throw IllegalStateException("This connection should not be closed explicitly.")
    }
  }

  private data class ConnectorParams(
    val projectPath: String,
    val serviceDirectory: String?,
    val distributionType: DistributionType?,
    val gradleHome: String?,
    val javaHome: String?,
    val wrapperPropertyFile: String?,
    val verboseProcessing: Boolean?,
    val ttlMs: Int?,
    val environmentConfigurationProvider: TargetEnvironmentConfigurationProvider?
  )

  companion object {
    private val LOG = logger<GradleConnectorService>()

    /** disable stop IDLE Gradle daemons on IDE project close. Applicable for Gradle versions w/o disconnect support (older than 6.5). */
    private val DISABLE_STOP_OLD_IDLE_DAEMONS = java.lang.Boolean.getBoolean("idea.gradle.disableStopIdleDaemonsOnProjectClose")

    @JvmStatic
    private fun getInstance(projectPath: String, taskId: ExternalSystemTaskId?): GradleConnectorService? {
      var project = taskId?.findProject()
      if (project == null) {
        for (openProject in ProjectUtil.getOpenProjects()) {
          val projectBasePath = openProject.basePath ?: continue
          if (FileUtil.isAncestor(projectBasePath, projectPath, false)) {
            project = openProject
            break
          }
        }
      }
      return project?.getService(GradleConnectorService::class.java)
    }

    @JvmStatic
    fun <R : Any?> withGradleConnection(
      projectPath: String,
      taskId: ExternalSystemTaskId?,
      executionSettings: GradleExecutionSettings? = null,
      listener: ExternalSystemTaskNotificationListener? = null,
      cancellationToken: CancellationToken? = null,
      function: Function<ProjectConnection, R>
    ): R {
      val targetEnvironmentConfigurationProvider = executionSettings?.getEnvironmentConfigurationProvider()
      val connectionParams = ConnectorParams(
        projectPath,
        executionSettings?.serviceDirectory,
        executionSettings?.distributionType,
        executionSettings?.gradleHome,
        executionSettings?.javaHome,
        executionSettings?.wrapperPropertyFile,
        executionSettings?.isVerboseProcessing,
        executionSettings?.remoteProcessIdleTtlInMs?.toInt(),
        targetEnvironmentConfigurationProvider
      )
      val connectionService = getInstance(projectPath, taskId)
      if (connectionService != null) {
        val connection = connectionService.getConnection(connectionParams, taskId, listener)
        return if (connection is NonClosableConnection) {
          function.apply(connection)
        }
        else {
          connection.use(function::apply)
        }
      }
      else {
        val newConnector = createConnector(connectionParams, taskId, listener)
        val connection = newConnector.connect()
        return connection.use(function::apply)
      }
    }

    private fun createConnector(connectorParams: ConnectorParams,
                                taskId: ExternalSystemTaskId?,
                                listener: ExternalSystemTaskNotificationListener?): GradleConnector {
      val connector: GradleConnector
      if (connectorParams.environmentConfigurationProvider != null) {
        connector = TargetGradleConnector(connectorParams.environmentConfigurationProvider, taskId, listener)
      }
      else {
        connector = GradleConnector.newConnector()
      }
      val projectDir = File(connectorParams.projectPath)
      val gradleUserHome = if (connectorParams.serviceDirectory == null) null else File(connectorParams.serviceDirectory)

      if (connectorParams.distributionType == DistributionType.LOCAL) {
        val gradleHome = if (connectorParams.gradleHome == null) null else File(connectorParams.gradleHome)
        if (gradleHome != null) {
          connector.useInstallation(gradleHome)
        }
      }
      else if (connectorParams.distributionType == DistributionType.WRAPPED) {
        if (connectorParams.wrapperPropertyFile != null) {
          DistributionFactoryExt.setWrappedDistribution(connector, connectorParams.wrapperPropertyFile, gradleUserHome, projectDir)
        }
      }

      // Setup Grade user home if necessary
      if (gradleUserHome != null) {
        connector.useGradleUserHomeDir(gradleUserHome)
      }
      // Setup logging if necessary
      if (connectorParams.verboseProcessing == true && connector is DefaultGradleConnector) {
        connector.setVerboseLogging(true)
      }
      // do not spawn gradle daemons during test execution
      val app = ApplicationManager.getApplication()
      val ttl = if (app != null && app.isUnitTestMode) 10000 else connectorParams.ttlMs ?: -1
      if (ttl > 0 && connector is DefaultGradleConnector) {
        connector.daemonMaxIdleTime(ttl, TimeUnit.MILLISECONDS)
      }

      connector.forProjectDirectory(projectDir)
      return connector
    }
  }
}
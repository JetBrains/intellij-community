// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.target

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.service.execution.TargetEnvironmentConfigurationProvider
import org.gradle.internal.time.Time
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.internal.consumer.*
import java.io.File
import java.net.URI
import java.util.concurrent.TimeUnit

class TargetGradleConnector(environmentConfigurationProvider: TargetEnvironmentConfigurationProvider,
                            taskId: ExternalSystemTaskId?,
                            taskListener: ExternalSystemTaskNotificationListener?) : GradleConnector(), ProjectConnectionCloseListener {
  private val connectionFactory: ConnectionFactory = TargetConnectionFactory(environmentConfigurationProvider, taskId, taskListener)
  private val distributionFactory: DistributionFactory = DistributionFactory(Time.clock())
  private var distribution: Distribution? = null
  private val connections = mutableListOf<TargetProjectConnection>()
  private var stopped = false
  private val connectionParamsBuilder = DefaultConnectionParameters.builder()

  fun close() {
  }

  override fun connectionClosed(connection: ProjectConnection?) {
    synchronized(connections) { connections.remove(connection) }
  }

  override fun disconnect() {
    synchronized(connections) {
      stopped = true
      connections.toMutableList().forEach(TargetProjectConnection::disconnect)
      connections.clear()
    }
  }

  override fun useInstallation(gradleHome: File?): GradleConnector {
    distribution = distributionFactory.getDistribution(gradleHome)
    return this
  }

  override fun useGradleVersion(gradleVersion: String?): GradleConnector {
    distribution = distributionFactory.getDistribution(gradleVersion)
    return this
  }

  override fun useDistribution(gradleDistribution: URI?): GradleConnector {
    distribution = distributionFactory.getDistribution(gradleDistribution)
    return this
  }

  fun useClasspathDistribution(): GradleConnector {
    distribution = distributionFactory.classpathDistribution
    return this
  }

  override fun useBuildDistribution(): GradleConnector {
    distribution = null
    return this
  }

  fun useDistributionBaseDir(distributionBaseDir: File?): GradleConnector {
    distributionFactory.setDistributionBaseDir(distributionBaseDir)
    return this
  }

  override fun forProjectDirectory(projectDir: File?): GradleConnector {
    connectionParamsBuilder.setProjectDir(projectDir)
    return this
  }

  override fun useGradleUserHomeDir(gradleUserHomeDir: File?): GradleConnector {
    connectionParamsBuilder.setGradleUserHomeDir(gradleUserHomeDir)
    return this
  }

  fun searchUpwards(searchUpwards: Boolean): GradleConnector {
    connectionParamsBuilder.setSearchUpwards(searchUpwards)
    return this
  }

  fun embedded(embedded: Boolean): GradleConnector {
    connectionParamsBuilder.setEmbedded(embedded)
    return this
  }

  fun daemonMaxIdleTime(timeoutValue: Int, timeoutUnits: TimeUnit?): GradleConnector {
    connectionParamsBuilder.setDaemonMaxIdleTimeValue(timeoutValue)
    connectionParamsBuilder.setDaemonMaxIdleTimeUnits(timeoutUnits)
    return this
  }

  fun daemonBaseDir(daemonBaseDir: File?): GradleConnector {
    connectionParamsBuilder.setDaemonBaseDir(daemonBaseDir)
    return this
  }

  fun setVerboseLogging(verboseLogging: Boolean): GradleConnector {
    connectionParamsBuilder.setVerboseLogging(verboseLogging)
    return this
  }

  @Throws(GradleConnectionException::class)
  override fun connect(): ProjectConnection {
    //LOGGER.debug("Connecting from tooling API consumer version {}", GradleVersion.current().version)
    val connectionParameters: ConnectionParameters = connectionParamsBuilder.build()
    checkNotNull(connectionParameters.projectDir) { "A project directory must be specified before creating a connection." }
    if (distribution == null) {
      val searchUpwards = if (connectionParameters.isSearchUpwards != null) connectionParameters.isSearchUpwards else true
      distribution = distributionFactory.getDefaultDistribution(connectionParameters.projectDir, searchUpwards)
    }
    synchronized(connections) {
      if (stopped) {
        throw IllegalStateException("Tooling API client has been disconnected. No other connections may be used.")
      }
      val connection = connectionFactory.create(distribution, connectionParameters, this)
      connections.add(connection as TargetProjectConnection)
      return connection
    }
  }
}
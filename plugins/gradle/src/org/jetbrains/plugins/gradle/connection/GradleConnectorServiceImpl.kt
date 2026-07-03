// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.connection

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.util.ExternalSystemTelemetryUtil
import com.intellij.openapi.project.Project
import com.intellij.platform.diagnostic.telemetry.helpers.use
import com.intellij.util.ThreeState
import com.intellij.util.application
import org.gradle.initialization.BuildCancellationToken
import org.gradle.tooling.CancellationToken
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.internal.consumer.CancellationTokenInternal
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.connection.GradleConnectorService.Companion.DISABLE_STOP_OLD_IDLE_DAEMONS_KEY
import org.jetbrains.plugins.gradle.connection.GradleConnectorService.Companion.USE_PRODUCTION_DISPOSE_FOR_TESTS_KEY
import org.jetbrains.plugins.gradle.execution.target.TargetGradleConnector
import org.jetbrains.plugins.gradle.internal.daemon.getDaemonsStatus
import org.jetbrains.plugins.gradle.internal.daemon.gracefulStopDaemons
import org.jetbrains.plugins.gradle.internal.daemon.stopDaemons
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.settings.GradleSettingsListener
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Function

/**
 * @author Vladislav.Soroka
 */
internal class GradleConnectorServiceImpl(project: Project) : GradleConnectorService, Disposable {

  private val connectorsMap = ConcurrentHashMap<String, GradleProjectConnection>()
  private val cancellationTokens = ConcurrentCollectionFactory.createConcurrentSet<BuildCancellationToken>()
  private val knownGradleUserHomes = ConcurrentCollectionFactory.createConcurrentSet<String>()

  @Volatile
  private var shutdownStarted = ThreeState.UNSURE

  init {
    Runtime.getRuntime().addShutdownHook(object : Thread("Shutdown hook to get to know whether shutdown is started") {
      override fun start() {
        shutdownStarted = ThreeState.YES
        super.start()
      }
    })

    // Always search for Gradle connections in the default gradle user home
    knownGradleUserHomes += ""
    GradleSettings.getInstance(project).serviceDirectoryPath?.let {
      knownGradleUserHomes += it
    }

    project.messageBus.connect(this).subscribe(GradleSettingsListener.TOPIC, object : GradleSettingsListener {
      override fun onServiceDirectoryPathChange(oldPath: String?, newPath: String?) {
        newPath?.let { knownGradleUserHomes.add(it) }
      }
    })
  }

  override fun getKnownGradleUserHomes(): Set<String> {
    return knownGradleUserHomes
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
      .spanBuilder("GradleConnection")
      .use {
        val buildCancellationToken = (cancellationToken as? CancellationTokenInternal)?.token
        buildCancellationToken?.let { cancellationTokens.add(it) }
        try {
          val connectionParams = ConnectorParams(projectPath, executionSettings)
          val connection = getConnection(connectionParams, taskId, listener)
          return@use if (connection is NonClosableConnection) {
            function.apply(connection)
          }
          else {
            connection.use(function::apply)
          }
        }
        finally {
          buildCancellationToken?.let { cancellationTokens.remove(it) }
        }
      }
  }

  override fun dispose() {
    if (!USE_PRODUCTION_DISPOSE_FOR_TESTS && application.isUnitTestMode) return
    disconnectGradleConnections()
    stopIdleDaemonsOfOldVersions()
  }

  private fun stopIdleDaemonsOfOldVersions() {
    if (DISABLE_STOP_OLD_IDLE_DAEMONS) return
    try {
      if (ProjectUtil.getOpenProjects().isEmpty()) {
        val gradleVersion_6_5 = GradleVersion.version("6.5")
        val idleDaemons = getDaemonsStatus(knownGradleUserHomes).filter {
          it.status.lowercase(Locale.getDefault()) == "idle" &&
          GradleVersion.version(it.version) < gradleVersion_6_5
        }
        if (idleDaemons.isNotEmpty()) {
          stopDaemons(knownGradleUserHomes, idleDaemons)
        }
      }
    }
    catch (e: Exception) {
      LOG.warn("Failed to stop Gradle daemons during project close", e)
    }
  }

  private fun disconnectGradleConnections() {
    if (shutdownStarted != ThreeState.YES) {
      // do not call Gradle connector disconnect API when IDE app exit is called during VM shutdown
      // otherwise Gradle call might lead to adding a new shutdown hook, but it's prohibited when shutdown is already started
      try {
        connectorsMap.values.forEach(GradleProjectConnection::disconnect)
      }
      catch (t: Throwable) {
        LOG.warn("Failed to disconnect Gradle connections during project close", t)
        // one more attempt to clean up Gradle daemons
        gracefulStopDaemons()
      }
    }
    else {
      gracefulStopDaemons()
    }
    cancellationTokens.clear()
    connectorsMap.clear()
  }

  private fun gracefulStopDaemons() {
    cancellationTokens.forEach {
      if (!it.isCancellationRequested) {
        try {
          it.cancel()
        }
        catch (t: Throwable) {
          LOG.warn("Failed to cancel build", t)
        }
      }
    }
    gracefulStopDaemons(knownGradleUserHomes)
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
          // do not block the current thread on "ProjectConnection.close" for existing running Gradle operation
          application.executeOnPooledThread {
            unwrappedConnection.delegate.close()
          }
        }
      }
      val newConnector = GradleConnectorFactory.createConnector(connectorParams, taskId, listener)
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
      connector.disconnect()
    }
  }

  private class NonClosableConnection(val delegate: ProjectConnection) : ProjectConnection by delegate {
    override fun close() {
      throw IllegalStateException("This connection should not be closed explicitly.")
    }
  }

  companion object {
    private val LOG = logger<GradleConnectorServiceImpl>()

    /** disable stop IDLE Gradle daemons on IDE project close. Applicable for Gradle versions w/o disconnect support (older than 6.5). */
    private val DISABLE_STOP_OLD_IDLE_DAEMONS: Boolean get() = java.lang.Boolean.getBoolean(DISABLE_STOP_OLD_IDLE_DAEMONS_KEY)

    /** Some tests need the production project-dispose cleanup without changing Gradle daemon TTL. */
    private val USE_PRODUCTION_DISPOSE_FOR_TESTS: Boolean get() = java.lang.Boolean.getBoolean(USE_PRODUCTION_DISPOSE_FOR_TESTS_KEY)
  }
}
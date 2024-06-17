// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.internal.daemon

import com.intellij.DynamicBundle
import com.intellij.gradle.toolingExtension.util.GradleReflectionUtil
import org.gradle.internal.id.IdGenerator
import org.gradle.launcher.daemon.client.DaemonClientConnection
import org.gradle.launcher.daemon.client.DaemonConnector
import org.gradle.launcher.daemon.client.ReportStatusDispatcher
import org.gradle.launcher.daemon.protocol.ReportStatus
import org.gradle.launcher.daemon.protocol.Status
import org.gradle.launcher.daemon.registry.DaemonInfo
import org.gradle.launcher.daemon.registry.DaemonRegistry
import org.gradle.launcher.daemon.registry.DaemonStopEvent
import org.gradle.launcher.daemon.registry.DaemonStopEvents
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.internal.daemon.DaemonAction.Companion.createCommand

@ApiStatus.Internal
class ReportDaemonStatusClient(private val daemonRegistry: DaemonRegistry,
                               private val connector: DaemonConnector,
                               private val idGenerator: IdGenerator<*>) {

  private val reportStatusDispatcher = ReportStatusDispatcher()

  fun get(): List<DaemonState> {
    val daemons: MutableList<DaemonState> = ArrayList()
    forEachAliveDaemon {
      daemons.add(it)
    }
    forEachStoppedDaemon {
      daemons.add(it)
    }
    return daemons
  }

  private fun forEachAliveDaemon(action: (DaemonState) -> Unit) {
    daemonRegistry.all.forEach {
      val connection = connector.maybeConnect(it)
      if (connection != null) {
        val state = getDaemonState(connection, it)
        action(state)
      }
    }
  }

  private fun forEachStoppedDaemon(action: (DaemonState) -> Unit) {
    DaemonStopEvents.uniqueRecentDaemonStopEvents(daemonRegistry.stopEvents)
      .forEach {
        val state = getStoppedDaemonState(it)
        action(state)
      }
  }

  private fun getStoppedDaemonState(event: DaemonStopEvent): DaemonState {
    return DaemonState(
      pid = event.pid,
      token = null,
      version = null,
      status = "Stopped",
      reason = event.reason,
      timestamp = event.getTimestampTime(),
      daemonExpirationStatus = event.getExpirationStatus(),
      daemonOpts = null,
      javaHome = null,
      idleTimeout = null,
      registryDir = null
    )
  }

  private fun getDaemonState(connection: DaemonClientConnection, daemon: DaemonInfo): DaemonState {
    val connectionDaemon = if (connection.daemon is DaemonInfo) connection.daemon as DaemonInfo else daemon
    try {
      val statusCommand = createCommand(ReportStatus::class.java, idGenerator.generateId(), daemon.token)
      val status = reportStatusDispatcher.dispatch(connection, statusCommand)
      return getState(connectionDaemon, status)
    }
    finally {
      connection.stop()
    }
  }

  private fun getState(daemon: DaemonInfo, status: Status?): DaemonState {
    return if (status != null) {
      DaemonState(
        pid = daemon.pid,
        token = daemon.token,
        version = status.version,
        status = status.status,
        reason = null,
        timestamp = daemon.getLastBusyTimestamp(),
        daemonExpirationStatus = null,
        daemonOpts = daemon.getDaemonOpts(),
        javaHome = daemon.getJavaHome(),
        idleTimeout = daemon.getIdleTimeout(),
        registryDir = daemon.getRegistryDir()
      )
    }
    else {
      DaemonState(
        pid = daemon.pid,
        token = daemon.token,
        version = "UNKNOWN",
        status = "UNKNOWN",
        reason = null,
        timestamp = daemon.getLastBusyTimestamp(),
        daemonExpirationStatus = null,
        daemonOpts = daemon.getDaemonOpts(),
        javaHome = daemon.getJavaHome(),
        idleTimeout = daemon.getIdleTimeout(),
        registryDir = daemon.getRegistryDir()
      )
    }
  }

  private fun DaemonStopEvent.getTimestampTime() = timestamp.time

  private fun DaemonStopEvent.getExpirationStatus(): String = status?.name?.replace("_", " ")?.lowercase(DynamicBundle.getLocale()) ?: ""

  private fun DaemonInfo.getRegistryDir() = context.daemonRegistryDir

  private fun DaemonInfo.getJavaHome() = context.javaHome

  private fun DaemonInfo.getIdleTimeout() = context.idleTimeout

  /**
   *  In Gradle 8.8, the signature of the `getDaemonOpts` method was changed.
   *  Before version 8.8 the return type was `List<String>`, after version 8.8 the return was changed to `Collection<String>`.
   *  As the result, non-reflectional invocation of `getDaemonOpts` results in a `MethodNotFoundException`,
   *  because there is no method with this signature.
   */
  private fun DaemonInfo.getDaemonOpts(): Collection<String> {
    try {
      return GradleReflectionUtil.reflectiveCall(
        context,
        "getDaemonOpts",
        java.util.Collection::class.java
      ) as Collection<String>
    }
    catch (t: Throwable) {
      return emptyList()
    }
  }

  private fun DaemonInfo.getLastBusyTimestamp() = lastBusy.time

}
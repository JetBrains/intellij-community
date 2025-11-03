// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.task.debugger

import com.intellij.openapi.components.Service
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelTunnelsApi
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.utils.forwardLocalServer
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.net.NetUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

/**
 * A project-level service that could provide a proxy, that will be available during execution of an [ExternalSystemTaskId].
 * When the task is complete, the proxy server will be stopped.
 */
@Service(Service.Level.PROJECT)
internal class GradleDebuggerProxyManager(
  private val project: Project,
  private val cs: CoroutineScope,
) {

  private class Mapping {

    private data class Entry(val localPort: Int, val remotePort: Int)

    private val entries: MutableList<Entry> = mutableListOf()

    fun add(localPort: Int, remotePort: Int) {
      entries.add(Entry(localPort, remotePort))
    }

    fun getLocalToRemote(remotePort: Int): Int? = entries.find { it.remotePort == remotePort }?.localPort
  }

  private val scopes: ConcurrentHashMap<Long, CoroutineScope> = ConcurrentHashMap()
  private val mappings: ConcurrentHashMap<Long, Mapping> = ConcurrentHashMap()

  /**
   * This proxy pipe will be used to establish the management connection between IDEA and our debugging-related tooling.
   *
   * @param taskId the corresponding task ID.
   * @param dispatchLocalPort the port provided by `ExternalSystemRunnableState.getForkSocket()`. On this port an instance of
   * `ForkedDebuggerThread` will wait for the connection from the daemon.
   * @return an opened port on the remote side.
   */
  fun launchProxy(taskId: ExternalSystemTaskId, dispatchLocalPort: Int): Int {
    val eelDescriptor = project.getEelDescriptor()
    return eelDescriptor.openSink(taskId, dispatchLocalPort)
  }

  /**
   * This proxy pipe will be used to establish the connection between IDEA and the debugger itself.
   * When a connection between our init script and IDEA will be established, the init script will modify an execution-related task and
   * start a debugger on this port.
   * After IDEA receive a heartbeat from the daemon, we could establish a connection with the remote debugger
   * on 127.0.0.1:[getReverseProxyLocalSink]
   * There is no need to explicitly receive/return the local port, because it could be looked up later based on the REMOTE port via
   * [getReverseProxyLocalSink].
   *
   * @param taskId the corresponding task ID.
   * @return an opened port on the **remote** side.
   */
  fun setupReverseProxy(taskId: ExternalSystemTaskId): Int {
    val localSink = NetUtils.findAvailableSocketPort()
    val eelDescriptor = project.getEelDescriptor()
    return eelDescriptor.openSink(taskId, localSink)
  }

  /**
   * Get a local port that corresponds to the remote port.
   *
   * @param reverseProxyRemoteSink a remote port
   * @return a local port that could be used to access [reverseProxyRemoteSink]
   */
  fun getReverseProxyLocalSink(reverseProxyRemoteSink: Int): Int {
    return mappings.values
             .firstNotNullOfOrNull { it.getLocalToRemote(reverseProxyRemoteSink) }
           ?: throw IllegalArgumentException("No local sink found for remote port $reverseProxyRemoteSink")
  }

  private fun EelDescriptor.openSink(taskId: ExternalSystemTaskId, localPort: Int): Int {
    val remoteHostAddress = EelTunnelsApi.HostAddress.Builder()
      .connectionTimeout(90.seconds)
      .build()
    return runBlockingCancellable {
      val eelApi = toEelApi()
      val remoteAddress = getTaskScope(taskId)
        .forwardLocalServer(eelApi.tunnels, localPort, remoteHostAddress)
        .await()
      val remotePort = remoteAddress.port.toInt()
      registerMapping(taskId, localPort, remotePort)
      return@runBlockingCancellable remotePort
    }
  }

  private fun registerMapping(taskId: ExternalSystemTaskId, localPort: Int, remotePort: Int) {
    val mapping = mappings.computeIfAbsent(taskId.id) { Mapping() }
    mapping.add(localPort, remotePort)
  }

  private fun getTaskScope(taskId: ExternalSystemTaskId): CoroutineScope {
    return scopes.computeIfAbsent(taskId.id) {
      taskId.onEnd {
        mappings.remove(taskId.id)
        val proxyScope = scopes.remove(taskId.id)
                         ?: throw IllegalStateException("The proxy coroutine scope for task $taskId not found")
        proxyScope.cancel("Task execution was finished")
      }
      return@computeIfAbsent cs.childScope("$taskId proxy coroutineScope")
    }
  }

  private fun ExternalSystemTaskId.onEnd(onTaskFinish: (ExternalSystemTaskId) -> Unit) {
    val notificationManager = ExternalSystemProgressNotificationManager.getInstance()
    notificationManager.addNotificationListener(this, object : ExternalSystemTaskNotificationListener {
      override fun onEnd(projectPath: String, id: ExternalSystemTaskId) {
        onTaskFinish.invoke(id)
      }
    })
  }
}

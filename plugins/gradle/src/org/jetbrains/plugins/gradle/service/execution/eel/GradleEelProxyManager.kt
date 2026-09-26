// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.execution.eel

import com.intellij.openapi.components.Service
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelTunnelsApi
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.platform.eel.provider.utils.forwardLocalPort
import com.intellij.platform.eel.provider.utils.forwardLocalServer
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.net.NetUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

/**
 * A project-level service that could provide a proxy, that will be available during execution of an
 * [com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId].
 * When the task is complete, the proxy server will be stopped.
 */
@Service(Service.Level.PROJECT)
internal class GradleEelProxyManager(
  private val project: Project,
  private val cs: CoroutineScope,
) {

  companion object {
    fun getInstance(project: Project): GradleEelProxyManager = project.getService(GradleEelProxyManager::class.java)
  }

  private val scopes: ConcurrentHashMap<Long, CoroutineScope> = ConcurrentHashMap()
  private val mappings: ConcurrentHashMap<Long, Mapping> = ConcurrentHashMap()

  /**
   * Set up a proxy between %localhost%:[localPort] and %remote_localhost%:[@result].
   *
   * @param taskId the corresponding task ID.
   * @param localPort a local port that should be used to forward traffic to.
   * @return an opened port on the **remote** side.
   */
  fun launchReverseProxy(taskId: ExternalSystemTaskId, localPort: Int): Int = project.getEelDescriptor()
    .openLocalSink(taskId, localPort)

  /**
   * Set up a proxy between %localhost%:[@result] and %remote_localhost%:[@remotePort].
   *
   * @param taskId the corresponding task ID.
   * @param remotePort a remote port that should be forwarded onto the local side.
   * @return an opened port on the **local** side.
   */
  fun launchProxy(taskId: ExternalSystemTaskId, remotePort: Int): Int = project.getEelDescriptor()
    .openRemoteSink(taskId, remotePort)

  /**
   * Get mapping between remote and local port.
   *
   * @param remotePort a remote port
   * @return a local port that could be used to forward traffic to %remoteHost%:[remotePort]
   */
  fun getLocalToRemotePort(remotePort: Int): Int {
    return mappings.values
             .firstNotNullOfOrNull { it.getLocalToRemote(remotePort) }
           ?: throw IllegalArgumentException("No local sink found for the remote port $remotePort")
  }

  /**
   * Get mapping between local and remote port.
   *
   * @param localPort a local port
   * @return a remote port that could be used to access %127.0.0.1%:[localPort]
   */
  fun getRemoteToLocalPort(localPort: Int): Int {
    return mappings.values
             .firstNotNullOfOrNull { it.getRemoteToLocal(localPort) }
           ?: throw IllegalArgumentException("No remote sink found for the local port $localPort")
  }

  private fun EelDescriptor.openLocalSink(taskId: ExternalSystemTaskId, localPort: Int): Int {
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

  private fun EelDescriptor.openRemoteSink(taskId: ExternalSystemTaskId, remotePort: Int): Int {
    val remoteHostAddress = EelTunnelsApi.HostAddress.Builder(remotePort.toUShort())
      .connectionTimeout(90.seconds)
      .build()
    val localPort = NetUtils.findAvailableSocketPort()
    return runBlockingCancellable {
      val eelApi = toEelApi()
      getTaskScope(taskId)
        .forwardLocalPort(eelApi.tunnels, localPort, remoteHostAddress)
      registerMapping(taskId, localPort, remotePort)
      return@runBlockingCancellable localPort
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

  private class Mapping {

    private data class Entry(val localPort: Int, val remotePort: Int)

    private val entries: MutableList<Entry> = mutableListOf()

    fun add(localPort: Int, remotePort: Int) {
      entries.add(Entry(localPort, remotePort))
    }

    fun getLocalToRemote(remotePort: Int): Int? = entries.find { it.remotePort == remotePort }?.localPort

    fun getRemoteToLocal(localPort: Int): Int? = entries.find { it.localPort == localPort }?.remotePort
  }
}
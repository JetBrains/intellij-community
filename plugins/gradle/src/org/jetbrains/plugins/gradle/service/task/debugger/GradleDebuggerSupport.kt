// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.task.debugger

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.rt.execution.ForkedDebuggerHelper.DEBUGGER_AGENT_SINK_PORT_SYS_PROP
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunnableState.BUILD_PROCESS_DEBUGGER_PORT_KEY
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunnableState.DEBUGGER_DISPATCH_ADDR_KEY
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunnableState.DEBUGGER_DISPATCH_PORT_KEY
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.util.net.NetUtils
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionContext
import org.jetbrains.plugins.gradle.service.execution.eel.GradleEelProxyManager
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings

@ApiStatus.Internal
object GradleDebuggerSupport {

  private const val LOCALHOST = "127.0.0.1"
  private const val DEBUGGER_AGENT_JVM_ARG_FORMAT = "-D${DEBUGGER_AGENT_SINK_PORT_SYS_PROP}=%d"

  /**
   * See the documentation entry for [GradleEelProxyManager.getLocalToRemotePort].
   */
  @JvmStatic
  fun getDebuggeeLocalPort(project: Project, mayBeRemotePort: String?): String? {
    if (mayBeRemotePort == null) {
      return null
    }
    val projectEelDescriptor = project.getEelDescriptor()
    if (projectEelDescriptor == LocalEelDescriptor) {
      return mayBeRemotePort
    }
    val proxyManager = GradleEelProxyManager.getInstance(project)
    val localSinkToDebuggee = proxyManager.getLocalToRemotePort(mayBeRemotePort.toInt())
    return localSinkToDebuggee.toString()
  }

  /**
   * See the documentation entry for [GradleEelProxyManager.launchReverseProxy].
   */
  @JvmStatic
  fun setupDebuggerProxy(context: GradleExecutionContext, settings: GradleExecutionSettings) {
    if (context.project.getEelDescriptor() == LocalEelDescriptor) {
      return
    }

    if (settings.isExecutionDebugRequired()) {
      val proxyManager = GradleEelProxyManager.getInstance(context.project)
      settings.replacePortWithProxy(proxyManager, context.taskId, DEBUGGER_DISPATCH_PORT_KEY)

      // This port will be used by the debugger agent ON THE REMOTE SIDE.
      // This proxy pipe will be used to establish the connection between IDEA and the debugger itself.
      // When a connection between our init script and IDEA will be established, the init script will modify an execution-related task and
      // start a debugger on this port.
      // After IDEA receive a heartbeat from the daemon, we could establish a connection with the remote debugger
      // on 127.0.0.1:[getLocalToRemotePort]
      val debuggerAgentSink = proxyManager.launchReverseProxy(context.taskId, NetUtils.findAvailableSocketPort())
      val debugArgument = DEBUGGER_AGENT_JVM_ARG_FORMAT.format(debuggerAgentSink)
      settings.withArguments(debugArgument)
    }

    if (settings.isScriptDebugRequired()) {
      val proxyManager = GradleEelProxyManager.getInstance(context.project)
      settings.replacePortWithProxy(proxyManager, context.taskId, BUILD_PROCESS_DEBUGGER_PORT_KEY)
    }
  }

  private fun GradleExecutionSettings.replacePortWithProxy(
    proxyManager: GradleEelProxyManager,
    taskId: ExternalSystemTaskId,
    portKey: Key<Int>,
  ) {
    // the port provided by `ExternalSystemRunnableState.getForkSocket()`
    // On this port an instance of `ForkedDebuggerThread` will wait for the connection from the daemon
    val sourceLocalPort = getUserData(portKey)
                          ?: throw IllegalStateException("The source port from key $portKey should not be null")

    val proxyPort = proxyManager.launchReverseProxy(taskId, sourceLocalPort)

    putUserData(portKey, proxyPort)
    putUserData(DEBUGGER_DISPATCH_ADDR_KEY, LOCALHOST)
  }

  private fun GradleExecutionSettings.isExecutionDebugRequired(): Boolean {
    val port = getUserData(DEBUGGER_DISPATCH_PORT_KEY) ?: -1
    return port > 0
  }

  private fun GradleExecutionSettings.isScriptDebugRequired(): Boolean {
    val port = getUserData(BUILD_PROCESS_DEBUGGER_PORT_KEY) ?: -1
    return isDebugServerProcess && port > 0
  }
}
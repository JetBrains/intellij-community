// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.task.debugger

import com.intellij.openapi.externalSystem.rt.execution.ForkedDebuggerHelper
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunnableState
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.getEelDescriptor
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionContext
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings

@ApiStatus.Internal
object GradleDebuggerSupport {

  private const val LOCALHOST = "127.0.0.1"
  private const val DEBUGGER_AGENT_JVM_ARG_FORMAT = "-D${ForkedDebuggerHelper.DEBUGGER_AGENT_SINK_PORT_SYS_PROP}=%d"

  /**
   * See the documentation entry for [GradleDebuggerProxyManager.getReverseProxyLocalSink].
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
    val proxyManager = project.getService(GradleDebuggerProxyManager::class.java)
    val localSinkToDebuggee = proxyManager.getReverseProxyLocalSink(mayBeRemotePort.toInt())
    return localSinkToDebuggee.toString()
  }

  /**
   * See the documentation entry for [GradleDebuggerProxyManager.setupReverseProxy].
   */
  @JvmStatic
  fun setupDebuggerProxy(context: GradleExecutionContext, settings: GradleExecutionSettings) {
    val dispatchPort = settings.getUserData(ExternalSystemRunnableState.DEBUGGER_DISPATCH_PORT_KEY) ?: return
    val project = context.project
    if (project.getEelDescriptor() == LocalEelDescriptor) {
      return
    }
    val proxyManager = project.getService(GradleDebuggerProxyManager::class.java)

    val remoteSink: Int = proxyManager.launchProxy(context.taskId, dispatchPort)
    settings.putUserData(ExternalSystemRunnableState.DEBUGGER_DISPATCH_ADDR_KEY, LOCALHOST)
    settings.putUserData(ExternalSystemRunnableState.DEBUGGER_DISPATCH_PORT_KEY, remoteSink)

    val debuggerAgentSink = proxyManager.setupReverseProxy(context.taskId)
    settings.withArguments(DEBUGGER_AGENT_JVM_ARG_FORMAT.format(debuggerAgentSink));
  }
}
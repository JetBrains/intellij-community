// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.sleep

// @spec community/plugins/agent-workbench/spec/agent-sessions-sleep-prevention.spec.md

import com.intellij.jna.JnaLoader
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.ui.mac.foundation.Foundation
import com.intellij.ui.mac.foundation.ID
import com.sun.jna.Native
import com.sun.jna.win32.StdCallLibrary

private val LOG = logger<AgentSleepInhibitor>()

private const val MAC_NS_ACTIVITY_IDLE_SYSTEM_SLEEP_DISABLED: Long = 1L shl 20
private const val MAC_NS_ACTIVITY_USER_INITIATED: Long = 0x00FFFFFFL or MAC_NS_ACTIVITY_IDLE_SYSTEM_SLEEP_DISABLED
private const val MAC_ACTIVITY_REASON: String = "Agent Workbench active session"

private const val WINDOWS_ES_SYSTEM_REQUIRED: Int = 0x00000001
private const val WINDOWS_ES_CONTINUOUS: Int = 0x80000000.toInt()

internal interface AgentSleepInhibitor : Disposable {
  fun acquire(): Boolean

  fun release()

  override fun dispose() {
    release()
  }
}

internal enum class AgentSleepPlatform {
  MAC,
  WINDOWS,
  OTHER,
}

internal fun createAgentSleepInhibitor(
  platform: AgentSleepPlatform = currentAgentSleepPlatform(),
  nativeAvailable: Boolean = JnaLoader.isLoaded(),
  unitTestMode: Boolean = ApplicationManager.getApplication().isUnitTestMode,
  headlessEnvironment: Boolean = ApplicationManager.getApplication().isHeadlessEnvironment,
  macFactory: () -> AgentSleepInhibitor = ::MacAgentSleepInhibitor,
  windowsFactory: () -> AgentSleepInhibitor = ::WindowsAgentSleepInhibitor,
): AgentSleepInhibitor {
  if (unitTestMode || headlessEnvironment || !nativeAvailable) {
    return NoopAgentSleepInhibitor
  }

  val delegate = try {
    when (platform) {
      AgentSleepPlatform.MAC -> macFactory()
      AgentSleepPlatform.WINDOWS -> windowsFactory()
      AgentSleepPlatform.OTHER -> NoopAgentSleepInhibitor
    }
  }
  catch (t: Throwable) {
    LOG.warn("Failed to initialize Agent Workbench sleep inhibitor", t)
    return NoopAgentSleepInhibitor
  }

  return if (delegate === NoopAgentSleepInhibitor) delegate else SafeAgentSleepInhibitor(delegate)
}

internal fun currentAgentSleepPlatform(): AgentSleepPlatform {
  return when {
    SystemInfoRt.isMac -> AgentSleepPlatform.MAC
    SystemInfoRt.isWindows -> AgentSleepPlatform.WINDOWS
    else -> AgentSleepPlatform.OTHER
  }
}

internal fun safeAgentSleepInhibitor(delegate: AgentSleepInhibitor): AgentSleepInhibitor {
  return SafeAgentSleepInhibitor(delegate)
}

private object NoopAgentSleepInhibitor : AgentSleepInhibitor {
  override fun acquire(): Boolean = false

  override fun release() {
  }
}

private class SafeAgentSleepInhibitor(private val delegate: AgentSleepInhibitor) : AgentSleepInhibitor {
  private var disabled = false

  override fun acquire(): Boolean {
    if (disabled) {
      return false
    }

    return runSafely(operation = "acquire") {
      delegate.acquire()
    }
  }

  override fun release() {
    if (disabled) {
      return
    }

    runSafely(operation = "release") {
      delegate.release()
    }
  }

  override fun dispose() {
    if (disabled) {
      return
    }

    runSafely(operation = "dispose") {
      Disposer.dispose(delegate)
    }
  }

  private fun <T> runSafely(operation: String, action: () -> T): T {
    try {
      return action()
    }
    catch (t: Throwable) {
      disabled = true
      LOG.warn("Agent Workbench sleep inhibitor failed during $operation; disabling it for the current session", t)
      runCatching { Disposer.dispose(delegate) }
      @Suppress("UNCHECKED_CAST")
      return when (operation) {
        "acquire" -> false as T
        else -> Unit as T
      }
    }
  }
}

private class MacAgentSleepInhibitor : AgentSleepInhibitor {
  private val processInfo = Foundation.invoke("NSProcessInfo", "processInfo")
  private var activityToken: ID? = null

  override fun acquire(): Boolean {
    if (activityToken != null) {
      return true
    }

    val rawToken = Foundation.invoke(
      processInfo,
      "beginActivityWithOptions:reason:",
      MAC_NS_ACTIVITY_USER_INITIATED,
      Foundation.nsString(MAC_ACTIVITY_REASON),
    )
    check(rawToken != ID.NIL) { "beginActivityWithOptions returned nil" }

    activityToken = Foundation.invoke(rawToken, "retain")
    return true
  }

  override fun release() {
    val currentToken = activityToken ?: return
    activityToken = null

    Foundation.invoke(processInfo, "endActivity:", currentToken)
    Foundation.invoke(currentToken, "release")
  }
}

internal class WindowsAgentSleepInhibitor(
  private val kernel32: WindowsKernel32 = Native.load("kernel32", WindowsKernel32::class.java),
) : AgentSleepInhibitor {
  private var held = false

  override fun acquire(): Boolean {
    if (held) {
      return true
    }

    invokeSetThreadExecutionState(WINDOWS_ES_CONTINUOUS or WINDOWS_ES_SYSTEM_REQUIRED)
    held = true
    return true
  }

  override fun release() {
    if (!held) {
      return
    }

    held = false
    invokeSetThreadExecutionState(WINDOWS_ES_CONTINUOUS)
  }

  private fun invokeSetThreadExecutionState(flags: Int) {
    val result = kernel32.SetThreadExecutionState(flags)
    check(result != 0) {
      val errorCode = kernel32.GetLastError()
      "SetThreadExecutionState($flags) failed: $errorCode"
    }
  }
}

@Suppress("FunctionName")
internal interface WindowsKernel32 : StdCallLibrary {
  fun SetThreadExecutionState(flags: Int): Int

  fun GetLastError(): Int
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.sleep

// @spec community/plugins/agent-workbench/spec/sessions/agent-sessions-sleep-prevention.spec.md

import com.intellij.jna.JnaLoader
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.ui.mac.foundation.Foundation
import com.intellij.ui.mac.foundation.ID
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.Union
import com.sun.jna.WString
import com.sun.jna.win32.StdCallLibrary

private val LOG = logger<AgentSleepInhibitor>()

private const val MAC_NS_ACTIVITY_IDLE_SYSTEM_SLEEP_DISABLED: Long = 1L shl 20
private const val MAC_NS_ACTIVITY_USER_INITIATED: Long = 0x00FFFFFFL or MAC_NS_ACTIVITY_IDLE_SYSTEM_SLEEP_DISABLED
private const val MAC_ACTIVITY_REASON: String = "Agent Workbench active session"

private const val WINDOWS_POWER_REQUEST_CONTEXT_VERSION: Int = 0
private const val WINDOWS_POWER_REQUEST_CONTEXT_SIMPLE_STRING: Int = 0x00000001
internal const val WINDOWS_POWER_REQUEST_SYSTEM_REQUIRED: Int = 1
internal const val WINDOWS_POWER_REQUEST_REASON: String = "Agent Workbench active session"

internal interface AgentSleepInhibitor : Disposable {
  fun acquire(): Boolean

  fun release(): Boolean

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

  override fun release(): Boolean {
    return true
  }
}

private class SafeAgentSleepInhibitor(private val delegate: AgentSleepInhibitor) : AgentSleepInhibitor {
  private var acquireDisabled = false

  override fun acquire(): Boolean {
    if (acquireDisabled) {
      return false
    }

    try {
      return delegate.acquire()
    }
    catch (t: Throwable) {
      acquireDisabled = true
      LOG.warn("Agent Workbench sleep inhibitor failed during acquire; disabling it for the current session", t)
      runCatching { Disposer.dispose(delegate) }
      return false
    }
  }

  override fun release(): Boolean {
    if (acquireDisabled) {
      return true
    }

    try {
      return delegate.release()
    }
    catch (t: Throwable) {
      LOG.warn("Agent Workbench sleep inhibitor failed during release; it will be retried while the blocker is considered held", t)
      return false
    }
  }

  override fun dispose() {
    runCatching {
      Disposer.dispose(delegate)
    }.onFailure { t ->
      LOG.warn("Agent Workbench sleep inhibitor failed during dispose", t)
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

  override fun release(): Boolean {
    val currentToken = activityToken ?: return true

    Foundation.invoke(processInfo, "endActivity:", currentToken)
    activityToken = null
    runCatching {
      Foundation.invoke(currentToken, "release")
    }.onFailure { t ->
      LOG.warn("Failed to release retained Agent Workbench sleep-prevention activity token", t)
    }
    return true
  }
}

internal class WindowsAgentSleepInhibitor(
  private val kernel32: WindowsKernel32 = Native.load("kernel32", WindowsKernel32::class.java),
) : AgentSleepInhibitor {
  private var requestHandle: Pointer? = null
  private var held = false

  override fun acquire(): Boolean {
    if (held) {
      return true
    }

    val handle = requestHandle ?: createRequestHandle().also { requestHandle = it }
    invokePowerSetRequest(handle)
    held = true
    return true
  }

  override fun release(): Boolean {
    if (!held) {
      return true
    }

    val handle = requestHandle
    if (handle == null) {
      held = false
      return true
    }
    invokePowerClearRequest(handle)
    held = false
    return true
  }

  override fun dispose() {
    try {
      release()
    }
    finally {
      closeRequestHandle()
    }
  }

  private fun createRequestHandle(): Pointer {
    val handle = kernel32.PowerCreateRequest(WindowsPowerRequestReasonContext(WINDOWS_POWER_REQUEST_REASON))
    check(!handle.isInvalidHandle()) { "PowerCreateRequest failed: ${kernel32.GetLastError()}" }
    return checkNotNull(handle)
  }

  private fun invokePowerSetRequest(handle: Pointer) {
    check(kernel32.PowerSetRequest(handle, WINDOWS_POWER_REQUEST_SYSTEM_REQUIRED)) {
      "PowerSetRequest($WINDOWS_POWER_REQUEST_SYSTEM_REQUIRED) failed: ${kernel32.GetLastError()}"
    }
  }

  private fun invokePowerClearRequest(handle: Pointer) {
    check(kernel32.PowerClearRequest(handle, WINDOWS_POWER_REQUEST_SYSTEM_REQUIRED)) {
      "PowerClearRequest($WINDOWS_POWER_REQUEST_SYSTEM_REQUIRED) failed: ${kernel32.GetLastError()}"
    }
  }

  private fun closeRequestHandle() {
    val handle = requestHandle ?: return
    requestHandle = null
    held = false
    if (!kernel32.CloseHandle(handle)) {
      LOG.warn("CloseHandle for Agent Workbench sleep inhibitor failed: ${kernel32.GetLastError()}")
    }
  }
}

private fun Pointer?.isInvalidHandle(): Boolean {
  return this == null || Pointer.nativeValue(this) == -1L
}

@Suppress("PropertyName")
@Structure.FieldOrder("Version", "Flags", "Reason")
internal class WindowsPowerRequestReasonContext(reason: String) : Structure() {
  @JvmField
  var Version: Int = WINDOWS_POWER_REQUEST_CONTEXT_VERSION

  @JvmField
  var Flags: Int = WINDOWS_POWER_REQUEST_CONTEXT_SIMPLE_STRING

  @JvmField
  var Reason: WindowsPowerRequestReason = WindowsPowerRequestReason(reason)
}

@Suppress("PropertyName")
@Structure.FieldOrder("SimpleReasonString")
internal class WindowsPowerRequestReason() : Union() {
  @JvmField
  var SimpleReasonString: WString? = null

  init {
    setType("SimpleReasonString")
  }

  constructor(reason: String) : this() {
    SimpleReasonString = WString(reason)
  }
}

@Suppress("FunctionName")
internal interface WindowsKernel32 : StdCallLibrary {
  fun PowerCreateRequest(context: WindowsPowerRequestReasonContext): Pointer?

  fun PowerSetRequest(powerRequest: Pointer, requestType: Int): Boolean

  fun PowerClearRequest(powerRequest: Pointer, requestType: Int): Boolean

  fun CloseHandle(handle: Pointer): Boolean

  fun GetLastError(): Int
}

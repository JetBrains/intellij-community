// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.sessions.sleep.AgentSleepInhibitor
import com.intellij.agent.workbench.sessions.sleep.AgentSleepPlatform
import com.intellij.agent.workbench.sessions.sleep.WINDOWS_POWER_REQUEST_REASON
import com.intellij.agent.workbench.sessions.sleep.WINDOWS_POWER_REQUEST_SYSTEM_REQUIRED
import com.intellij.agent.workbench.sessions.sleep.WindowsAgentSleepInhibitor
import com.intellij.agent.workbench.sessions.sleep.WindowsKernel32
import com.intellij.agent.workbench.sessions.sleep.WindowsPowerRequestReasonContext
import com.intellij.agent.workbench.sessions.sleep.createAgentSleepInhibitor
import com.intellij.agent.workbench.sessions.sleep.safeAgentSleepInhibitor
import com.intellij.openapi.util.Disposer
import com.sun.jna.Pointer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentSleepInhibitorTest {
  @Test
  fun factoryChoosesMacImplementationWhenSupported() {
    val mac = RecordingFactorySleepInhibitor()
    val inhibitor = createAgentSleepInhibitor(
      platform = AgentSleepPlatform.MAC,
      nativeAvailable = true,
      unitTestMode = false,
      headlessEnvironment = false,
      macFactory = { mac },
      windowsFactory = { error("Windows factory should not be used") },
    )

    assertThat(inhibitor.acquire()).isTrue()
    assertThat(mac.acquireCalls).isEqualTo(1)
    assertThat(inhibitor.release()).isTrue()
    assertThat(mac.releaseCalls).isEqualTo(1)
  }

  @Test
  fun factoryChoosesWindowsImplementationWhenSupported() {
    val windows = RecordingFactorySleepInhibitor()
    val inhibitor = createAgentSleepInhibitor(
      platform = AgentSleepPlatform.WINDOWS,
      nativeAvailable = true,
      unitTestMode = false,
      headlessEnvironment = false,
      macFactory = { error("macOS factory should not be used") },
      windowsFactory = { windows },
    )

    assertThat(inhibitor.acquire()).isTrue()
    assertThat(windows.acquireCalls).isEqualTo(1)
    assertThat(inhibitor.release()).isTrue()
    assertThat(windows.releaseCalls).isEqualTo(1)
  }

  @Test
  fun factoryFallsBackToNoopWithoutNativeSupport() {
    val inhibitor = createAgentSleepInhibitor(
      platform = AgentSleepPlatform.MAC,
      nativeAvailable = false,
      unitTestMode = false,
      headlessEnvironment = false,
      macFactory = { error("macOS factory should not be used") },
      windowsFactory = { error("Windows factory should not be used") },
    )

    assertThat(inhibitor.acquire()).isFalse()
    assertThat(inhibitor.release()).isTrue()
  }

  @Test
  fun factoryFallsBackToNoopOnOtherPlatforms() {
    val inhibitor = createAgentSleepInhibitor(
      platform = AgentSleepPlatform.OTHER,
      nativeAvailable = true,
      unitTestMode = false,
      headlessEnvironment = false,
      macFactory = { error("macOS factory should not be used") },
      windowsFactory = { error("Windows factory should not be used") },
    )

    assertThat(inhibitor.acquire()).isFalse()
    assertThat(inhibitor.release()).isTrue()
  }

  @Test
  fun safeWrapperDisablesDelegateAfterAcquireFailure() {
    val delegate = FailingAcquireSleepInhibitor()
    val inhibitor = safeAgentSleepInhibitor(delegate)

    assertThat(inhibitor.acquire()).isFalse()
    assertThat(inhibitor.acquire()).isFalse()
    assertThat(inhibitor.release()).isTrue()

    assertThat(delegate.acquireAttempts).isEqualTo(1)
    assertThat(delegate.releaseAttempts).isZero()
  }

  @Test
  fun safeWrapperKeepsReleaseRetryAvailable() {
    val delegate = ReleaseFailsOnceSleepInhibitor()
    val inhibitor = safeAgentSleepInhibitor(delegate)

    assertThat(inhibitor.acquire()).isTrue()
    assertThat(inhibitor.release()).isFalse()
    assertThat(inhibitor.release()).isTrue()

    assertThat(delegate.releaseAttempts).isEqualTo(2)
  }

  @Test
  fun windowsInhibitorUsesPowerRequestSystemRequired() {
    val kernel32 = RecordingWindowsKernel32()
    val inhibitor = WindowsAgentSleepInhibitor(kernel32)

    assertThat(inhibitor.acquire()).isTrue()
    assertThat(inhibitor.acquire()).isTrue()

    assertThat(kernel32.createRequestReasonStrings).containsExactly(WINDOWS_POWER_REQUEST_REASON)
    assertThat(kernel32.powerSetRequestCalls).containsExactly(PowerRequestCall(REQUEST_HANDLE, WINDOWS_POWER_REQUEST_SYSTEM_REQUIRED))

    assertThat(inhibitor.release()).isTrue()
    assertThat(inhibitor.release()).isTrue()

    assertThat(kernel32.powerClearRequestCalls).containsExactly(PowerRequestCall(REQUEST_HANDLE, WINDOWS_POWER_REQUEST_SYSTEM_REQUIRED))
    assertThat(kernel32.closedHandles).isEmpty()
  }

  @Test
  fun windowsInhibitorClosesPowerRequestOnDispose() {
    val kernel32 = RecordingWindowsKernel32()
    val inhibitor = WindowsAgentSleepInhibitor(kernel32)

    assertThat(inhibitor.acquire()).isTrue()

    Disposer.dispose(inhibitor)

    assertThat(kernel32.powerClearRequestCalls).containsExactly(PowerRequestCall(REQUEST_HANDLE, WINDOWS_POWER_REQUEST_SYSTEM_REQUIRED))
    assertThat(kernel32.closedHandles).containsExactly(REQUEST_HANDLE)
  }

  @Test
  fun windowsInhibitorReportsPowerCreateRequestFailure() {
    val kernel32 = RecordingWindowsKernel32(createRequestResult = null, errorCode = 42)
    val inhibitor = WindowsAgentSleepInhibitor(kernel32)

    assertThatThrownBy { inhibitor.acquire() }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("PowerCreateRequest")
      .hasMessageContaining("42")
  }

  @Test
  fun windowsInhibitorKeepsHeldStateAfterReleaseFailure() {
    val kernel32 = RecordingWindowsKernel32(clearRequestResults = listOf(false, true), errorCode = 42)
    val inhibitor = WindowsAgentSleepInhibitor(kernel32)

    assertThat(inhibitor.acquire()).isTrue()
    assertThatThrownBy { inhibitor.release() }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("PowerClearRequest")
      .hasMessageContaining("42")

    assertThat(inhibitor.release()).isTrue()
    assertThat(kernel32.powerClearRequestCalls).containsExactly(
      PowerRequestCall(REQUEST_HANDLE, WINDOWS_POWER_REQUEST_SYSTEM_REQUIRED),
      PowerRequestCall(REQUEST_HANDLE, WINDOWS_POWER_REQUEST_SYSTEM_REQUIRED),
    )
  }
}

private val REQUEST_HANDLE: Pointer = Pointer.createConstant(0xA11CE)

private data class PowerRequestCall(
  @JvmField val handle: Pointer,
  @JvmField val requestType: Int,
)

private class RecordingFactorySleepInhibitor : AgentSleepInhibitor {
  var acquireCalls: Int = 0
  var releaseCalls: Int = 0
  private var held = false

  override fun acquire(): Boolean {
    acquireCalls++
    held = true
    return true
  }

  override fun release(): Boolean {
    if (!held) {
      return true
    }

    held = false
    releaseCalls++
    return true
  }
}

private class FailingAcquireSleepInhibitor : AgentSleepInhibitor {
  var acquireAttempts: Int = 0
  var releaseAttempts: Int = 0

  override fun acquire(): Boolean {
    acquireAttempts++
    error("boom")
  }

  override fun release(): Boolean {
    releaseAttempts++
    return true
  }

  override fun dispose() {
  }
}

private class ReleaseFailsOnceSleepInhibitor : AgentSleepInhibitor {
  var releaseAttempts: Int = 0

  override fun acquire(): Boolean = true

  override fun release(): Boolean {
    releaseAttempts++
    if (releaseAttempts == 1) {
      error("boom")
    }
    return true
  }
}

@Suppress("TestFunctionName")
private class RecordingWindowsKernel32(
  private val createRequestResult: Pointer? = REQUEST_HANDLE,
  private val setRequestResults: List<Boolean> = emptyList(),
  private val clearRequestResults: List<Boolean> = emptyList(),
  private val closeHandleResult: Boolean = true,
  private val errorCode: Int = 0,
) : WindowsKernel32 {
  val createRequestReasonStrings = mutableListOf<String>()
  val powerSetRequestCalls = mutableListOf<PowerRequestCall>()
  val powerClearRequestCalls = mutableListOf<PowerRequestCall>()
  val closedHandles = mutableListOf<Pointer>()
  private val setRequestResultsQueue = ArrayDeque<Boolean>().apply { addAll(setRequestResults) }
  private val clearRequestResultsQueue = ArrayDeque<Boolean>().apply { addAll(clearRequestResults) }

  override fun PowerCreateRequest(context: WindowsPowerRequestReasonContext): Pointer? {
    createRequestReasonStrings += context.Reason.SimpleReasonString.toString()
    return createRequestResult
  }

  override fun PowerSetRequest(powerRequest: Pointer, requestType: Int): Boolean {
    powerSetRequestCalls += PowerRequestCall(powerRequest, requestType)
    return setRequestResultsQueue.nextOrDefault(true)
  }

  override fun PowerClearRequest(powerRequest: Pointer, requestType: Int): Boolean {
    powerClearRequestCalls += PowerRequestCall(powerRequest, requestType)
    return clearRequestResultsQueue.nextOrDefault(true)
  }

  override fun CloseHandle(handle: Pointer): Boolean {
    closedHandles += handle
    return closeHandleResult
  }

  override fun GetLastError(): Int = errorCode
}

private fun ArrayDeque<Boolean>.nextOrDefault(defaultValue: Boolean): Boolean {
  return if (isEmpty()) defaultValue else removeFirst()
}

package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.sessions.sleep.AgentSleepInhibitor
import com.intellij.agent.workbench.sessions.sleep.AgentSleepPlatform
import com.intellij.agent.workbench.sessions.sleep.WindowsAgentSleepInhibitor
import com.intellij.agent.workbench.sessions.sleep.WindowsKernel32
import com.intellij.agent.workbench.sessions.sleep.createAgentSleepInhibitor
import com.intellij.agent.workbench.sessions.sleep.safeAgentSleepInhibitor
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

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
    inhibitor.release()
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
    inhibitor.release()
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
  }

  @Test
  fun safeWrapperDisablesDelegateAfterAcquireFailure() {
    val delegate = FailingSleepInhibitor()
    val inhibitor = safeAgentSleepInhibitor(delegate)

    assertThat(inhibitor.acquire()).isFalse()
    assertThat(inhibitor.acquire()).isFalse()
    inhibitor.release()

    assertThat(delegate.acquireAttempts).isEqualTo(1)
    assertThat(delegate.releaseAttempts).isZero()
  }

  @Test
  fun windowsInhibitorUsesExpectedExecutionStateFlags() {
    val kernel32 = RecordingWindowsKernel32()
    val inhibitor = WindowsAgentSleepInhibitor(kernel32)

    assertThat(inhibitor.acquire()).isTrue()
    assertThat(inhibitor.acquire()).isTrue()

    inhibitor.release()
    inhibitor.release()

    assertThat(kernel32.executionStateCalls).containsExactly(0x80000000.toInt() or 0x00000001, 0x80000000.toInt())
  }

  @Test
  fun windowsInhibitorReportsNativeFailure() {
    val kernel32 = RecordingWindowsKernel32(result = 0, errorCode = 42)
    val inhibitor = WindowsAgentSleepInhibitor(kernel32)

    assertThatThrownBy { inhibitor.acquire() }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("SetThreadExecutionState")
      .hasMessageContaining("42")
  }
}

private class RecordingFactorySleepInhibitor : AgentSleepInhibitor {
  var acquireCalls: Int = 0
  var releaseCalls: Int = 0
  private var held = false

  override fun acquire(): Boolean {
    acquireCalls++
    held = true
    return true
  }

  override fun release() {
    if (!held) {
      return
    }

    held = false
    releaseCalls++
  }
}

private class FailingSleepInhibitor : AgentSleepInhibitor {
  var acquireAttempts: Int = 0
  var releaseAttempts: Int = 0

  override fun acquire(): Boolean {
    acquireAttempts++
    error("boom")
  }

  override fun release() {
    releaseAttempts++
  }

  override fun dispose() {
  }
}

@Suppress("TestFunctionName")
private class RecordingWindowsKernel32(
  private val result: Int = 1,
  private val errorCode: Int = 0,
) : WindowsKernel32 {
  val executionStateCalls = mutableListOf<Int>()

  override fun SetThreadExecutionState(flags: Int): Int {
    executionStateCalls += flags
    return result
  }

  override fun GetLastError(): Int = errorCode
}

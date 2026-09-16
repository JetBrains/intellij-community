package com.intellij.execution.process

import com.pty4j.PtyProcess
import com.pty4j.WinSize
import com.pty4j.windows.conpty.WinConPtyProcess
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

internal class LocalProcessServiceTest {
  private val service = LocalProcessServiceImpl()

  @Test
  fun `a process without a terminal has no control`() {
    assertThat(service.getPtyControl(mock<Process>())).isNull()
  }

  @Test
  fun `a disabled legacy terminal has no control`() {
    val process = mock<LegacyPtyProcess>()
    whenever(process.hasPty()).thenReturn(false)
    assertThat(service.getPtyControl(process)).isNull()
  }

  @Test
  fun `a legacy terminal keeps the default enter behavior`() {
    val process = mock<LegacyPtyProcess>()
    whenever(process.hasPty()).thenReturn(true)

    val control = requireNotNull(service.getPtyControl(process))
    assertThat(control.enterKeyCode).isNull()
    assertThat(control.isConPty).isFalse()
    assertThat(control.isConPtyInheritCursor).isFalse()
    control.setWindowSize(132, 43)
    verify(process).setWindowSize(132, 43)
  }

  @Test
  fun `a process can supply its own control`() {
    val process = mock<ControlledProcess>()
    whenever(process.enterKeyCode).thenReturn(13)

    val control = requireNotNull(service.getPtyControl(process))
    assertThat(control).isSameAs(process)
    assertThat(control.enterKeyCode).isEqualTo(13.toByte())
    control.setWindowSize(80, 24)
    verify(process).setWindowSize(80, 24)
  }

  @Test
  fun `a native terminal exposes its enter code and console mode`() {
    val process = mock<PtyProcess>()
    whenever(process.enterKeyCode).thenReturn(10)
    whenever(process.isConsoleMode).thenReturn(true)

    val control = requireNotNull(service.getPtyControl(process))
    assertThat(control.enterKeyCode).isEqualTo(10.toByte())
    assertThat(control.isConsoleMode).isTrue()
    assertThat(control.isConPty).isFalse()
    assertThat(control.isConPtyInheritCursor).isFalse()
    control.setWindowSize(132, 43)
    verify(process).winSize = WinSize(132, 43)
  }

  @Test
  fun `a resize failure reaches the caller`() {
    val process = mock<PtyProcess>()
    val failure = IllegalStateException("The process has exited")
    whenever(process.setWinSize(WinSize(80, 24))).thenThrow(failure)

    val control = requireNotNull(service.getPtyControl(process))
    assertThatThrownBy { control.setWindowSize(80, 24) }.isSameAs(failure)
  }

  @Test
  fun `a ConPTY terminal exposes its cursor mode`() {
    val process = mock<WinConPtyProcess>()
    whenever(process.isConPtyInheritCursor).thenReturn(true, false)

    val control = requireNotNull(service.getPtyControl(process))
    assertThat(control.isConPty).isTrue()
    assertThat(control.isConPtyInheritCursor).isTrue()
    assertThat(control.isConPtyInheritCursor).isFalse()
  }

  private abstract class LegacyPtyProcess : Process(), PtyBasedProcess

  private abstract class ControlledProcess : Process(), PtyProcessControl
}

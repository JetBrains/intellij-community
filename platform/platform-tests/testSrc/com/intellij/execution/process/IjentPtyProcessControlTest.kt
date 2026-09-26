package com.intellij.execution.process

import com.intellij.platform.eel.EelPosixProcess
import com.intellij.platform.eel.EelProcess
import com.intellij.platform.eel.SafeDeferred
import com.intellij.platform.eel.provider.utils.asEelChannel
import com.intellij.platform.eel.provider.utils.consumeAsEelChannel
import com.intellij.platform.ijent.IjentChildPtyProcessAdapter
import com.intellij.platform.ijent.spi.IjentThreadPool
import com.intellij.testFramework.common.timeoutRunBlocking
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@Timeout(30)
internal class IjentPtyProcessControlTest {
  @Test
  fun `a remote terminal preserves its enter code and resize behavior`(): Unit =
    timeoutRunBlocking(context = IjentThreadPool.coroutineContext) {
      val process = remoteProcess()
      coEvery { process.resizePty(any(), any()) } just Runs
      val adapter = IjentChildPtyProcessAdapter(this, process)
      val control: PtyProcessControl = adapter

      assertThat(control.enterKeyCode).isEqualTo(13.toByte())
      assertThat(control.isConPty).isFalse()
      control.setWindowSize(132, 43)
      coVerify(exactly = 1) { process.resizePty(132, 43) }
      assertThatThrownBy { adapter.pid() }.isInstanceOf(UnsupportedOperationException::class.java)
    }

  @Test
  fun `a remote resize failure retains its cause`(): Unit = timeoutRunBlocking(context = IjentThreadPool.coroutineContext) {
    val process = remoteProcess()
    val failure = EelProcess.ResizePtyError.ProcessExited()
    coEvery { process.resizePty(any(), any()) } throws failure
    val adapter = IjentChildPtyProcessAdapter(this, process)

    assertThatThrownBy { adapter.setWindowSize(80, 24) }
      .isInstanceOf(IllegalStateException::class.java)
      .hasCause(failure)
  }

  @Test
  fun `the exit future retains the process identity`(): Unit = timeoutRunBlocking(context = IjentThreadPool.coroutineContext) {
    val process = remoteProcess()
    val exitCode = CompletableDeferred<Int>()
    every { process.exitCode } returns SafeDeferred(exitCode)
    val adapter = IjentChildPtyProcessAdapter(this, process)
    val onExit = adapter.onExit()

    assertThat(adapter.isAlive).isTrue()
    exitCode.complete(42)
    assertThat(onExit.join()).isSameAs(adapter)
    assertThat(adapter.exitValue()).isEqualTo(42)
    assertThat(adapter.isAlive).isFalse()
  }

  private fun remoteProcess(): EelPosixProcess = mockk {
    every { stdout } returns ByteArrayInputStream(byteArrayOf()).consumeAsEelChannel()
    every { stderr } returns ByteArrayInputStream(byteArrayOf()).consumeAsEelChannel()
    every { stdin } returns ByteArrayOutputStream().asEelChannel()
  }
}

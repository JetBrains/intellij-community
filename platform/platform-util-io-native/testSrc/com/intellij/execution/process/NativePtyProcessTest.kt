package com.intellij.execution.process

import com.intellij.util.system.LowLevelLocalMachineAccess
import com.intellij.util.system.OS
import com.pty4j.WinSize
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Timeout(30)
internal class NativePtyProcessTest {
  @Test
  fun `a native terminal supports input and resize`() {
    checkTerminal(consoleMode = false)
  }

  @Test
  fun `a native console supports input and resize`() {
    checkTerminal(consoleMode = true)
  }

  @OptIn(LowLevelLocalMachineAccess::class)
  private fun checkTerminal(consoleMode: Boolean) {
    val windows = OS.CURRENT == OS.Windows
    val command = if (windows) {
      listOf("cmd.exe", "/d", "/q", "/v:on", "/c", "set /p JNA_INPUT= & echo jna-pty:!JNA_INPUT! & exit /b 23")
    }
    else {
      listOf("/bin/sh", "-c", $$"read -r input; printf 'jna-pty:%s\\n' \"$input\"; stty size; exit 23")
    }
    val service = LocalProcessServiceImpl()
    val options = LocalPtyOptions.defaults().builder()
      .consoleMode(consoleMode)
      .initialColumns(80)
      .initialRows(24)
      .useWinConPty(windows && !consoleMode)
      .build()
    val process = service.startPtyProcess(command, null, System.getenv(), options, redirectErrorStream = true)
    val executor = Executors.newVirtualThreadPerTaskExecutor()
    try {
      val output = executor.submit<String> { process.inputStream.readAllBytes().toString(Charsets.UTF_8) }
      val control = requireNotNull(service.getPtyControl(process))
      assertThat(service.isLocalPtyProcess(process)).isTrue()
      assertThat(service.hasControllingTerminal(process)).isEqualTo(!consoleMode)
      assertThat(control.isConsoleMode).isEqualTo(consoleMode)
      assertThat(control.isConPty).isEqualTo(windows && !consoleMode)
      assertThat(control.isConPtyInheritCursor).isFalse()
      assertThat(process.winSize).isEqualTo(WinSize(80, 24))
      control.setWindowSize(132, 43)
      assertThat(process.winSize).isEqualTo(WinSize(132, 43))

      process.outputStream.write("native-input".toByteArray(Charsets.UTF_8))
      process.outputStream.write(requireNotNull(control.enterKeyCode).toInt())
      process.outputStream.flush()

      assertThat(process.waitFor(10, TimeUnit.SECONDS)).isTrue()
      assertThat(process.exitValue()).isEqualTo(23)
      val text = output.get(10, TimeUnit.SECONDS)
      assertThat(text).contains("jna-pty:native-input")
      if (!windows) {
        assertThat(text).contains("43 132")
      }
    }
    finally {
      process.destroyForcibly()
      process.waitFor(5, TimeUnit.SECONDS)
      process.inputStream.close()
      process.outputStream.close()
      executor.shutdownNow()
    }
  }
}

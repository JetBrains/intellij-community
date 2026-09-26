// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.util.system.LowLevelLocalMachineAccess
import com.intellij.util.system.OS
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException
import java.nio.file.Files

@TestApplication
@OptIn(LowLevelLocalMachineAccess::class)
internal class ShellEnvironmentReaderApplicationTest {
  val tempDir by tempPathFixture()

  // disable.winp=true makes OSProcessUtil.terminateProcessGracefully throw UnsupportedOperationException
  // while keeping killProcessTree working via the WinProcessManager fallback.
  @RegistryKey(key = "disable.winp", value = "true")
  @Test
  fun winShellReaderTerminationWhenGracefulTerminateUnsupported() {
    assumeTrue(OS.CURRENT == OS.Windows)

    val timeout = 1000
    val file = Files.writeString(tempDir.resolve("test.ps1"), "Start-Sleep -Seconds " + timeout * 10 / 1000)
    val command = ShellEnvironmentReader.powerShellCommand(file, null)
    // The reader must recover from the UnsupportedOperationException, force-kill the process,
    // and surface a plain IOException instead of leaking the UOE.
    assertThrows<IOException> {
      ShellEnvironmentReader.readEnvironment(command, timeout.toLong())
    }
  }
}

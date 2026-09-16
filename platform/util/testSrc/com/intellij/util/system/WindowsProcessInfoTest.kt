// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.system

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS.WINDOWS
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.pathString

@EnabledOnOs(WINDOWS)
internal class WindowsProcessInfoTest {
  @Test
  fun `reports the command line of a process`() {
    val path = systemCmdExecutable()
    val process = ProcessBuilder(path.pathString).start()
    try {
      val info = WindowsProcessInfo.get(process.pid()).getOrThrow()
      assertThat(info.commandLine).isEqualTo(path.pathString)
      assertThat(info.pid).isEqualTo(process.pid())
      assertThat(info.parentId).isEqualTo(ProcessHandle.current().pid())
    }
    finally {
      process.destroyForcibly()
      process.waitFor()
    }
  }

  @Test
  fun `splits the arguments of a quoted path`(@TempDir tempDir: Path) {
    // Windows quotes a path that holds a space, so a split on a space gives the wrong arguments.
    val executable = tempDir.resolve("dir with space").createDirectories().resolve("cmd.exe")
    systemCmdExecutable().copyTo(executable)

    val process = ProcessBuilder(executable.pathString, "/c", "pause").start()
    try {
      val info = WindowsProcessInfo.get(process.pid()).getOrThrow()
      assertThat(info.arguments).containsExactly(executable.pathString, "/c", "pause")
      assertThat(info.executable).isEqualTo(executable)
    }
    finally {
      process.destroyForcibly()
      process.waitFor()
    }
  }
}

/** The system `cmd.exe`. A running process holds its own image open, which [WindowsFileLocksTest] needs. */
internal fun systemCmdExecutable(): Path = Path(System.getenv("SystemRoot") ?: "c:\\windows", "system32/cmd.exe")

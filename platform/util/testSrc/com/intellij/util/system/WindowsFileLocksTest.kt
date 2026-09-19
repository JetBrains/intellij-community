// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.system

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS.WINDOWS
import kotlin.io.path.pathString

@EnabledOnOs(WINDOWS)
internal class WindowsFileLocksTest {
  @Test
  fun `names the process that holds a file open`() {
    val path = systemCmdExecutable()
    val process = ProcessBuilder(path.pathString).start()
    try {
      val pids = WindowsFileLocks.processesUsingPath(path).getOrThrow().map { it.pid() }
      assertThat(pids).contains(process.pid())
    }
    finally {
      process.destroyForcibly()
      process.waitFor()
    }
  }

  /** The query opens the file itself, so a caller must know that the answer holds no own process. */
  @Test
  fun `does not name the calling process`() {
    val pids = WindowsFileLocks.processesUsingPath(systemCmdExecutable()).getOrThrow().map { it.pid() }
    assertThat(pids).doesNotContain(ProcessHandle.current().pid())
  }
}

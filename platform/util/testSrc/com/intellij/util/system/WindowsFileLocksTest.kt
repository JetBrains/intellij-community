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
}

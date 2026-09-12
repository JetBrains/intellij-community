// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.system

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.TimeUnit

class ProcessMetricsTest {
  @Test
  fun `reports the resident set size and the CPU time of the current process`() {
    val pid = ProcessHandle.current().pid()

    val residentSetSize = ProcessMetrics.residentSetSize(pid)
    assertThat(residentSetSize).isNotNull()
    assertThat(residentSetSize!!).isGreaterThan(1L shl 20)

    val cpuTime = ProcessMetrics.cpuTime(pid)
    assertThat(cpuTime).isNotNull()
    assertThat(cpuTime!!).isGreaterThanOrEqualTo(Duration.ZERO)
  }

  @Test
  fun `reports the resident set size of a child process`() {
    val child = startJavaChild()
    try {
      val residentSetSize = ProcessMetrics.residentSetSize(child.pid())
      // The child can exit before the query. Then the reader must report nothing instead of a failure.
      if (child.isAlive) {
        assertThat(residentSetSize).isNotNull()
        assertThat(residentSetSize!!).isGreaterThan(0L)
      }
    }
    finally {
      child.destroyForcibly()
      child.waitFor(1, TimeUnit.MINUTES)
    }
  }

  @Test
  fun `returns null for a process that exited`() {
    val child = startJavaChild()
    assertThat(child.waitFor(1, TimeUnit.MINUTES)).isTrue()

    assertThat(ProcessMetrics.residentSetSize(child.pid())).isNull()
    assertThat(ProcessMetrics.cpuTime(child.pid())).isNull()
  }

  /** Starts `java -version` with the JDK of the current process. It exits within a second. */
  private fun startJavaChild(): Process {
    val java = ProcessHandle.current().info().command().orElseThrow()
    return ProcessBuilder(java, "-version").redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
  }
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.system

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS as JunitOS
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

  /**
   * The JDK reports no CPU time for a child process on macOS, so the reader falls back to a `proc_pidinfo` downcall.
   * The kernel measures the time in Mach absolute time units, and this test catches a wrong conversion by comparing with `ps`.
   */
  @Test
  @EnabledOnOs(JunitOS.MAC)
  fun `reports the CPU time of a live child process on macOS`() {
    // The shell burns the CPU for one to two seconds, prints a marker, and sleeps, so that its CPU time is stable while it is measured.
    val child = ProcessBuilder("/bin/sh", "-c", "end=\$((SECONDS + 2)); while [ \$SECONDS -lt \$end ]; do :; done; echo done; sleep 60")
      .redirectError(ProcessBuilder.Redirect.DISCARD)
      .start()
    try {
      assertThat(child.inputStream.bufferedReader().readLine()).isEqualTo("done")
      assertThat(child.isAlive).isTrue()

      val cpuTime = ProcessMetrics.cpuTime(child.pid())
      assertThat(cpuTime).isNotNull()
      val psCpuTime = psCpuTime(child.pid())
      assertThat(cpuTime!!)
        .isGreaterThan(Duration.ofMillis(100))
        .isBetween(psCpuTime.minusMillis(100), psCpuTime.plusMillis(100))
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

  /** Reads the accumulated CPU time of a process through `ps`, which prints it as `[[hours:]minutes:]seconds.hundredths`. */
  private fun psCpuTime(pid: Long): Duration {
    val ps = ProcessBuilder("ps", "-o", "time=", "-p", pid.toString()).redirectError(ProcessBuilder.Redirect.DISCARD).start()
    val output = ps.inputStream.bufferedReader().readText().trim()
    assertThat(ps.waitFor(1, TimeUnit.MINUTES)).isTrue()
    assertThat(output).isNotEmpty()
    var millis = 0L
    for (field in output.split(':')) {
      millis = millis * 60 + (field.toDouble() * 1000).toLong()
    }
    return Duration.ofMillis(millis)
  }

  /** Starts `java -version` with the JDK of the current process. It exits within a second. */
  private fun startJavaChild(): Process {
    val java = ProcessHandle.current().info().command().orElseThrow()
    return ProcessBuilder(java, "-version").redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
  }
}

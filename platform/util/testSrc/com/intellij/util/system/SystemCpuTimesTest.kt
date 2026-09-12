// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.system

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS.LINUX
import org.junit.jupiter.api.condition.OS.MAC
import org.junit.jupiter.api.condition.OS.WINDOWS

class SystemCpuTimesTest {
  @Test
  fun `parses the cpu line of proc stat`() {
    val lines = listOf(
      "cpu  100 20 30 4000 50 6 7 8 0 0",
      "cpu0 50 10 15 2000 25 3 4 4 0 0",
      "intr 12345",
    )
    // busy: user + nice + system + irq + softirq + steal; idle: idle + iowait; both in 10 ms ticks
    assertThat(SystemCpuTimes.parseProcStat(lines)).isEqualTo(CpuTimes(busyMillis = 1710, idleMillis = 40500))
  }

  @Test
  fun `returns null for a proc stat without a cpu line`() {
    assertThat(SystemCpuTimes.parseProcStat(listOf("intr 1", "ctxt 2"))).isNull()
  }

  @Test
  @EnabledOnOs(MAC, WINDOWS, LINUX)
  fun `reads growing counters`() {
    val first = SystemCpuTimes.read()
    assertThat(first).isNotNull()
    assertThat(first!!.busyMillis).isGreaterThan(0)
    assertThat(first.idleMillis).isGreaterThanOrEqualTo(0)

    val deadline = System.nanoTime() + 50_000_000L
    var spin = 0L
    while (System.nanoTime() < deadline) {
      spin++
    }
    assertThat(spin).isGreaterThan(0)

    val second = SystemCpuTimes.read()
    assertThat(second).isNotNull()
    assertThat(second!!.busyMillis).isGreaterThanOrEqualTo(first.busyMillis)
    assertThat(second.idleMillis).isGreaterThanOrEqualTo(first.idleMillis)
    assertThat(second.busyMillis + second.idleMillis).isGreaterThan(first.busyMillis + first.idleMillis)
  }
}

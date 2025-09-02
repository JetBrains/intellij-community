// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.os

import com.intellij.ReviseWhenPortedToJDK
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class SystemRuntimeCollectorTest : BareTestFixtureTestCase() {
  @Test
  fun smoke() {
    val metrics = SystemRuntimeCollector().getMetrics().map { it.eventId to it.data.build() }
    fun metric(id: String) = metrics.find { it.first == id }?.second ?: throw AssertionError("No '${id}' in ${metrics.map { it.first }}")

    val cores = metric("cores")
    assertThat(cores["value"]).isIn(1, 2, 4, 6, 8, 12, 16, 20, 24, 32, 64)

    val ram = metric("memory.size")
    assertThat(ram["gigabytes"]).isIn(1, 2, 4, 8, 12, 16, 24, 32, 48, 64, 128, 256)

    val gc = metric("garbage.collector")
    assertThat(gc["name"]).isNotNull.isNotEqualTo("Other")

    val jvm = metric("jvm")
    assertThat(jvm["arch"]).isNotNull.isNotIn("other", "unknown")
    assertThat(jvm["vendor"]).isNotNull.isNotEqualTo("Other")

    val mem = metrics.asSequence().filter { it.first == "jvm.option" }.map { it.second["name"]!! to it.second["value"] }.toMap()
    mem["Xms"]?.let { assertThat(it).isIn(64L, 128L, 256L, 512L) }
    mem["Xmx"]?.let { assertThat(it).isIn(512L, 750L, 1000L, 1024L, 1500L, 2000L, 2048L, 3000L, 4000L, 4096L, 6000L, 8000L) }

    @ReviseWhenPortedToJDK("21")
    if (Runtime.version().feature() >= 21) {
      val osVm = metric("os.vm")
      assertThat(osVm["name"]).isNotNull.isNotIn("other", "unknown")
    }
  }
}

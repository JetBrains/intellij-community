// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.os

import com.intellij.util.system.GraphicsCardInfo
import com.intellij.util.system.MemoryModuleInfo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class HardwareMetricsTest {
  @Test
  fun `maps a GPU vendor to the closed set`() {
    assertThat(HardwareMetrics.gpuVendor("NVIDIA Corporation (0x10de)")).isEqualTo("nvidia")
    assertThat(HardwareMetrics.gpuVendor("Advanced Micro Devices, Inc. [AMD/ATI]")).isEqualTo("amd")
    assertThat(HardwareMetrics.gpuVendor("AMD (0x1002)")).isEqualTo("amd")
    assertThat(HardwareMetrics.gpuVendor("Intel Corporation")).isEqualTo("intel")
    assertThat(HardwareMetrics.gpuVendor("Apple (0x106b)")).isEqualTo("apple")
    assertThat(HardwareMetrics.gpuVendor("Matrox Electronics Systems Ltd.")).isEqualTo("other")
    assertThat(HardwareMetrics.gpuVendor("")).isEqualTo("other")
  }

  @Test
  fun `reads the GPU type from the vendor and the name`() {
    assertThat(gpuType("Apple M3 Pro", "Apple (0x106b)")).isEqualTo("integrated")
    assertThat(gpuType("NVIDIA GeForce RTX 4070", "NVIDIA")).isEqualTo("discrete")
    assertThat(gpuType("Intel(R) UHD Graphics 770", "Intel Corporation")).isEqualTo("integrated")
    assertThat(gpuType("Intel(R) Arc(TM) Graphics", "Intel Corporation")).isEqualTo("integrated")
    assertThat(gpuType("Intel(R) Arc(TM) 140V GPU", "Intel Corporation")).isEqualTo("integrated")
    assertThat(gpuType("Intel(R) Arc(TM) B390 GPU", "Intel Corporation")).isEqualTo("integrated")
    assertThat(gpuType("Intel(R) Arc(TM) Pro B60 Graphics", "Intel Corporation")).isEqualTo("discrete")
    assertThat(gpuType("Intel(R) Arc(TM) A770 Graphics", "Intel Corporation")).isEqualTo("discrete")
    assertThat(gpuType("Battlemage G21 [Arc B580]", "Intel Corporation (0x8086)")).isEqualTo("discrete")
    assertThat(gpuType("AMD Radeon RX 7800 XT", "Advanced Micro Devices, Inc.")).isEqualTo("discrete")
    assertThat(gpuType("AMD Radeon Pro 5500M", "AMD (0x1002)")).isEqualTo("discrete")
    assertThat(gpuType("AMD Radeon(TM) Graphics", "Advanced Micro Devices, Inc.")).isEqualTo("integrated")
    assertThat(gpuType("AMD Radeon(TM) 780M", "Advanced Micro Devices, Inc.")).isEqualTo("integrated")
    assertThat(gpuType("AMD Radeon RX Vega 11 Graphics", "Advanced Micro Devices, Inc.")).isEqualTo("integrated")
    assertThat(gpuType("Radeon RX Vega 64", "Advanced Micro Devices, Inc.")).isEqualTo("discrete")
    assertThat(gpuType("Vega 10 XL/XT [Radeon RX Vega 56/64]", "Advanced Micro Devices, Inc. [AMD/ATI] (0x1002)")).isEqualTo("discrete")
    assertThat(gpuType("Phoenix1", "Advanced Micro Devices, Inc. [AMD/ATI] (0x1002)")).isEqualTo("unknown")
    assertThat(gpuType("Apple Paravirtual device", "Apple (0x106b)")).isEqualTo("virtual")
    assertThat(gpuType("VirtualBox Graphics Adapter", "Oracle Corporation")).isEqualTo("virtual")
    assertThat(gpuType("Parsec Virtual Display Adapter", "Parsec Cloud, Inc.")).isEqualTo("virtual")
    assertThat(gpuType("SVGA II Adapter", "VMware (0x15ad)")).isEqualTo("virtual")
    assertThat(gpuType("Virtio 1.0 GPU", "Red Hat, Inc. (0x1af4)")).isEqualTo("virtual")
    assertThat(gpuType("Microsoft Hyper-V Video", "Microsoft")).isEqualTo("virtual")
    assertThat(gpuType("Microsoft Basic Display Adapter", "Microsoft")).isEqualTo("software")
    assertThat(gpuType("unknown", "unknown")).isEqualTo("unknown")
  }

  @Test
  fun `reads the memory type of the modules`() {
    assertThat(HardwareMetrics.memoryType(emptyList())).isEqualTo("unknown")
    assertThat(HardwareMetrics.memoryType(listOf(module(" DDR4 "), module("DDR4")))).isEqualTo("ddr4")
    assertThat(HardwareMetrics.memoryType(listOf(module("LPDDR5X")))).isEqualTo("lpddr5x")
    assertThat(HardwareMetrics.memoryType(listOf(module("DDR4"), module("DDR5")))).isEqualTo("mixed")
    assertThat(HardwareMetrics.memoryType(listOf(module("SDRAM"), module("unknown")))).isEqualTo("unknown")
    assertThat(HardwareMetrics.memoryType(listOf(module("mixed")))).isEqualTo("mixed")
  }

  @Test
  fun `reads the data rate of the fastest module`() {
    assertThat(HardwareMetrics.memorySpeedMts(emptyList())).isNull()
    assertThat(HardwareMetrics.memorySpeedMts(listOf(module("DDR4"), module("DDR4", 0)))).isNull()
    assertThat(HardwareMetrics.memorySpeedMts(listOf(module("DDR4", 2667), module("DDR4", 3200)))).isEqualTo(3200)
  }

  private fun gpuType(name: String, vendor: String): String =
    HardwareMetrics.gpuType(GraphicsCardInfo(name = name, vendor = vendor, deviceId = "unknown", vramBytes = 0))

  private fun module(type: String, speedMts: Long? = null): MemoryModuleInfo = MemoryModuleInfo(type = type, speedMts = speedMts)
}

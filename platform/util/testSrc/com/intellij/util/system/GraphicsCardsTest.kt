// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.system

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS.LINUX
import org.junit.jupiter.api.condition.OS.MAC
import org.junit.jupiter.api.condition.OS.WINDOWS

class GraphicsCardsTest {
  @Test
  fun `parses Apple silicon system profiler output`() {
    val cards = GraphicsCards.parseSystemProfiler(APPLE_SILICON.lines())
    assertThat(cards).containsExactly(GraphicsCardInfo(name = "Apple M5 Max", vendor = "Apple (0x106b)", deviceId = "unknown", vramBytes = 0))
  }

  @Test
  fun `parses Intel Mac system profiler output with two cards`() {
    val cards = GraphicsCards.parseSystemProfiler(INTEL_MAC.lines())
    assertThat(cards).containsExactly(
      GraphicsCardInfo(name = "Intel UHD Graphics 630", vendor = "Intel", deviceId = "0x3e9b", vramBytes = 1536L shl 20),
      GraphicsCardInfo(name = "AMD Radeon Pro 5500M", vendor = "AMD (0x1002)", deviceId = "0x7340", vramBytes = 8L shl 30),
    )
  }

  @Test
  fun `parses lspci output and sums the prefetchable memory`() {
    val cards = GraphicsCards.parseLspci(LSPCI.lines()) { slot ->
      assertThat(slot).isEqualTo("01:00.0")
      LSPCI_VERBOSE.lines()
    }
    assertThat(cards).containsExactly(
      GraphicsCardInfo(name = "GP104 [GeForce GTX 1080]", vendor = "NVIDIA Corporation (0x10de)", deviceId = "0x1b80", vramBytes = (256L shl 20) + (32L shl 20)),
    )
  }

  @Test
  fun `normalizes device ids`() {
    assertThat(GraphicsCards.normalizeDeviceId("0x3E9B")).isEqualTo("0x3e9b")
    assertThat(GraphicsCards.normalizeDeviceId(" 0x1b80 ")).isEqualTo("0x1b80")
    assertThat(GraphicsCards.normalizeDeviceId("3e9b")).isEqualTo("unknown")
    assertThat(GraphicsCards.normalizeDeviceId("")).isEqualTo("unknown")
  }

  @Test
  fun `parses memory sizes`() {
    assertThat(GraphicsCards.parseMemorySize("8 GB")).isEqualTo(8L shl 30)
    assertThat(GraphicsCards.parseMemorySize("1536 MB")).isEqualTo(1536L shl 20)
    assertThat(GraphicsCards.parseMemorySize("256M")).isEqualTo(256L shl 20)
    assertThat(GraphicsCards.parseMemorySize("32 KB")).isEqualTo(32L shl 10)
    assertThat(GraphicsCards.parseMemorySize("Dynamic")).isEqualTo(0)
  }

  @Test
  @EnabledOnOs(MAC, WINDOWS, LINUX)
  fun `lists at least one card on a desktop OS`() {
    // A CI container can have no PCI display adapter. Then the list is empty and the test proves only that the call does not fail.
    assertThat(GraphicsCards.list()).allSatisfy { card ->
      assertThat(card.name).isNotBlank()
      assertThat(card.deviceId).matches("0x[0-9a-f]{1,8}|unknown")
      assertThat(card.vramBytes).isGreaterThanOrEqualTo(0)
    }
  }
}

private val APPLE_SILICON = """
Graphics/Displays:

    Apple M5 Max:

      Chipset Model: Apple M5 Max
      Type: GPU
      Bus: Built-In
      Total Number of Cores: 40
      Vendor: Apple (0x106b)
      Metal Support: Metal 4
      Displays:
        Color LCD:
          Display Type: Built-in Liquid Retina XDR Display
          Resolution: 3456 x 2234 Retina
""".trimIndent()

private val INTEL_MAC = """
Graphics/Displays:

    Intel UHD Graphics 630:

      Chipset Model: Intel UHD Graphics 630
      Type: GPU
      Bus: Built-In
      VRAM (Dynamic, Max): 1536 MB
      Vendor: Intel
      Device ID: 0x3e9b
      Revision ID: 0x0002
      Metal Support: Metal 3

    AMD Radeon Pro 5500M:

      Chipset Model: AMD Radeon Pro 5500M
      Type: GPU
      Bus: PCIe
      PCIe Lane Width: x16
      VRAM (Total): 8 GB
      Vendor: AMD (0x1002)
      Device ID: 0x7340
      Revision ID: 0x0040
      ROM Revision: 113-D3220E-190
""".trimIndent()

private val LSPCI = """
Device:	00:00.0
Class:	Host bridge [0600]
Vendor:	Intel Corporation [8086]
Device:	8th Gen Core Processor Host Bridge/DRAM Registers [3e30]
Rev:	0d

Device:	01:00.0
Class:	VGA compatible controller [0300]
Vendor:	NVIDIA Corporation [10de]
Device:	GP104 [GeForce GTX 1080] [1b80]
SVendor:	ASUSTeK Computer Inc. [1043]
SDevice:	Device [8592]
Rev:	a1

Device:	01:00.1
Class:	Audio device [0403]
Vendor:	NVIDIA Corporation [10de]
Device:	GP104 High Definition Audio Controller [10f0]
Rev:	a1
""".trimIndent()

private val LSPCI_VERBOSE = """
01:00.0 VGA compatible controller: NVIDIA Corporation GP104 [GeForce GTX 1080] (rev a1) (prog-if 00 [VGA controller])
	Subsystem: ASUSTeK Computer Inc. Device 8592
	Flags: bus master, fast devsel, latency 0, IRQ 130
	Memory at de000000 (32-bit, non-prefetchable) [size=16M]
	Memory at c0000000 (64-bit, prefetchable) [size=256M]
	Memory at d0000000 (64-bit, prefetchable) [size=32M]
	I/O ports at e000 [size=128]
	Expansion ROM at df000000 [disabled] [size=512K]
""".trimIndent()

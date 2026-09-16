// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.system

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS.MAC
import org.junit.jupiter.api.condition.OS.WINDOWS

class MemoryModulesTest {
  @Test
  fun `parses Apple silicon system profiler output`() {
    val modules = MemoryModules.parseSystemProfiler(APPLE_SILICON.lines())
    assertThat(modules).containsExactly(MemoryModuleInfo(type = "LPDDR5", speedMts = null))
  }

  @Test
  fun `parses Intel Mac system profiler output and skips an empty slot`() {
    val modules = MemoryModules.parseSystemProfiler(INTEL_MAC.lines())
    assertThat(modules).containsExactly(
      MemoryModuleInfo(type = "DDR4", speedMts = 2667),
      MemoryModuleInfo(type = "DDR4", speedMts = 2667),
    )
  }

  @Test
  fun `maps SMBIOS memory type codes`() {
    assertThat(MemoryModules.smbiosMemoryType(0x1A)).isEqualTo("DDR4")
    assertThat(MemoryModules.smbiosMemoryType(0x22)).isEqualTo("DDR5")
    assertThat(MemoryModules.smbiosMemoryType(0x23)).isEqualTo("LPDDR5")
    assertThat(MemoryModules.smbiosMemoryType(0x02)).isEqualTo("unknown")
    assertThat(MemoryModules.smbiosMemoryType(0)).isEqualTo("unknown")
  }

  @Test
  @EnabledOnOs(MAC, WINDOWS)
  fun `lists the installed modules`() {
    // A virtual machine can report no module. Then the test proves only that the call does not fail.
    assertThat(MemoryModules.list()).allSatisfy { module ->
      assertThat(module.type).isNotBlank()
      module.speedMts?.let { assertThat(it).isGreaterThan(0) }
    }
  }
}

private val APPLE_SILICON = """
Memory:

      Memory: 128 GB
      Type: LPDDR5
      Manufacturer: Micron
""".trimIndent()

private val INTEL_MAC = """
Memory:

    Memory Slots:

      ECC: Disabled
      Upgradeable Memory: Yes

        BANK 0/ChannelA-DIMM0:

          Size: 16 GB
          Type: DDR4
          Speed: 2667 MHz
          Status: OK
          Manufacturer: Micron
          Part Number: 16ATF2G64HZ-2G6E1
          Serial Number: 12345678

        BANK 1/ChannelB-DIMM0:

          Size: Empty
          Type: Empty
          Speed: Empty
          Status: Empty

        BANK 2/ChannelA-DIMM1:

          Size: 16 GB
          Type: DDR4
          Speed: 2667 MHz
          Status: OK
""".trimIndent()

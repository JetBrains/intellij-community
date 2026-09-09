package com.intellij.ui.mac.touchbar

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.lang.foreign.Arena
import java.lang.foreign.ValueLayout.JAVA_INT_UNALIGNED

internal class NSTMemoryTest {
  @Test
  fun `scrubber rasters support unaligned pixels`() {
    Arena.ofConfined().use { arena ->
      val memory = arena.allocate(10)
      val raster = DirectDataBufferInt(memory, 8, 2)
      raster.setElem(0, 0, 0x12345678)
      raster.setElem(0, 1, 0xFEDCBA98.toInt())
      assertThat(raster.getElem(0, 0)).isEqualTo(0x12345678)
      assertThat(memory.get(JAVA_INT_UNALIGNED, 6)).isEqualTo(0xFEDCBA98.toInt())
    }
  }

  @Test
  fun `pixel access stays inside the allocated buffer`() {
    Arena.ofConfined().use { arena ->
      val raster = DirectDataBufferInt(arena.allocate(4), 4, 0)
      assertThatThrownBy { raster.setElem(0, 1, 7) }.isInstanceOf(IndexOutOfBoundsException::class.java)
    }
  }
}

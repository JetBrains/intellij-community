package com.intellij.openapi.wm.impl

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.awt.Rectangle
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout.ADDRESS
import java.lang.foreign.ValueLayout.JAVA_BYTE
import java.lang.foreign.ValueLayout.JAVA_INT
import java.lang.invoke.MethodHandles
import java.util.Optional

internal class WindowsWindowEffectsTest {
  @Test
  fun `alpha preserves the other style bits and uses an unsigned byte`() {
    Arena.ofConfined().use { arena ->
      val native = FakeWindows()
      val library = WindowsWindowEffects(native.symbols(arena))
      library.setAlpha(0x123456789L, 0.75f)
      assertThat(native.window).isEqualTo(0x123456789L)
      assertThat(native.styleIndex).isEqualTo(-20)
      assertThat(native.writtenStyle).isEqualTo(native.style or 0x80000)
      assertThat(native.alpha).isEqualTo(191)
      assertThat(native.colorKey).isZero()
      assertThat(native.alphaFlags).isEqualTo(2)
    }
  }

  @Test
  fun `opaque windows clear the layered flag`() {
    Arena.ofConfined().use { arena ->
      val native = FakeWindows()
      native.style = native.style or 0x80000
      WindowsWindowEffects(native.symbols(arena)).setAlpha(123, 1f)
      assertThat(native.writtenStyle).isEqualTo(native.style and 0x80000.inv())
      assertThat(native.alpha).isEqualTo(-1)
    }
  }

  @Test
  fun `fully transparent windows use zero alpha`() {
    Arena.ofConfined().use { arena ->
      val native = FakeWindows()
      WindowsWindowEffects(native.symbols(arena)).setAlpha(123, 0f)
      assertThat(native.alpha).isZero()
    }
  }

  @Test
  fun `failed alpha updates report the failure`() {
    Arena.ofConfined().use { arena ->
      val native = FakeWindows()
      native.alphaResult = 0
      val library = WindowsWindowEffects(native.symbols(arena))
      assertThatThrownBy { library.setAlpha(123, 0.5f) }.hasMessage("SetLayeredWindowAttributes failed")
    }
  }

  @Test
  fun `successful masks transfer the region to Windows`() {
    Arena.ofConfined().use { arena ->
      val native = FakeWindows()
      WindowsWindowEffects(native.symbols(arena)).setMask(0x123456789L, listOf(Rectangle(2, 3, 8, 1), Rectangle(2, 5, 6, 2)))
      assertThat(native.regionData).containsExactly(32, 1, 2, 32, 2, 3, 10, 7, 2, 3, 10, 4, 2, 5, 8, 7)
      assertThat(native.window).isEqualTo(0x123456789L)
      assertThat(native.region).isEqualTo(42L)
      assertThat(native.redraw).isEqualTo(1)
      assertThat(native.transform).isZero()
      assertThat(native.deleted).isEmpty()
    }
  }

  @Test
  fun `failed masks release the region`() {
    Arena.ofConfined().use { arena ->
      val native = FakeWindows()
      native.maskResult = 0
      val library = WindowsWindowEffects(native.symbols(arena))
      assertThatThrownBy { library.setMask(123, listOf(Rectangle(0, 0, 4, 4))) }.hasMessage("SetWindowRgn failed")
      assertThat(native.deleted).containsExactly(42L)
    }
  }

  @Test
  fun `failed region allocation does not change the window`() {
    Arena.ofConfined().use { arena ->
      val native = FakeWindows()
      native.regionResult = MemorySegment.NULL
      val library = WindowsWindowEffects(native.symbols(arena))
      assertThatThrownBy { library.setMask(123, emptyList()) }.hasMessage("ExtCreateRegion failed")
      assertThat(native.region).isEqualTo(-1L)
      assertThat(native.deleted).isEmpty()
    }
  }

  @Test
  fun `null masks reset the region without allocating or deleting it`() {
    Arena.ofConfined().use { arena ->
      val native = FakeWindows()
      WindowsWindowEffects(native.symbols(arena)).setMask(123, null)
      assertThat(native.region).isZero()
      assertThat(native.regionData).isEmpty()
      assertThat(native.deleted).isEmpty()
    }
  }

  @Suppress("UNUSED_PARAMETER")
  private class FakeWindows {
    var style = 0x40000121
    var writtenStyle = 0
    var styleIndex = 0
    var window = 0L
    var alpha = -1
    var colorKey = -1
    var alphaFlags = -1
    var alphaResult = 1
    var regionResult = MemorySegment.ofAddress(42)
    var maskResult = 1
    var regionData = intArrayOf()
    var region = -1L
    var redraw = 0
    var transform = -1L
    val deleted = ArrayList<Long>()

    fun symbols(arena: Arena): SymbolLookup {
      val symbols = HashMap<String, MemorySegment>()
      fun bind(symbol: String, method: String, descriptor: FunctionDescriptor) {
        val handle = MethodHandles.lookup().findVirtual(javaClass, method, descriptor.toMethodType()).bindTo(this)
        symbols[symbol] = Linker.nativeLinker().upcallStub(handle, descriptor, arena)
      }
      bind("GetWindowLongW", "getStyle", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT))
      bind("SetWindowLongW", "setStyle", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT))
      bind("SetLayeredWindowAttributes", "setAlpha", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_BYTE, JAVA_INT))
      bind("ExtCreateRegion", "createRegion", FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT, ADDRESS))
      bind("SetWindowRgn", "setMask", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT))
      bind("DeleteObject", "delete", FunctionDescriptor.of(JAVA_INT, ADDRESS))
      return SymbolLookup { Optional.ofNullable(symbols[it]) }
    }

    fun getStyle(window: MemorySegment, index: Int): Int {
      this.window = window.address()
      styleIndex = index
      return style
    }

    fun setStyle(window: MemorySegment, index: Int, value: Int): Int {
      this.window = window.address()
      styleIndex = index
      writtenStyle = value
      return style
    }

    fun setAlpha(window: MemorySegment, color: Int, alpha: Byte, flags: Int): Int {
      this.window = window.address()
      colorKey = color
      this.alpha = alpha.toInt() and 0xFF
      alphaFlags = flags
      return alphaResult
    }

    fun createRegion(transform: MemorySegment, size: Int, data: MemorySegment): MemorySegment {
      this.transform = transform.address()
      regionData = data.reinterpret(size.toLong()).toArray(JAVA_INT)
      return regionResult
    }

    fun setMask(window: MemorySegment, region: MemorySegment, redraw: Int): Int {
      this.window = window.address()
      this.region = region.address()
      this.redraw = redraw
      return maskResult
    }

    fun delete(region: MemorySegment): Int {
      deleted.add(region.address())
      return 1
    }
  }
}

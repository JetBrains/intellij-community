@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package com.intellij.openapi.wm.impl

import com.intellij.util.system.WindowsSystemLibraries
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import sun.awt.AWTAccessor
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.geom.Area
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout.ADDRESS
import java.lang.foreign.ValueLayout.JAVA_BYTE
import java.lang.foreign.ValueLayout.JAVA_INT
import javax.swing.JWindow
import javax.swing.SwingUtilities

@EnabledOnOs(OS.WINDOWS)
internal class WindowsWindowEffectsNativeTest {
  @Test
  fun `Windows retains the region after the call and removes it on reset`() {
    withWindow { window, symbols ->
      val effects = WindowsWindowEffects(symbols)
      val shape = Area(Rectangle(0, 0, 80, 80))
      shape.subtract(Area(Rectangle(20, 20, 20, 20)))
      effects.setMask(window.address(), WindowMask.rectangles(shape))
      val linker = Linker.nativeLinker()
      val createRegion = linker.downcallHandle(symbols.find("CreateRectRgn").orElseThrow(),
                                               FunctionDescriptor.of(ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT))
      val getRegion = linker.downcallHandle(symbols.find("GetWindowRgn").orElseThrow(), FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS))
      val contains = linker.downcallHandle(symbols.find("PtInRegion").orElseThrow(),
                                           FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT))
      val delete = linker.downcallHandle(symbols.find("DeleteObject").orElseThrow(), FunctionDescriptor.of(JAVA_INT, ADDRESS))
      val region = createRegion.invokeExact(0, 0, 0, 0) as MemorySegment
      assertThat(region).isNotEqualTo(MemorySegment.NULL)
      try {
        assertThat(getRegion.invokeExact(window, region) as Int).isEqualTo(3)
        assertThat(contains.invokeExact(region, 10, 10) as Int).isEqualTo(1)
        assertThat(contains.invokeExact(region, 30, 30) as Int).isZero()
        assertThat(contains.invokeExact(region, 90, 90) as Int).isZero()
        effects.setMask(window.address(), null)
        assertThat(getRegion.invokeExact(window, region) as Int).isZero()
      }
      finally {
        delete.invokeExact(region) as Int
      }
    }
  }

  @Test
  fun `Windows applies the alpha byte and clears the layered style on reset`() {
    withWindow { window, symbols ->
      val effects = WindowsWindowEffects(symbols)
      val linker = Linker.nativeLinker()
      val getStyle = linker.downcallHandle(symbols.find("GetWindowLongW").orElseThrow(),
                                           FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT))
      val getAlpha = linker.downcallHandle(symbols.find("GetLayeredWindowAttributes").orElseThrow(),
                                           FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS))
      val originalStyle = getStyle.invokeExact(window, -20) as Int
      Arena.ofConfined().use { arena ->
        val color = arena.allocate(JAVA_INT)
        val alpha = arena.allocate(JAVA_BYTE)
        val flags = arena.allocate(JAVA_INT)
        effects.setAlpha(window.address(), 0.75f)
        assertThat(getStyle.invokeExact(window, -20) as Int).isEqualTo(originalStyle or 0x80000)
        assertThat(getAlpha.invokeExact(window, color, alpha, flags) as Int).isEqualTo(1)
        assertThat(alpha.get(JAVA_BYTE, 0).toInt() and 0xFF).isEqualTo(191)
        assertThat(flags.get(JAVA_INT, 0)).isEqualTo(2)
        effects.setAlpha(window.address(), 1f)
        assertThat(getStyle.invokeExact(window, -20) as Int).isEqualTo(originalStyle and 0x80000.inv())
      }
    }
  }

  private fun withWindow(action: (MemorySegment, SymbolLookup) -> Unit) {
    assumeFalse(GraphicsEnvironment.isHeadless())
    SwingUtilities.invokeAndWait {
      val window = JWindow()
      try {
        window.setSize(100, 100)
        window.addNotify()
        val peer = checkNotNull(AWTAccessor.getComponentAccessor().getPeer(window))
        val handle = peer.javaClass.getMethod("getHWnd").invoke(peer) as Long
        assertThat(handle).isNotZero()
        action(MemorySegment.ofAddress(handle),
               WindowsSystemLibraries.lookup("user32.dll").or(WindowsSystemLibraries.lookup("gdi32.dll")))
      }
      finally {
        window.dispose()
      }
    }
  }
}

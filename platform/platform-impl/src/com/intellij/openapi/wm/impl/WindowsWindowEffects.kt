package com.intellij.openapi.wm.impl

import org.jetbrains.annotations.ApiStatus
import java.awt.Rectangle
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout.ADDRESS
import java.lang.foreign.ValueLayout.JAVA_BYTE
import java.lang.foreign.ValueLayout.JAVA_INT

@ApiStatus.Internal
class WindowsWindowEffects(lookup: SymbolLookup) {
  private val linker = Linker.nativeLinker()
  private val getWindowLong = linker.downcallHandle(lookup.find("GetWindowLongW").orElseThrow(),
                                                   FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT))
  private val setWindowLong = linker.downcallHandle(lookup.find("SetWindowLongW").orElseThrow(),
                                                   FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT))
  private val setLayeredWindowAttributes = linker.downcallHandle(lookup.find("SetLayeredWindowAttributes").orElseThrow(),
                                                                FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_BYTE, JAVA_INT))
  private val createRegion = linker.downcallHandle(lookup.find("ExtCreateRegion").orElseThrow(),
                                                  FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT, ADDRESS))
  private val setWindowRegion = linker.downcallHandle(lookup.find("SetWindowRgn").orElseThrow(),
                                                     FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT))
  private val deleteObject = linker.downcallHandle(lookup.find("DeleteObject").orElseThrow(), FunctionDescriptor.of(JAVA_INT, ADDRESS))

  fun setAlpha(window: Long, alpha: Float) {
    require(window != 0L)
    require(alpha in 0f..1f)
    val handle = MemorySegment.ofAddress(window)
    val style = getWindowLong.invokeExact(handle, -20) as Int
    val newStyle = if (alpha == 1f) style and 0x80000.inv() else style or 0x80000
    setWindowLong.invokeExact(handle, -20, newStyle) as Int
    if (alpha != 1f) {
      check(setLayeredWindowAttributes.invokeExact(handle, 0, (255 * alpha).toInt().toByte(), 2) as Int != 0) {
        "SetLayeredWindowAttributes failed"
      }
    }
  }

  fun setMask(window: Long, rectangles: List<Rectangle>?) {
    require(window != 0L)
    Arena.ofConfined().use { arena ->
      val region = if (rectangles == null) MemorySegment.NULL else {
        val size = Math.addExact(32, Math.multiplyExact(rectangles.size, 16))
        val data = arena.allocate(size.toLong(), 4)
        data.set(JAVA_INT, 0, 32)
        data.set(JAVA_INT, 4, 1)
        data.set(JAVA_INT, 8, rectangles.size)
        data.set(JAVA_INT, 12, size - 32)
        val bounds = rectangles.fold(Rectangle()) { bounds, rectangle ->
          if (bounds.isEmpty) Rectangle(rectangle) else bounds.union(rectangle)
        }
        writeRectangle(data, 16, bounds)
        rectangles.forEachIndexed { index, rectangle -> writeRectangle(data, 32L + index * 16L, rectangle) }
        (createRegion.invokeExact(MemorySegment.NULL, size, data) as MemorySegment).also {
          check(it != MemorySegment.NULL) { "ExtCreateRegion failed" }
        }
      }
      var transferred = false
      try {
        check(setWindowRegion.invokeExact(MemorySegment.ofAddress(window), region, 1) as Int != 0) { "SetWindowRgn failed" }
        transferred = true
      }
      finally {
        if (!transferred && region != MemorySegment.NULL) {
          deleteObject.invokeExact(region) as Int
        }
      }
    }
  }

  private fun writeRectangle(data: MemorySegment, offset: Long, rectangle: Rectangle) {
    data.set(JAVA_INT, offset, rectangle.x)
    data.set(JAVA_INT, offset + 4, rectangle.y)
    data.set(JAVA_INT, offset + 8, Math.addExact(rectangle.x, rectangle.width))
    data.set(JAVA_INT, offset + 12, Math.addExact(rectangle.y, rectangle.height))
  }
}

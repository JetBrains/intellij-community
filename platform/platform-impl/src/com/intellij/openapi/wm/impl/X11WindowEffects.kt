package com.intellij.openapi.wm.impl

import org.jetbrains.annotations.ApiStatus
import java.awt.Rectangle
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout.ADDRESS
import java.lang.foreign.ValueLayout.JAVA_INT
import java.lang.foreign.ValueLayout.JAVA_LONG
import java.lang.foreign.ValueLayout.JAVA_SHORT

@ApiStatus.Internal
class X11WindowEffects(private val lookup: SymbolLookup) {
  private fun function(name: String, descriptor: FunctionDescriptor) =
    Linker.nativeLinker().downcallHandle(lookup.find(name).orElseThrow(), descriptor)

  private val openDisplay = function("XOpenDisplay", FunctionDescriptor.of(ADDRESS, ADDRESS))
  private val closeDisplay = function("XCloseDisplay", FunctionDescriptor.of(JAVA_INT, ADDRESS))
  private val defaultScreen by lazy { function("XDefaultScreen", FunctionDescriptor.of(JAVA_INT, ADDRESS)) }
  private val getVisualInfo by lazy { function("XGetVisualInfo", FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_LONG, ADDRESS, ADDRESS)) }
  private val findVisualFormat by lazy { function("XRenderFindVisualFormat", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS)) }
  private val free by lazy { function("XFree", FunctionDescriptor.of(JAVA_INT, ADDRESS)) }
  private val internAtom by lazy { function("XInternAtom", FunctionDescriptor.of(JAVA_LONG, ADDRESS, ADDRESS, JAVA_INT)) }
  private val deleteProperty by lazy { function("XDeleteProperty", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG, JAVA_LONG)) }
  private val changeProperty by lazy {
    function("XChangeProperty", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG, JAVA_LONG, JAVA_LONG,
                                                     JAVA_INT, JAVA_INT, ADDRESS, JAVA_INT))
  }
  private val combineRectangles by lazy {
    function("XShapeCombineRectangles", FunctionDescriptor.ofVoid(ADDRESS, JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_INT,
                                                                ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT))
  }
  private val combineMask by lazy {
    function("XShapeCombineMask", FunctionDescriptor.ofVoid(ADDRESS, JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_LONG, JAVA_INT))
  }

  val isAlphaSupported: Boolean by lazy {
    withDisplay(false) { display ->
      Arena.ofConfined().use { arena ->
        val template = arena.allocate(64, 8)
        template.set(JAVA_INT, 16, defaultScreen.invokeExact(display) as Int)
        template.set(JAVA_INT, 20, 32)
        template.set(JAVA_INT, 24, 4)
        val count = arena.allocate(JAVA_INT)
        val visuals = getVisualInfo.invokeExact(display, 0xEL, template, count) as MemorySegment
        if (visuals == MemorySegment.NULL) return@use false
        try {
          val size = count.get(JAVA_INT, 0)
          val data = visuals.reinterpret(size * 64L)
          (0 until size).any { index ->
            val format = findVisualFormat.invokeExact(display, data.get(ADDRESS, index * 64L)) as MemorySegment
            if (format == MemorySegment.NULL) false else {
              val formatData = format.reinterpret(40)
              formatData.get(JAVA_INT, 8) == 1 && formatData.get(JAVA_SHORT, 30) != 0.toShort()
            }
          }
        }
        finally {
          free.invokeExact(visuals) as Int
        }
      }
    }
  }

  fun setAlpha(window: Long, alpha: Float) {
    require(window != 0L)
    require(alpha in 0f..1f)
    withDisplay(Unit) { display ->
      Arena.ofConfined().use<Arena, Unit> { arena ->
        val atom = internAtom.invokeExact(display, arena.allocateFrom("_NET_WM_WINDOW_OPACITY"), 0) as Long
        if (alpha == 1f) {
          deleteProperty.invokeExact(display, window, atom) as Int
        }
        else {
          val value = arena.allocate(JAVA_LONG)
          value.set(JAVA_LONG, 0, (alpha.toDouble() * 0xFFFFFFFFL).toLong())
          changeProperty.invokeExact(display, window, atom, 6L, 32, 0, value, 1) as Int
        }
      }
    }
  }

  fun setMask(window: Long, rectangles: List<Rectangle>?) {
    require(window != 0L)
    rectangles?.forEach {
      require(it.x in 0..Short.MAX_VALUE && it.y in 0..Short.MAX_VALUE && it.width in 0..65535 && it.height in 0..65535) {
        "The window mask exceeds the X11 coordinate range"
      }
    }
    withDisplay(Unit) { display ->
      Arena.ofConfined().use<Arena, Unit> { arena ->
        if (rectangles == null) {
          combineMask.invokeExact(display, window, 0, 0, 0, 0L, 0)
        }
        else {
          val data = if (rectangles.isEmpty()) MemorySegment.NULL else arena.allocate(rectangles.size * 8L, 2)
          rectangles.forEachIndexed { index, rectangle ->
            val offset = index * 8L
            data.set(JAVA_SHORT, offset, rectangle.x.toShort())
            data.set(JAVA_SHORT, offset + 2, rectangle.y.toShort())
            data.set(JAVA_SHORT, offset + 4, rectangle.width.toShort())
            data.set(JAVA_SHORT, offset + 6, rectangle.height.toShort())
          }
          combineRectangles.invokeExact(display, window, 0, 0, 0, data, rectangles.size, 0, 0)
        }
      }
    }
  }

  private fun <T> withDisplay(fallback: T, action: (MemorySegment) -> T): T {
    val display = openDisplay.invokeExact(MemorySegment.NULL) as MemorySegment
    if (display == MemorySegment.NULL) return fallback
    try {
      return action(display)
    }
    finally {
      closeDisplay.invokeExact(display) as Int
    }
  }
}

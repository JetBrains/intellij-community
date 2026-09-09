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
import java.lang.foreign.ValueLayout.JAVA_INT
import java.lang.foreign.ValueLayout.JAVA_LONG
import java.lang.foreign.ValueLayout.JAVA_SHORT
import java.lang.invoke.MethodHandles
import java.util.Optional

internal class X11WindowEffectsTest {
  @Test
  fun `opacity uses a native long for the format32 value`() {
    Arena.ofConfined().use { arena ->
      val native = FakeX11(arena)
      X11WindowEffects(native.symbols()).setAlpha(0x123456789L, 0.75f)
      assertThat(native.atomName).isEqualTo("_NET_WM_WINDOW_OPACITY")
      assertThat(native.window).isEqualTo(0x123456789L)
      assertThat(native.property).isEqualTo(23L)
      assertThat(native.propertyType).isEqualTo(6L)
      assertThat(native.format).isEqualTo(32)
      assertThat(native.mode).isZero()
      assertThat(native.count).isEqualTo(1)
      assertThat(native.opacity).isEqualTo(0xBFFFFFFFL)
      assertThat(native.closed).isEqualTo(1)
    }
  }

  @Test
  fun `opaque windows delete the opacity property`() {
    Arena.ofConfined().use { arena ->
      val native = FakeX11(arena)
      X11WindowEffects(native.symbols()).setAlpha(123, 1f)
      assertThat(native.deletedProperty).isTrue()
      assertThat(native.property).isEqualTo(23L)
      assertThat(native.opacity).isEqualTo(-1L)
      assertThat(native.closed).isEqualTo(1)
    }
  }

  @Test
  fun `missing displays disable alpha without further native calls`() {
    Arena.ofConfined().use { arena ->
      val native = FakeX11(arena)
      native.display = MemorySegment.NULL
      val library = X11WindowEffects(native.symbols())
      assertThat(library.isAlphaSupported).isFalse()
      library.setAlpha(123, 0.5f)
      library.setMask(123, null)
      assertThat(native.closed).isZero()
      assertThat(native.atomName).isEmpty()
    }
  }

  @Test
  fun `alpha support reads the native visual layouts and releases resources`() {
    Arena.ofConfined().use { arena ->
      val native = FakeX11(arena)
      native.visualCount = 2
      val library = X11WindowEffects(native.symbols())
      assertThat(library.isAlphaSupported).isTrue()
      assertThat(native.template).containsExactly(5, 32, 4)
      assertThat(native.visualMask).isEqualTo(0xEL)
      assertThat(native.visitedVisuals).containsExactly(101L, 102L)
      assertThat(native.freed).containsExactly(native.visuals.address())
      assertThat(native.closed).isEqualTo(1)
      assertThat(library.isAlphaSupported).isTrue()
      assertThat(native.opened).isEqualTo(1)
    }
  }

  @Test
  fun `visuals without direct alpha disable support`() {
    Arena.ofConfined().use { arena ->
      val native = FakeX11(arena)
      native.visualCount = 1
      assertThat(X11WindowEffects(native.symbols()).isAlphaSupported).isFalse()
      assertThat(native.freed).containsExactly(native.visuals.address())
      assertThat(native.closed).isEqualTo(1)
    }
  }

  @Test
  fun `null visual arrays are not freed`() {
    Arena.ofConfined().use { arena ->
      val native = FakeX11(arena)
      native.visualResult = MemorySegment.NULL
      assertThat(X11WindowEffects(native.symbols()).isAlphaSupported).isFalse()
      assertThat(native.freed).isEmpty()
      assertThat(native.closed).isEqualTo(1)
    }
  }

  @Test
  fun `failed render lookup still frees the visuals and display`() {
    Arena.ofConfined().use { arena ->
      val native = FakeX11(arena)
      val symbols = native.symbols()
      val library = X11WindowEffects(SymbolLookup { if (it == "XRenderFindVisualFormat") Optional.empty() else symbols.find(it) })
      assertThatThrownBy { library.isAlphaSupported }.isInstanceOf(NoSuchElementException::class.java)
      assertThat(native.freed).containsExactly(native.visuals.address())
      assertThat(native.closed).isEqualTo(1)
    }
  }

  @Test
  fun `masks use packed XRectangles with unsigned sizes`() {
    Arena.ofConfined().use { arena ->
      val native = FakeX11(arena)
      X11WindowEffects(native.symbols()).setMask(123, listOf(Rectangle(2, 3, 40000, 7), Rectangle(4, 12, 5, 9)))
      assertThat(native.rectangles.map { it.toInt() and 0xFFFF }).containsExactly(2, 3, 40000, 7, 4, 12, 5, 9)
      assertThat(native.shapeArguments).containsExactly(0, 0, 0, 0, 0)
      assertThat(native.count).isEqualTo(2)
      assertThat(native.window).isEqualTo(123L)
      assertThat(native.closed).isEqualTo(1)
    }
  }

  @Test
  fun `null masks reset the bounding shape`() {
    Arena.ofConfined().use { arena ->
      val native = FakeX11(arena)
      X11WindowEffects(native.symbols()).setMask(123, null)
      assertThat(native.pixmap).isZero()
      assertThat(native.shapeArguments).containsExactly(0, 0, 0, 0)
      assertThat(native.closed).isEqualTo(1)
    }
  }

  @Test
  fun `empty masks send zero rectangles`() {
    Arena.ofConfined().use { arena ->
      val native = FakeX11(arena)
      X11WindowEffects(native.symbols()).setMask(123, emptyList())
      assertThat(native.rectangles).isEmpty()
      assertThat(native.count).isZero()
      assertThat(native.closed).isEqualTo(1)
    }
  }

  @Test
  fun `oversized coordinates fail before opening a display`() {
    Arena.ofConfined().use { arena ->
      val native = FakeX11(arena)
      val library = X11WindowEffects(native.symbols())
      assertThatThrownBy { library.setMask(123, listOf(Rectangle(32768, 0, 1, 1))) }
        .isInstanceOf(IllegalArgumentException::class.java)
      assertThat(native.opened).isZero()
    }
  }

  @Suppress("UNUSED_PARAMETER")
  private class FakeX11(private val arena: Arena) {
    var display = MemorySegment.ofAddress(17)
    var opened = 0
    var closed = 0
    var window = 0L
    var property = 0L
    var propertyType = 0L
    var atomName = ""
    var opacity = -1L
    var format = 0
    var mode = -1
    var count = -1
    var deletedProperty = false
    val visuals = arena.allocate(128, 8)
    var visualResult = visuals
    var visualCount = 2
    var visualMask = 0L
    var template = intArrayOf()
    val visitedVisuals = ArrayList<Long>()
    val freed = ArrayList<Long>()
    private val visualFormat = arena.allocate(40, 8)
    var rectangles = shortArrayOf()
    var shapeArguments = intArrayOf()
    var pixmap = -1L

    init {
      visuals.set(ADDRESS, 0, MemorySegment.ofAddress(101))
      visuals.set(ADDRESS, 64, MemorySegment.ofAddress(102))
    }

    fun symbols(): SymbolLookup {
      val symbols = HashMap<String, MemorySegment>()
      fun bind(symbol: String, method: String, descriptor: FunctionDescriptor) {
        val handle = MethodHandles.lookup().findVirtual(javaClass, method, descriptor.toMethodType()).bindTo(this)
        symbols[symbol] = Linker.nativeLinker().upcallStub(handle, descriptor, arena)
      }
      bind("XOpenDisplay", "open", FunctionDescriptor.of(ADDRESS, ADDRESS))
      bind("XCloseDisplay", "close", FunctionDescriptor.of(JAVA_INT, ADDRESS))
      bind("XDefaultScreen", "screen", FunctionDescriptor.of(JAVA_INT, ADDRESS))
      bind("XGetVisualInfo", "visualInfo", FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_LONG, ADDRESS, ADDRESS))
      bind("XRenderFindVisualFormat", "visualFormat", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS))
      bind("XFree", "free", FunctionDescriptor.of(JAVA_INT, ADDRESS))
      bind("XInternAtom", "atom", FunctionDescriptor.of(JAVA_LONG, ADDRESS, ADDRESS, JAVA_INT))
      bind("XDeleteProperty", "delete", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG, JAVA_LONG))
      bind("XChangeProperty", "change", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG, JAVA_LONG, JAVA_LONG,
                                                            JAVA_INT, JAVA_INT, ADDRESS, JAVA_INT))
      bind("XShapeCombineRectangles", "mask", FunctionDescriptor.ofVoid(ADDRESS, JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_INT,
                                                                      ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT))
      bind("XShapeCombineMask", "resetMask",
           FunctionDescriptor.ofVoid(ADDRESS, JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_LONG, JAVA_INT))
      return SymbolLookup { Optional.ofNullable(symbols[it]) }
    }

    fun open(name: MemorySegment): MemorySegment {
      opened++
      return display
    }

    fun close(display: MemorySegment): Int {
      closed++
      return 0
    }

    fun screen(display: MemorySegment): Int = 5

    fun visualInfo(display: MemorySegment, mask: Long, template: MemorySegment, count: MemorySegment): MemorySegment {
      visualMask = mask
      this.template = template.reinterpret(64).asSlice(16, 12).toArray(JAVA_INT)
      count.reinterpret(4).set(JAVA_INT, 0, visualCount)
      return visualResult
    }

    fun visualFormat(display: MemorySegment, visual: MemorySegment): MemorySegment {
      visitedVisuals.add(visual.address())
      visualFormat.set(JAVA_INT, 8, 1)
      visualFormat.set(JAVA_SHORT, 30, if (visual.address() == 102L) 0xFF else 0)
      return visualFormat
    }

    fun free(data: MemorySegment): Int {
      freed.add(data.address())
      return 0
    }

    fun atom(display: MemorySegment, name: MemorySegment, onlyIfExists: Int): Long {
      atomName = name.reinterpret(128).getString(0)
      return 23
    }

    fun delete(display: MemorySegment, window: Long, property: Long): Int {
      this.window = window
      this.property = property
      deletedProperty = true
      return 0
    }

    fun change(display: MemorySegment, window: Long, property: Long, type: Long,
               format: Int, mode: Int, data: MemorySegment, count: Int): Int {
      this.window = window
      this.property = property
      propertyType = type
      this.format = format
      this.mode = mode
      this.count = count
      opacity = data.reinterpret(8).get(JAVA_LONG, 0)
      return 0
    }

    fun mask(display: MemorySegment, window: Long, kind: Int, xOffset: Int, yOffset: Int,
             data: MemorySegment, count: Int, operation: Int, ordering: Int) {
      this.window = window
      this.count = count
      rectangles = if (count == 0) shortArrayOf() else data.reinterpret(count * 8L).toArray(JAVA_SHORT)
      shapeArguments = intArrayOf(kind, xOffset, yOffset, operation, ordering)
    }

    fun resetMask(display: MemorySegment, window: Long, kind: Int, xOffset: Int, yOffset: Int, pixmap: Long, operation: Int) {
      this.window = window
      this.pixmap = pixmap
      shapeArguments = intArrayOf(kind, xOffset, yOffset, operation)
    }
  }
}

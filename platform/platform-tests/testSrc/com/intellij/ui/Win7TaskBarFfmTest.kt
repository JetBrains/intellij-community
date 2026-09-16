package com.intellij.ui

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy

import org.junit.jupiter.api.Test
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout.ADDRESS
import java.lang.foreign.ValueLayout.JAVA_INT
import java.lang.foreign.ValueLayout.JAVA_LONG
import java.lang.invoke.MethodHandles

internal class Win7TaskBarFfmTest {
  @Test
  fun `vtable calls preserve handles and integer widths`() {
    Arena.ofConfined().use { arena ->
      val native = FakeTaskbar()
      val pointer = native.allocate(arena)
      val taskbar = Win7TaskBar.TaskbarInterface(pointer)
      taskbar.init()
      assertThat(native.receiver).isEqualTo(pointer.address())
      val window = MemorySegment.ofAddress(0x123456789L)
      taskbar.setProgressState(window, 4)
      assertThat(native.state).isEqualTo(4)
      taskbar.setProgressValue(window, Long.MIN_VALUE, -1)
      assertThat(native.progress).isEqualTo(listOf(Long.MIN_VALUE, -1L))
      taskbar.setOverlayIcon(window, MemorySegment.ofAddress(0x987654321L))
      assertThat(native.window).isEqualTo(window.address())
      assertThat(native.icon).isEqualTo(0x987654321L)
      assertThat(native.description).isEqualTo(0L)
    }
  }

  @Test
  fun `failed initialization reports the HRESULT`() {
    Arena.ofConfined().use { arena ->
      val native = FakeTaskbar()
      native.initResult = 0x80004005.toInt()
      val taskbar = Win7TaskBar.TaskbarInterface(native.allocate(arena))
      assertThatThrownBy { taskbar.init() }
        .isInstanceOf(IllegalStateException::class.java)
        .hasMessage("ITaskbarList.HrInit failed: 0x80004005")
    }
  }

  private class FakeTaskbar {
    var receiver = 0L
    var window = 0L
    var state = 0
    var progress = emptyList<Long>()
    var icon = 0L
    var description = -1L
    var initResult = 0

    fun allocate(arena: Arena): MemorySegment {
      val table = arena.allocate(ADDRESS, 21)
      fun method(slot: Long, name: String, descriptor: FunctionDescriptor) {
        val handle = MethodHandles.lookup().findVirtual(javaClass, name, descriptor.toMethodType()).bindTo(this)
        table.setAtIndex(ADDRESS, slot, Linker.nativeLinker().upcallStub(handle, descriptor, arena))
      }
      method(3, "init", FunctionDescriptor.of(JAVA_INT, ADDRESS))
      method(9, "progress", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_LONG, JAVA_LONG))
      method(10, "state", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT))
      method(18, "overlay", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS))
      return arena.allocateFrom(ADDRESS, table)
    }

    fun init(pointer: MemorySegment): Int {
      receiver = pointer.address()
      return initResult
    }

    fun progress(pointer: MemorySegment, handle: MemorySegment, completed: Long, total: Long): Int {
      receiver = pointer.address()
      window = handle.address()
      progress = listOf(completed, total)
      return 0
    }

    fun state(pointer: MemorySegment, handle: MemorySegment, value: Int): Int {
      receiver = pointer.address()
      window = handle.address()
      state = value
      return 0
    }

    fun overlay(pointer: MemorySegment, handle: MemorySegment, image: MemorySegment, text: MemorySegment): Int {
      receiver = pointer.address()
      window = handle.address()
      icon = image.address()
      description = text.address()
      return 0
    }
  }
}

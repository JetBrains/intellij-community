package com.intellij.ui

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy

import org.junit.jupiter.api.Test
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout.ADDRESS
import java.lang.foreign.ValueLayout.JAVA_INT
import java.lang.invoke.MethodHandles
import java.util.Optional

internal class LibNotifyFfmTest {
  @Test
  fun `notification copies UTF8 strings and releases the object`() {
    Arena.ofConfined().use { arena ->
      val native = FakeNotify()
      val library = LibNotifyWrapper.LibNotify(native.symbols(arena))
      library.init("IntelliJ")
      assertThat(native.appName).isEqualTo("IntelliJ")
      assertThat(library.notify("Résumé", "完成", "icon")).isTrue()
      assertThat(native.strings).isEqualTo(listOf("Résumé", "完成", "icon"))
      assertThat(native.released).isEqualTo(listOf(123L))
      assertThat(native.errorPointer).isEqualTo(0L)
      library.uninit()
      assertThat(native.uninitialized).isTrue()
    }
  }

  @Test
  fun `failed show still releases the object`() {
    Arena.ofConfined().use { arena ->
      val native = FakeNotify()
      native.showResult = 0
      assertThat(LibNotifyWrapper.LibNotify(native.symbols(arena)).notify("title", "body", "icon")).isFalse()
      assertThat(native.released).isEqualTo(listOf(123L))
    }
  }

  @Test
  fun `null notification is not shown or released`() {
    Arena.ofConfined().use { arena ->
      val native = FakeNotify()
      native.notification = MemorySegment.NULL
      assertThat(LibNotifyWrapper.LibNotify(native.symbols(arena)).notify("title", "body", "icon")).isFalse()
      assertThat(native.showCalls).isEqualTo(0)
      assertThat(native.released.isEmpty()).isTrue()
    }
  }

  @Test
  fun `failed initialization disables notifications`() {
    Arena.ofConfined().use { arena ->
      val native = FakeNotify()
      native.initResult = 0
      val library = LibNotifyWrapper.LibNotify(native.symbols(arena))
      assertThatThrownBy { library.init("IntelliJ") }.isInstanceOf(IllegalStateException::class.java)
    }
  }

  private class FakeNotify {
    var appName = ""
    var strings = emptyList<String>()
    val released = ArrayList<Long>()
    var notification: MemorySegment = MemorySegment.ofAddress(123)
    var errorPointer = -1L
    var showCalls = 0
    var showResult = 1
    var initResult = 1
    var uninitialized = false

    fun symbols(arena: Arena): SymbolLookup {
      val symbols = HashMap<String, MemorySegment>()
      fun bind(symbol: String, name: String, descriptor: FunctionDescriptor) {
        val handle = MethodHandles.lookup().findVirtual(javaClass, name, descriptor.toMethodType()).bindTo(this)
        symbols[symbol] = Linker.nativeLinker().upcallStub(handle, descriptor, arena)
      }
      bind("notify_init", "init", FunctionDescriptor.of(JAVA_INT, ADDRESS))
      bind("notify_uninit", "uninit", FunctionDescriptor.ofVoid())
      bind("notify_notification_new", "create", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS))
      bind("notify_notification_show", "show", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS))
      bind("g_object_unref", "unref", FunctionDescriptor.ofVoid(ADDRESS))
      return SymbolLookup { Optional.ofNullable(symbols[it]) }
    }

    fun init(name: MemorySegment): Int {
      appName = name.reinterpret(Long.MAX_VALUE).getString(0)
      return initResult
    }

    fun uninit() {
      uninitialized = true
    }

    fun create(title: MemorySegment, body: MemorySegment, icon: MemorySegment): MemorySegment {
      strings = listOf(title, body, icon).map { it.reinterpret(Long.MAX_VALUE).getString(0) }
      return notification
    }

    fun show(notification: MemorySegment, error: MemorySegment): Int {
      showCalls++
      errorPointer = error.address()
      return if (notification.address() == this.notification.address()) showResult else 0
    }

    fun unref(notification: MemorySegment) {
      released.add(notification.address())
    }
  }
}

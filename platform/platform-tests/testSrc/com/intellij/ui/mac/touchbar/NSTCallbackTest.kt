package com.intellij.ui.mac.touchbar

import org.assertj.core.api.Assertions.assertThat

import org.junit.jupiter.api.Test
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout.ADDRESS
import java.lang.foreign.ValueLayout.JAVA_INT
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal class NSTCallbackTest {
  @Test
  fun `release does not invalidate an active native callback`() {
    val entered = CountDownLatch(1)
    val resume = CountDownLatch(1)
    var completed = false
    val callback = NSTCallback.action {
      entered.countDown()
      completed = resume.await(10, TimeUnit.SECONDS)
    }
    val call = Linker.nativeLinker().downcallHandle(callback.stub, FunctionDescriptor.ofVoid())
    val invocation = CompletableFuture.runAsync { call.invokeExact() }
    try {
      assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue()
      callback.clear()
      assertThat(callback.target).isNull()
    }
    finally {
      callback.clear()
      resume.countDown()
      invocation.get(10, TimeUnit.SECONDS)
    }
    assertThat(completed).isTrue()
    call.invokeExact()
  }

  @Test
  fun `released callbacks remain callable without retaining their targets`() {
    var calls = 0
    val callback = NSTCallback.action { calls++ }
    val call = Linker.nativeLinker().downcallHandle(callback.stub, FunctionDescriptor.ofVoid())
    call.invokeExact()
    callback.target = NSTLibrary.Action { calls += 10 }
    call.invokeExact()
    callback.clear()
    call.invokeExact()
    assertThat(calls).isEqualTo(11)
    assertThat(callback.target).isNull()
  }

  @Test
  fun `creator decodes UTF8 and returns a native handle`() {
    var identifier = ""
    val callback = NSTCallback.creator { uid ->
      identifier = uid
      MemorySegment.ofAddress(0x123456789L)
    }
    val call = Linker.nativeLinker().downcallHandle(callback.stub, FunctionDescriptor.of(ADDRESS, ADDRESS))
    try {
      Arena.ofConfined().use { arena ->
        val result = call.invokeExact(arena.allocateFrom("Résumé")) as MemorySegment
        assertThat(result.address()).isEqualTo(0x123456789L)
        assertThat(identifier).isEqualTo("Résumé")
        callback.clear()
        val released = call.invokeExact(arena.allocateFrom("ignored")) as MemorySegment
        assertThat(released.address()).isEqualTo(0L)
      }
    }
    finally {
      callback.clear()
    }
  }

  @Test
  fun `scrubber callbacks use signed 32 bit values`() {
    var selected = 0
    val delegate = NSTCallback.delegate { selected = it }
    val updater = NSTCallback.updater { -1 }
    try {
      val select = Linker.nativeLinker().downcallHandle(delegate.stub, FunctionDescriptor.ofVoid(JAVA_INT))
      select.invokeExact(Int.MIN_VALUE)
      assertThat(selected).isEqualTo(Int.MIN_VALUE)
      val update = Linker.nativeLinker().downcallHandle(updater.stub, FunctionDescriptor.of(JAVA_INT))
      assertThat(update.invokeExact() as Int).isEqualTo(-1)
      updater.clear()
      assertThat(update.invokeExact() as Int).isEqualTo(0)
    }
    finally {
      delegate.clear()
      updater.clear()
    }
  }

  @Test
  fun `callback failure returns a safe native default`() {
    val callback = NSTCallback.updater { throw IllegalStateException("test callback") }
    try {
      val update = Linker.nativeLinker().downcallHandle(callback.stub, FunctionDescriptor.of(JAVA_INT))
      assertThat(update.invokeExact() as Int).isEqualTo(0)
    }
    finally {
      callback.clear()
    }
  }
}

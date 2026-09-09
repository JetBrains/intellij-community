@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package com.intellij.util

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.mac.foundation.Foundation
import com.intellij.ui.mac.foundation.ID
import com.intellij.ui.mac.foundation.ObjcTypeEncoding
import com.intellij.ui.mac.foundation.Selector
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLong
import com.sun.jna.Pointer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import java.lang.foreign.Arena
import java.lang.foreign.ValueLayout.ADDRESS
import java.lang.foreign.ValueLayout.JAVA_DOUBLE
import java.lang.foreign.ValueLayout.JAVA_INT
import java.lang.foreign.ValueLayout.JAVA_LONG
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@TestApplication
@Timeout(30)
internal class FoundationFfmTest {
  @Test
  @DisabledOnOs(OS.MAC)
  fun `other systems load the facade without native libraries`() {
    assertThat(Foundation.isAvailable()).isFalse()
    assertThat(ID(42).toLong()).isEqualTo(42)
  }

  @Test
  fun `handles have no native superclass`() {
    assertThat(ID::class.java.superclass).isEqualTo(Number::class.java)
    assertThat(ID(0)).isEqualTo(ID.NIL)
    assertThat(ID(Long.MIN_VALUE).asMemorySegment().address()).isEqualTo(Long.MIN_VALUE)
    assertThat(Selector("first", 42)).isEqualTo(Selector("second", 42))
  }

  @Test
  fun `native signatures retain widths and structure alignment`() {
    val signature = ObjcTypeEncoding("q40@0:8i16q20d28").signature()
    assertThat(signature.descriptor.returnLayout().orElseThrow()).isEqualTo(JAVA_LONG)
    assertThat(signature.descriptor.argumentLayouts()).containsExactly(ADDRESS, ADDRESS, JAVA_INT, JAVA_LONG, JAVA_DOUBLE)
    val geometry = ObjcTypeEncoding("v@:{CGRect={CGPoint=dd}{CGSize=dd}}").signature().arguments.last().layout!!
    assertThat(geometry.byteSize()).isEqualTo(32)
    assertThat(geometry.byteAlignment()).isEqualTo(8)
    val padded = ObjcTypeEncoding("v@:{Sample=cd}").signature().arguments.last().layout!!
    assertThat(padded.byteSize()).isEqualTo(16)
    assertThat(ObjcTypeEncoding("v@:^v").signature().arguments.last().layout).isEqualTo(ADDRESS)
    assertThat(ObjcTypeEncoding("l@:L").signature().descriptor.returnLayout().orElseThrow()).isEqualTo(JAVA_INT)
    assertThatThrownBy { ObjcTypeEncoding("v@:{Broken=").signature() }.isInstanceOf(IllegalArgumentException::class.java)
    assertThatThrownBy { ObjcTypeEncoding("v@:D").signature() }.isInstanceOf(IllegalArgumentException::class.java)
  }

  @Test
  @EnabledOnOs(OS.MAC)
  fun `strings arrays and data preserve their contents`() = withPool {
    for (text in listOf("", "plain", "кириллица 日本語 😀", "left\u0000right", "x".repeat(100_000))) {
      assertThat(Foundation.toStringViaUTF8(Foundation.nsString(text))).isEqualTo(text)
      assertThat(Foundation.toStringViaUTF8(Foundation.nsString(StringBuilder(text)))).isEqualTo(text)
    }
    assertThat(Foundation.nsString(null as String?)).isEqualTo(ID.NIL)
    assertThat(Foundation.toStringViaUTF8(ID.NIL)).isNull()
    assertThat(Foundation.NSArray(Foundation.createArray()).count()).isZero()
    assertThatThrownBy { Foundation.createArray(ID.NIL) }.isInstanceOf(IllegalArgumentException::class.java)
    assertThatThrownBy { Foundation.createArray(ID.NIL) }.isInstanceOf(IllegalArgumentException::class.java)
    val strings = Array(100) { "value $it" }
    val array = Foundation.NSArray(Foundation.createArray(*strings))
    assertThat(array.list.map { Foundation.toStringViaUTF8(it) }).containsExactly(*strings)
    val dictionary = Foundation.createDict(arrayOf("key"), arrayOf("value"))
    assertThat(Foundation.NSDictionary.toStringMap(dictionary)).containsEntry("key", "value")
    assertThatThrownBy { Foundation.createDict(arrayOf("key"), emptyArray()) }.isInstanceOf(IllegalArgumentException::class.java)
    val bytes = byteArrayOf(0, 1, -1, 127)
    val data = Foundation.invoke("NSData", "dataWithBytes:length:", bytes, bytes.size.toLong())
    assertThat(Foundation.NSData(data).bytes()).containsExactly(*bytes)
    val copied = ByteArray(bytes.size)
    Foundation.invoke(data, "getBytes:length:", copied, copied.size.toLong())
    assertThat(copied).containsExactly(*bytes)
  }

  @Test
  @EnabledOnOs(OS.MAC)
  fun `native strings match independent JNA bindings`() = withPool {
    val oracle = Native.load("CoreFoundation", FoundationJnaOracle::class.java)
    val expected = "Independent 😀 日本語"
    val pointer = oracle.CFStringCreateWithCString(null, expected, 0x08000100)
    try {
      assertThat(Foundation.toStringViaUTF8(ID(Pointer.nativeValue(pointer)))).isEqualTo(expected)
      val string = Foundation.nsString(expected)
      val bytes = ByteArray(256)
      assertThat(oracle.CFStringGetCString(Pointer(string.toLong()), bytes, NativeLong(bytes.size.toLong()), 0x08000100)).isNotZero()
      assertThat(Native.toString(bytes, "UTF-8")).isEqualTo(expected)
    }
    finally {
      oracle.CFRelease(pointer)
    }
  }

  @Test
  @EnabledOnOs(OS.MAC)
  fun `native declarations control argument and result widths`() = withPool {
    val number = Foundation.invoke("NSNumber", "numberWithUnsignedInt:", -1)
    assertThat(Foundation.invoke(number, "unsignedIntValue").toLong()).isEqualTo(0xffffffffL)
    assertThat(Foundation.invoke("NSNumber", "numberWithLongLong:", Long.MIN_VALUE)
                 .let { Foundation.invoke(it, "longLongValue").toLong() }).isEqualTo(Long.MIN_VALUE)
    val boolean = Foundation.invoke("NSNumber", "numberWithBool:", true)
    assertThat(Foundation.invoke(boolean, "boolValue").booleanValue()).isTrue()
    val decimal = Foundation.invoke("NSNumber", "numberWithDouble:", Math.PI)
    assertThat(Foundation.invoke_fpret(decimal, "doubleValue")).isEqualTo(Math.PI)
    assertThat(Foundation.invoke(ID.NIL, "unavailableSelector:", ID.NIL)).isEqualTo(ID.NIL)
    assertThatThrownBy { Foundation.invoke("NSNumber", "numberWithInt:") }.isInstanceOf(IllegalArgumentException::class.java)
  }

  @Test
  @EnabledOnOs(OS.MAC)
  fun `structures pass by value and pointer references use native addresses`() = withPool {
    Arena.ofConfined().use { arena ->
      val rectangle = Foundation.NSRect(1.25, -2.5, 30.75, 40.0)
      val value = Foundation.invoke("NSValue", "valueWithRect:", rectangle)
      val output = arena.allocate(JAVA_DOUBLE, 4)
      Foundation.invoke(value, "getValue:size:", output, output.byteSize())
      assertThat(output.toArray(JAVA_DOUBLE)).containsExactly(1.25, -2.5, 30.75, 40.0)
      val error = Foundation.createPointerReference(arena)
      assertThat(Foundation.castPointerToNSError(error)).isEqualTo(ID.NIL)
      error.set(ADDRESS, 0, value.asMemorySegment())
      assertThat(Foundation.castPointerToNSError(error)).isEqualTo(value)
    }
  }

  @Test
  @EnabledOnOs(OS.MAC)
  fun `callbacks retain their targets and contain exceptions`() = withPool {
    val calls = AtomicInteger()
    val callback = object {
      @Suppress("unused", "UNUSED_PARAMETER")
      fun count(self: ID, selector: Selector, value: Int): Int = calls.addAndGet(value)

      @Suppress("unused", "UNUSED_PARAMETER")
      fun fail(self: ID, selector: Selector): Int = error("Expected callback failure")
    }
    val nativeClass = Foundation.allocateObjcClassPair(Foundation.getObjcClass("NSObject"),
                                                       "FoundationTest_" + UUID.randomUUID().toString().replace("-", ""))
    val countSelector = Foundation.createSelector("count:")
    assertThat(Foundation.addMethod(nativeClass, countSelector,
                                    Foundation.callback(callback,
                                                        "count",
                                                        ID::class.java,
                                                        Selector::class.java,
                                                        Int::class.javaPrimitiveType), "i@:i")).isTrue()
    assertThat(Foundation.addMethod(nativeClass, Foundation.createSelector("fail"),
                                    Foundation.callback(callback, "fail", ID::class.java, Selector::class.java), "i@:")).isTrue()
    Foundation.registerObjcClassPair(nativeClass)
    val instance = Foundation.invoke(nativeClass, "new")
    try {
      assertThat(Foundation.invoke(instance, countSelector, 2).toInt()).isEqualTo(2)
      assertThat(Foundation.invoke(instance, countSelector, 3).toInt()).isEqualTo(5)
      assertThat(Foundation.invoke(instance, "fail").toInt()).isZero()
      assertThat(Foundation.invoke(instance, countSelector, 1).toInt()).isEqualTo(6)
    }
    finally {
      Foundation.invoke(instance, "release")
    }
  }

  @Test
  @EnabledOnOs(OS.MAC)
  fun `forwarded methods use their Objective-C signatures`() = withPool {
    val callback = object {
      @Suppress("unused", "UNUSED_PARAMETER")
      fun signature(self: ID, selector: Selector, requested: Selector): ID =
        Foundation.invoke("NSMethodSignature", "signatureWithObjCTypes:", "i@:i")

      @Suppress("unused", "UNUSED_PARAMETER")
      fun forward(self: ID, selector: Selector, invocation: ID) {
        Arena.ofConfined().use { arena ->
          val value = arena.allocate(JAVA_INT)
          Foundation.invoke(invocation, "getArgument:atIndex:", value, 2L)
          value.set(JAVA_INT, 0, value.get(JAVA_INT, 0) + 1)
          Foundation.invoke(invocation, "setReturnValue:", value)
        }
      }
    }
    val nativeClass = Foundation.allocateObjcClassPair(Foundation.getObjcClass("NSProxy"),
                                                       "FoundationProxyTest_" + UUID.randomUUID().toString().replace("-", ""))
    assertThat(Foundation.addMethod(nativeClass, Foundation.createSelector("methodSignatureForSelector:"),
                                    Foundation.callback(callback, "signature"), "@@::")).isTrue()
    assertThat(Foundation.addMethod(nativeClass, Foundation.createSelector("forwardInvocation:"),
                                    Foundation.callback(callback, "forward"), "v@:@")).isTrue()
    Foundation.registerObjcClassPair(nativeClass)
    val proxy = Foundation.invoke(nativeClass, "alloc")
    try {
      assertThat(Foundation.invoke(proxy, "readValue:", 41).toInt()).isEqualTo(42)
    }
    finally {
      Foundation.invoke(proxy, "release")
    }
  }

  @Test
  @EnabledOnOs(OS.MAC)
  fun `main thread work completes and activity cleanup is idempotent`() {
    java.awt.Toolkit.getDefaultToolkit()
    val calls = AtomicInteger()
    Foundation.executeOnMainThread(true, true) {
      assertThat(Foundation.isMainThread()).isTrue()
      calls.incrementAndGet()
    }
    val completed = CountDownLatch(1)
    Foundation.executeOnMainThread(true, false) {
      calls.incrementAndGet()
      completed.countDown()
    }
    assertThat(completed.await(10, TimeUnit.SECONDS)).isTrue()
    assertThat(calls.get()).isEqualTo(2)
    val activity = com.intellij.ui.mac.foundation.MacUtil.wakeUpNeo("Foundation test")
    activity.run()
    activity.run()
  }

  @Test
  @EnabledOnOs(OS.MAC)
  fun `foundation loads without JNA`() {
    val parent = Foundation::class.java.classLoader
    val loader = object : ClassLoader(parent) {
      override fun loadClass(name: String, resolve: Boolean): Class<*> = synchronized(getClassLoadingLock(name)) {
        if (name.startsWith("com.sun.jna.") || name.startsWith("com.intellij.jna.")) throw ClassNotFoundException(name)
        if (!name.startsWith("com.intellij.ui.mac.foundation.")) return@synchronized super.loadClass(name, resolve)
        val loaded = findLoadedClass(name) ?: run {
          val bytes = parent.getResourceAsStream(name.replace('.', '/') + ".class")!!.use { it.readBytes() }
          defineClass(name, bytes, 0, bytes.size)
        }
        if (resolve) resolveClass(loaded)
        loaded
      }
    }
    val foundation = loader.loadClass(Foundation::class.java.name)
    assertThat(foundation.getMethod("isAvailable").invoke(null)).isEqualTo(true)
    val poolClass = loader.loadClass(Foundation.NSAutoreleasePool::class.java.name)
    val pool = poolClass.getConstructor().newInstance()
    try {
      val nativeString = foundation.getMethod("nsString", String::class.java).invoke(null, "without JNA")
      assertThat(foundation.getMethod("toStringViaUTF8", loader.loadClass(ID::class.java.name))
                   .invoke(null, nativeString)).isEqualTo("without JNA")
      val calls = AtomicInteger()
      foundation.getMethod("executeOnMainThread", Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType, Runnable::class.java)
        .invoke(null, true, true, Runnable { calls.incrementAndGet() })
      assertThat(calls.get()).isEqualTo(1)
    }
    finally {
      poolClass.getMethod("drain").invoke(pool)
    }
  }

  private inline fun withPool(action: () -> Unit) {
    val pool = Foundation.NSAutoreleasePool()
    try {
      action()
    }
    finally {
      pool.drain()
    }
  }
}

internal interface FoundationJnaOracle : Library {
  fun CFStringCreateWithCString(allocator: Pointer?, text: String, encoding: Int): Pointer
  fun CFStringGetCString(string: Pointer, bytes: ByteArray, size: NativeLong, encoding: Int): Byte
  fun CFRelease(value: Pointer)
}

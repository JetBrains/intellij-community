// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util

import com.intellij.openapi.Disposable
import com.intellij.testFramework.LeakHunter
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.PairProcessor
import com.intellij.util.ref.GCUtil
import com.intellij.util.ref.IgnoredTraverseEntry
import com.intellij.util.ref.IgnoredTraverseReference
import org.junit.Assert.assertTrue
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.util.IdentityHashMap
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Predicate
import java.util.function.Supplier
import java.util.stream.Stream
import kotlin.test.assertFalse
import kotlin.time.Duration.Companion.seconds

@TestApplication
class LeakHunterIgnoreReferencesTest {
  @Test
  fun `leaked disposable ignored`(): Unit = timeoutRunBlocking(60.seconds) {
    val ref = ReferenceToDisposable(TestDisposable())
    assertThrows<AssertionError> { checkReferenced(ref, listOf()) }
    assertDoesNotThrow { checkReferenced(ref, listOf(IgnoredTraverseReference("com.intellij.openapi.util.ReferenceToDisposable.ref", -1))) }
  }
  fun checkReferenced(root: Any, ignoredTraverseEntries: List<IgnoredTraverseEntry>) {
    val rootSupplier: Supplier<Map<Any, String>> = Supplier {
      mapOf(root to "root")
    }
    LeakHunter.checkLeak(rootSupplier, TestDisposable::class.java, ignoredTraverseEntries) { true }
  }

  @Test
  fun testLeakHunterDoesntReportFalsePositivesFromWeakHashMap() {
    val javaMap: WeakHashMap<Any?, MyLeakData?> = WeakHashMap<Any?, MyLeakData?>()
    val disposable = AtomicReference(Any())
    javaMap.put(disposable.get(), MyLeakData())

    disposable.set(null)

    LeakHunter.processLeaks({ mapOf(javaMap to "Standard WeakHashMap")}, MyLeakData::class.java, Predicate { leak: MyLeakData? -> true }, null, PairProcessor { _, _ ->
      Assertions.fail("Found a leak!")
    })
  }
  @Test
  fun testLeakHunterDoesntReportDisposedDisposablesFromDisposer() {
    val disposable: AtomicReference<Disposable.Default?> = AtomicReference(object : Disposable.Default {})
    Disposer.dispose(disposable.get()!!)
    Disposer.getDisposalTrace(disposable.get()!!).addSuppressed(MyLeakThrowable(MyLeakData()))
    //Ensure that the reference is GC'ed, even in interpreter mode
    disposable.set(null)
    LeakHunter.processLeaks({ mapOf(Disposer.getTree() to "Disposer.getTree()")}, MyLeakData::class.java, Predicate { leak: MyLeakData? -> true }, null, PairProcessor { _, _ ->
      Assertions.fail("Found a leak!")
    })
  }
}

private class MyLeakData
private class MyLeakThrowable(private val myData: MyLeakData) : Throwable()

class TestDisposable : Disposable {
  override fun dispose() {}
}

class ReferenceToDisposable(val ref: Disposable)


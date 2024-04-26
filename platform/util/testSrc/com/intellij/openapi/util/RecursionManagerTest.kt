// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util

import com.intellij.openapi.Disposable
import com.intellij.testFramework.UsefulTestCase
import junit.framework.TestCase

class RecursionManagerTest : TestCase() {
  private val myGuard: RecursionGuard<Any> = RecursionManager.createGuard("RecursionManagerTest")
  private val myDisposable: Disposable = Disposer.newDisposable()

  public override fun setUp() {
    RecursionManager.assertOnMissedCache(myDisposable)
    super.setUp()
  }

  public override fun tearDown() {
    Disposer.dispose(myDisposable)
    super.tearDown()
  }

  fun prevent(key: Any, memoize: Boolean = true, c: Computable<Any>): Any? {
    return myGuard.doPreventingRecursion(key, memoize, c)
  }

  fun testPreventRecursion() {
    assertEquals("foo-return", prevent("foo") {
      assertEquals("bar-return", prevent("bar") {
        assertEquals(null, prevent("foo") { "foo-return" })
        "bar-return"
      })
      "foo-return"
    })
  }

  fun testAssertOnMissedCache() {
    assertEquals("foo-return", prevent("foo") {
      val stamp = RecursionManager.markStack()
      assertEquals(null, prevent("foo") { fail() })
      UsefulTestCase.assertThrows(RecursionManager.CachingPreventedException::class.java) { stamp.mayCacheNow() }
      "foo-return"
    })
  }

  private fun methodWhichShouldBePresentInPreventionTrace() {
    prevent("inner") {
      assertEquals(null, prevent("outer") {
        fail()
      })
      "inner-return"
    }
  }

  fun testMemoizedValueAccessDoesntClearPreventionTrace() {
    assertEquals("outer-return", prevent("outer") {
      val stamp = RecursionManager.markStack()
      methodWhichShouldBePresentInPreventionTrace()  // prevents caching until exited from 'outer'
      assertEquals("inner-return", prevent("inner") {             // memoized value from previous call
        fail()
      })
      try {
        stamp.mayCacheNow()
        fail()
      }
      catch (e: RecursionManager.CachingPreventedException) {
        val soe = UsefulTestCase.assertInstanceOf(e.cause, StackOverflowPreventedException::class.java)
        assertTrue(soe.stackTrace.any { ste ->
          ste.methodName == "methodWhichShouldBePresentInPreventionTrace"
        })
      }
      "outer-return"
    })
  }

  fun testMemoization() {
    assertEquals("foo-return", prevent("foo") {
      assertEquals("bar-return", prevent("bar") {
        assertEquals(null, prevent("foo") { "foo-return" })
        "bar-return"
      })
      assertEquals("bar-return", prevent("bar") {
        fail()
      })
      "foo-return"
    })
  }

  fun testNoMemoizationAfterExit() {
    assertEquals("foo-return", prevent("foo") {
      assertEquals("bar-return", prevent("bar") {
        assertEquals(null, prevent("foo") { "foo-return" })
        "bar-return"
      })
      "foo-return"
    })
    assertEquals("bar-return2", prevent("bar") {
      "bar-return2"
    })
  }

  fun `test no memoization after exiting SOE loop inside another preventing call`() {
    prevent("unrelated") {
      testNoMemoizationAfterExit()
    }
  }

  fun `test memoize when the we run into the same prevention via different route`() {
    prevent("foo") {
      prevent("foo") { fail() }
      assertEquals("x", prevent("bar") {
        prevent("foo") { fail() }
        "x"
      })
      assertEquals("x", prevent("bar") { fail() })
    }
  }

  fun testMayCache() {
    RecursionManager.disableMissedCacheAssertions(myDisposable)
    val doo1 = RecursionManager.markStack()
    assertEquals("doo-return", prevent("doo") {
      val foo1 = RecursionManager.markStack()
      assertEquals("foo-return", prevent("foo") {
        val bar1 = RecursionManager.markStack()
        assertEquals("bar-return", prevent("bar") {
          val foo2 = RecursionManager.markStack()
          assertEquals(null, prevent("foo") { "foo-return" })
          assertFalse(foo2.mayCacheNow())
          "bar-return"
        })
        assertFalse(bar1.mayCacheNow())

        val goo1 = RecursionManager.markStack()
        assertEquals("goo-return", prevent("goo") {
          "goo-return"
        })
        assertTrue(goo1.mayCacheNow())
        assertFalse(bar1.mayCacheNow())

        "foo-return"
      })
      assertTrue(foo1.mayCacheNow())
      "doo-return"
    })
    assertTrue(doo1.mayCacheNow())
  }

  fun testNoCachingForMemoizedValues() {
    RecursionManager.disableMissedCacheAssertions(myDisposable)
    assertEquals("foo-return", prevent("foo") {
      assertEquals("bar-return", prevent("bar") {
        assertEquals(null, prevent("foo") { "foo-return" })
        "bar-return"
      })
      val stamp = RecursionManager.markStack()
      assertEquals("bar-return", prevent("bar") {
        fail()
      })
      assertFalse(stamp.mayCacheNow())
      "foo-return"
    })
  }

  fun testNoCachingForMemoizedValues2() {
    RecursionManager.disableMissedCacheAssertions(myDisposable)
    assertEquals("1-return", prevent("1") {
      assertEquals("2-return", prevent("2") {
        assertEquals("3-return", prevent("3") {
          assertEquals(null, prevent("2") { "2-return" })
          assertEquals(null, prevent("1") { "1-return" })
          "3-return"
        })
        "2-return"
      })
      var stamp = RecursionManager.markStack()
      assertEquals("2-return", prevent("2") { fail() })
      assertFalse(stamp.mayCacheNow())

      stamp = RecursionManager.markStack()
      assertEquals("3-return", prevent("3") { fail() })
      assertFalse(stamp.mayCacheNow())

      "1-return"
    })
  }

  fun testNoMemoizationForNoReentrancy() {
    assertEquals("foo-return", prevent("foo") {
      assertEquals(null, prevent("foo") { "foo-return" })
      assertEquals("bar-return", prevent("bar") { "bar-return" })
      val stamp = RecursionManager.markStack()
      assertEquals("bar-return2", prevent("bar") { "bar-return2" })
      assertTrue(stamp.mayCacheNow())
      "foo-return"
    })
  }

  fun testFullGraphPerformance() {
    val start = System.currentTimeMillis()
    val count = 20
    var cl: () -> Any = {}
    cl = {
      (1..count).forEach { i ->
        prevent("foo" + i, c = cl)
      }
      "zoo"
    }

    assertEquals("zoo", cl())

    assertTrue(System.currentTimeMillis() - start < 10000)
  }

  private inner class FullGraphCorrectness {
    fun a(): Set<String> {
      return prevent("a") { a() + b() + setOf("a") } as Set<String>? ?: setOf()
    }

    fun b(): Set<String> {
      return prevent("b") { a() + b() + setOf("b") } as Set<String>? ?: setOf()
    }

    fun ensureSymmetric() {
      assertEquals(setOf("a", "b"), a())
      assertEquals(setOf("a", "b"), b())
    }
  }

  fun `test full graph correctness`() {
    FullGraphCorrectness().ensureSymmetric()
    prevent("unrelated") {
      FullGraphCorrectness().ensureSymmetric()
    }
  }

  fun `test changing hash code doesn't crash RecursionManager`() {
    val key = mutableListOf("b")
    prevent(key) {
      key.add("a")
    }
  }

  fun `test key equals that invokes RecursionManager`() {
    prevent(RecursiveKey("a")) {
      prevent(RecursiveKey("b")) {
        prevent(RecursiveKey("a")) {
          throw AssertionError("shouldn't be called")
        }
      }
    }
  }

  data class RecursiveKey(val id: String) {
    override fun hashCode(): Int {
      return id.hashCode()
    }

    override fun equals(other: Any?): Boolean {
      RecursionManager.doPreventingRecursion("abc", false) { true }
      return other is RecursiveKey && other.id == id
    }
  }

  fun `test exception from hashCode on exiting`() {
    val key1 = ThrowingKey()
    val key2 = ThrowingKey()
    val key3 = ThrowingKey()
    prevent(key1) {
      prevent(key2) {
        prevent(key3) {
          key1.fail = true
          key2.fail = true
          key3.fail = true
          true
        }
      }
    }
  }

  private class ThrowingKey {
    var fail = false

    override fun hashCode(): Int {
      if (fail) {
        throw RuntimeException()
      }
      return 0
    }

    override fun equals(obj: Any?): Boolean {
      if (fail) {
        throw RuntimeException()
      }
      return super.equals(obj)
    }
  }
}
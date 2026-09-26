// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicLong

internal class ObjectCollectorTest {

  class TestObject(
    val number: Int,
    val testHandleInvocationCounter: AtomicLong = AtomicLong(),
  ) {

    fun testHandle(): Int {
      testHandleInvocationCounter.incrementAndGet()
      return number
    }

    fun myCustomEquals(o2: TestObject): Boolean {
      testHandleInvocationCounter.incrementAndGet()
      return number == o2.number
    }
  }

  class TestHasher : ObjectCollector.Hasher<TestObject> {
    override fun computeHashCode(target: TestObject): Int {
      return target.testHandle()
    }

    override fun equals(o1: TestObject, o2: TestObject): Boolean = o1.myCustomEquals(o2)
  }

  @Test
  fun `test custom hashing strategy`() {
    val collector = ObjectCollector<TestObject, RuntimeException>(TestHasher())
    val callbackLatch = CountDownLatch(3)

    val firstDummy = TestObject(42)
    collector.add(firstDummy, ObjectCollector.Processor { added, id ->
      assertTrue(added)
      assertEquals(1, id)
      callbackLatch.countDown()
    })

    val secondDummy = TestObject(24)
    collector.add(secondDummy, ObjectCollector.Processor { added, id ->
      assertTrue(added)
      assertEquals(2, id)
      callbackLatch.countDown()
    })

    val thirdDummy = TestObject(42)
    collector.add(thirdDummy, ObjectCollector.Processor { added, id ->
      assertFalse(added)
      assertEquals(1, id)
      callbackLatch.countDown()
    })

    assertEquals(1L, firstDummy.testHandleInvocationCounter.get())
    assertEquals(2L, secondDummy.testHandleInvocationCounter.get())
    assertEquals(2L, thirdDummy.testHandleInvocationCounter.get())
    assertEquals(0, callbackLatch.count)
  }
}

// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics.utils

import com.intellij.internal.statistic.eventLog.connection.StatisticsCachingSupplier
import junit.framework.TestCase
import org.junit.Test
import kotlin.test.assertEquals

class StatisticsCachedConfigTest {
  @Test
  fun `test cached value`() {
    var counter = 0
    val supplier = StatisticsCachingSupplier<Int>({ ++counter }, 50)

    assertEquals(1, supplier.get())
    assertEquals(1, supplier.get())
    assertEquals(1, supplier.get())
    assertEquals(1, supplier.get())
    Thread.sleep(50)
    assertEquals(2, supplier.get())
    assertEquals(2, supplier.get())
    assertEquals(2, supplier.get())
  }

  @Test
  fun `test value re-calculated on short timeout`() {
    var counter = 0
    val supplier = StatisticsCachingSupplier<Int>({ ++counter }, 4)

    assertEquals(1, supplier.get())
    Thread.sleep(5)
    assertEquals(2, supplier.get())
    Thread.sleep(5)
    assertEquals(3, supplier.get())
    Thread.sleep(5)
    assertEquals(4, supplier.get())
  }

  @Test
  fun `test value re-calculated on exception`() {
    var counter = 0
    val supplier = StatisticsCachingSupplier<Int>(
      { ++counter; if (counter % 2 == 0) throw RuntimeException() else counter },
      4
    )

    assertEquals(1, supplier.get())
    Thread.sleep(5)
    assertExceptionThrown(Runnable { supplier.get() })
    assertEquals(3, supplier.get())
    Thread.sleep(5)
    assertExceptionThrown(Runnable { supplier.get() })
    assertEquals(5, supplier.get())
  }

  private fun assertExceptionThrown(runnable: Runnable) {
    var thrown = false
    try {
      runnable.run()
    }
    catch (e: RuntimeException) {
      thrown = true
    }
    TestCase.assertTrue(thrown)
  }

}
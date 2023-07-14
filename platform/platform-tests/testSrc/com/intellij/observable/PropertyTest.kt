// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.observable

import com.intellij.openapi.observable.util.transform
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class PropertyTest : PropertyTestCase() {

  @Test
  fun `test property simple usage`() {
    val property = property { "initial value" }
    assertProperty(property, "initial value")
    property.set("value")
    assertProperty(property, "value")
    property.set("initial value")
    assertProperty(property, "initial value")
  }

  @Test
  fun `test property delegation usage`() {
    val property = property { "initial value" }
    var value by property
    assertEquals(value, "initial value")
    assertProperty(property, "initial value")
    value = "value"
    assertEquals(value, "value")
    assertProperty(property, "value")
    value = "initial value"
    assertEquals(value, "initial value")
    assertProperty(property, "initial value")
  }

  @Test
  fun `test property propagation of modification`() {
    val property1 = property { 0 }
    val property2 = property { 0 }
    val property3 = property { 0 to 0 }
    val property4 = property { 0 }
    val property5 = property { 0 }
    val property6 = property { 0 }

    /**
     * (1)⟷(2)⟷(3)⟷(4)⟷(5)
     * (3)⟷(6)
     */
    property1.dependsOn(property2) { property2.get() }
    property2.dependsOn(property1) { property1.get() }
    property2.dependsOn(property3) { property3.get().first }
    property3.dependsOn(property2) { property2.get() to property4.get() }
    property3.dependsOn(property4) { property2.get() to property4.get() }
    property4.dependsOn(property3) { property3.get().second }
    property4.dependsOn(property5) { property5.get() }
    property5.dependsOn(property4) { property4.get() }
    property6.dependsOn(property3) { property3.get().first + property3.get().second }

    assertProperty(property1, 0)
    assertProperty(property2, 0)
    assertProperty(property3, 0 to 0)
    assertProperty(property4, 0)
    assertProperty(property5, 0)
    assertProperty(property6, 0)

    property1.set(2)
    assertProperty(property1, 2)
    assertProperty(property2, 2)
    assertProperty(property3, 2 to 0)
    assertProperty(property4, 0)
    assertProperty(property5, 0)
    assertProperty(property6, 2)

    property1.set(4)
    assertProperty(property1, 4)
    assertProperty(property2, 4)
    assertProperty(property3, 4 to 0)
    assertProperty(property4, 0)
    assertProperty(property5, 0)
    assertProperty(property6, 4)

    property5.set(7)
    assertProperty(property1, 4)
    assertProperty(property2, 4)
    assertProperty(property3, 4 to 7)
    assertProperty(property4, 7)
    assertProperty(property5, 7)
    assertProperty(property6, 11)

    property3.set(12 to 18)
    assertProperty(property1, 4)
    assertProperty(property2, 12)
    assertProperty(property3, 12 to 18)
    assertProperty(property4, 18)
    assertProperty(property5, 7)
    assertProperty(property6, 30)
  }

  @Test
  fun `test property listening`() {
    val property1 = property { 0 }
    val property2 = property { 0 }
    val property3 = property { 0 }
    val property4 = property { 0 }

    /**
     * (1)⟷(2)⟷(3)⟷(4)
     */
    property1.dependsOn(property2) { property2.get() }
    property2.dependsOn(property1) { property1.get() }
    property2.dependsOn(property3) { property3.get() }
    property3.dependsOn(property2) { property2.get() }
    property3.dependsOn(property4) { property4.get() }
    property4.dependsOn(property3) { property3.get() }

    val propagationCounters = listOf(property1, property2, property3, property4)
      .map { property ->
        AtomicInteger(0).also { counter ->
          property.afterPropagation {
            counter.incrementAndGet()
          }
        }
      }
    val changeCounters = listOf(property1, property2, property3, property4)
      .map { property ->
        AtomicInteger(0).also { counter ->
          property.afterChange {
            counter.incrementAndGet()
          }
        }
      }

    property3.set(0)
    assertEquals(listOf(1, 1, 1, 1), propagationCounters.map { it.get() })
    assertEquals(listOf(1, 1, 1, 1), changeCounters.map { it.get() })

    property3.set(0)
    assertEquals(listOf(2, 2, 2, 2), propagationCounters.map { it.get() })
    assertEquals(listOf(2, 2, 2, 2), changeCounters.map { it.get() })

    property4.set(0)
    assertEquals(listOf(3, 3, 3, 3), propagationCounters.map { it.get() })
    assertEquals(listOf(2, 2, 2, 3), changeCounters.map { it.get() })

    property1.set(0)
    assertEquals(listOf(4, 4, 4, 4), propagationCounters.map { it.get() })
    assertEquals(listOf(3, 3, 2, 3), changeCounters.map { it.get() })

    property1.set(0)
    assertEquals(listOf(5, 5, 5, 5), propagationCounters.map { it.get() })
    assertEquals(listOf(4, 4, 2, 3), changeCounters.map { it.get() })

    property2.set(0)
    assertEquals(listOf(6, 6, 6, 6), propagationCounters.map { it.get() })
    assertEquals(listOf(4, 5, 2, 3), changeCounters.map { it.get() })
  }

  @Test
  fun `test unsafe concurrent modification`() {
    val numCounts = 1000
    val numProperties = 10

    val accumulator = property { 0 }
    val producers = generate(numProperties) { property { 0 } }
    val consumers = generate(numProperties) { property { 0 } }

    producers.zip(consumers).forEach { (producer, consumer) ->
      consumer.dependsOn(producer) { producer.get() }
      accumulator.dependsOn(producer) { producers.sumOf { it.get() } }
    }

    val startLatch = CountDownLatch(1)
    val finishLatch = CountDownLatch(numProperties)
    repeat(numProperties) {
      val property = producers[it]
      thread {
        startLatch.await()
        repeat(numCounts) {
          property.set(property.get() + 1)
        }
        finishLatch.countDown()
      }
    }
    startLatch.countDown()
    finishLatch.await()

    assertEquals(numProperties * numCounts, producers.sumOf { it.get() })
    assertEquals(numProperties * numCounts, consumers.sumOf { it.get() })
    assertEquals(numProperties * numCounts, accumulator.get())
  }

  @Test
  fun `test property view`() {
    val property0 = property { 0 }
    val property1 = property { 1 }.transform({ it * 2 }, { it })
    val property2 = property { 2 }.transform({ it }, { it / 2 })
    val property3 = property { 3 }.transform({ it * 3 }, { it / 3 })
    val property4 = property { 4 to 5 }.transform({ it.first * it.second }, { (it / 5) to (it / 4) })

    val properties = listOf(property0, property1, property2, property3, property4)

    /**
     * (0) -> (1) -> (2) ⟷ (3) <- (4)
     */
    property1.dependsOn(property0) { property0.get() }
    property2.dependsOn(property1) { property1.get() }
    property3.dependsOn(property2) { property2.get() }
    property2.dependsOn(property3) { property3.get() }
    property3.dependsOn(property4) { property4.get() }

    assertProperties(properties, 0, 2, 2, 9, 20)

    property0.set(3)
    assertProperties(properties, 3, 6, 3, 3, 20)

    property4.set(100)
    assertProperties(properties, 3, 6, 249, 498, 500)

    property2.set(100)
    assertProperties(properties, 3, 6, 50, 48, 500)
  }

  @Test
  fun `test property dependency blocking`() {
    val property0 = property { 0 }
    val property1 = property { 0 }
    val property2 = property { 0 }

    val properties = listOf(property0, property1, property2)

    property1.dependsOn(property0, deleteWhenModified = true) { property0.get() }
    property2.dependsOn(property0, deleteWhenModified = false) { property0.get() }

    assertProperties(properties, 0, 0, 0)

    property0.set(1)
    assertProperties(properties, 1, 1, 1)

    property0.set(2)
    assertProperties(properties, 2, 2, 2)

    property1.set(3)
    assertProperties(properties, 2, 3, 2)

    property0.set(4)
    assertProperties(properties, 4, 3, 4)

    property1.set(5)
    assertProperties(properties, 4, 5, 4)

    property2.set(6)
    assertProperties(properties, 4, 5, 6)

    property0.set(7)
    assertProperties(properties, 7, 5, 7)
  }

  @Test
  fun `test graph propagation event`() {
    val property0 = property { 0 }
    val property1 = property { 0 }
    val property2 = property { 0 }

    val propagationCounter = AtomicInteger(0)
    afterGraphPropagation { propagationCounter.incrementAndGet() }

    property1.dependsOn(property0) { property0.get() }

    assertEquals(0, propagationCounter.get())
    property0.set(1)
    assertEquals(1, propagationCounter.get())
    property1.set(1)
    assertEquals(2, propagationCounter.get())
    property2.set(1)
    assertEquals(3, propagationCounter.get())
  }
}
// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.concurrency

import com.intellij.util.containers.CollectionFactory
import com.intellij.util.containers.HashingStrategy
import com.intellij.util.containers.ReferenceQueueable
import com.sun.management.ThreadMXBean
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.lang.management.ManagementFactory
import java.lang.ref.Reference
import java.lang.ref.SoftReference
import java.lang.ref.WeakReference
import java.util.AbstractMap.SimpleEntry
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class ConcurrentCollectionFactoryTest {
  @Test
  fun `factories support original keys and custom hashing`() {
    val maps = listOf(
      ConcurrentCollectionFactory.createConcurrentWeakMap<String, String>(),
      ConcurrentCollectionFactory.createConcurrentSoftMap<String, String>(),
      ConcurrentCollectionFactory.createConcurrentWeakMap<String, String>(HashingStrategy.caseInsensitive()),
      ConcurrentCollectionFactory.createConcurrentSoftMap<String, String>(16, 0.75f, HashingStrategy.caseInsensitive()),
    )
    for (map in maps) {
      map["key"] = "value"
      assertThat(map["key"]).isEqualTo("value")
      assertThat(map.containsKey("key")).isTrue()
      assertThat(map["missing"]).isNull()
    }
    for (map in maps.drop(2)) {
      assertThat(map["KEY"]).isEqualTo("value")
      assertThat(map.putIfAbsent("KEY", "other")).isEqualTo("value")
      assertThat(map).hasSize(1)
    }
  }

  @Test
  fun `reference value factories support original keys and custom hashing`() {
    val maps = referenceValueMaps<String, String>(HashingStrategy.caseInsensitive()) +
               ConcurrentCollectionFactory.createConcurrentSoftKeySoftValueMap(16, 0.75f, 4, HashingStrategy.caseInsensitive())
    for (map in maps) {
      map["key"] = "value"
      assertThat(map["KEY"]).isEqualTo("value")
      assertThat(map["missing"]).isNull()
      assertThat(map.putIfAbsent("KEY", "other")).isEqualTo("value")
      assertThat(map).hasSize(1)
    }
  }

  @Test
  fun `reference value factories use the requested reference strengths`() {
    val key = Any()
    val value = Any()
    val weakSoft = ConcurrentCollectionFactory.createConcurrentWeakKeySoftValueMap<Any, Any>()
    val weakWeak = ConcurrentCollectionFactory.createConcurrentWeakKeyWeakValueMap<Any, Any>()
    val softSoft = ConcurrentCollectionFactory.createConcurrentSoftKeySoftValueMap<Any, Any>()
    for (map in listOf(weakSoft, weakWeak, softSoft)) {
      map[key] = value
    }

    assertThat(reference(weakSoft, key)).isInstanceOf(WeakReference::class.java)
    assertThat(valueReference(weakSoft)).isInstanceOf(SoftReference::class.java)
    assertThat(reference(weakWeak, key)).isInstanceOf(WeakReference::class.java)
    assertThat(valueReference(weakWeak)).isInstanceOf(WeakReference::class.java)
    assertThat(reference(softSoft, key)).isInstanceOf(SoftReference::class.java)
    assertThat(valueReference(softSoft)).isInstanceOf(SoftReference::class.java)
    Reference.reachabilityFence(key)
    Reference.reachabilityFence(value)
  }

  @Test
  fun `reference value identity factories use identity equality`() {
    val maps = listOf(
      ConcurrentCollectionFactory.createConcurrentWeakKeySoftValueIdentityMap<String, String>(16, 0.75f, 4),
      ConcurrentCollectionFactory.createConcurrentWeakKeyWeakValueIdentityMap<String, String>(),
      ConcurrentCollectionFactory.createConcurrentSoftKeySoftValueIdentityMap<String, String>(16, 0.75f, 4),
    )
    for (map in maps) {
      val key = String(charArrayOf('k', 'e', 'y'))
      val equalKey = String(charArrayOf('k', 'e', 'y'))
      map[key] = "value"
      assertThat(map[key]).isEqualTo("value")
      assertThat(map[equalKey]).isNull()
    }
  }

  @Test
  fun `weak factory variants use requested hashing strategies`() {
    val caseInsensitiveMap = ConcurrentCollectionFactory.createConcurrentWeakCaseInsensitiveMap<String>()
    caseInsensitiveMap["key"] = "value"
    assertThat(caseInsensitiveMap["KEY"]).isEqualTo("value")

    val identityMap = ConcurrentCollectionFactory.createConcurrentWeakIdentityMap<String, String>()
    val key = String(charArrayOf('k', 'e', 'y'))
    val equalKey = String(charArrayOf('k', 'e', 'y'))
    identityMap[key] = "value"
    assertThat(identityMap[key]).isEqualTo("value")
    assertThat(identityMap[equalKey]).isNull()

    val configuredMap = ConcurrentCollectionFactory.createConcurrentWeakMap<String, String>(
      1, 0.5f, HashingStrategy.caseInsensitive(),
    )
    configuredMap["key"] = "value"
    assertThat(configuredMap["KEY"]).isEqualTo("value")
  }

  @Test
  fun `listener factory variants report evictions`() {
    val evictedValues = mutableListOf<String?>()
    val listener = CollectionFactory.EvictionListener<String, String, String> { _, _, value ->
      evictedValues.add(value)
    }
    val maps = listOf(
      ConcurrentCollectionFactory.createConcurrentWeakIdentityMap(listener),
      ConcurrentCollectionFactory.createConcurrentSoftMap(listener),
      ConcurrentCollectionFactory.createConcurrentSoftMap(HashingStrategy.caseInsensitive(), listener),
    )

    for ((index, map) in maps.withIndex()) {
      val key = String(charArrayOf('k', ('0'.code + index).toChar()))
      map[key] = "value-$index"
      val reference = reference(map, key)
      reference.clear()
      assertThat(reference.enqueue()).isTrue()
      assertThat(processQueue(map)).isTrue()
    }

    assertThat(evictedValues).containsExactly("value-0", "value-1", "value-2")
  }

  @Test
  fun `atomic mutations use original keys`() {
    for (map in lookupMaps<String, String>(HashingStrategy.caseInsensitive())) {
      assertThat(map.putIfAbsent("key", "first")).isNull()
      assertThat(map.putIfAbsent("KEY", "second")).isEqualTo("first")
      assertThat(map.replace("KEY", "wrong", "second")).isFalse()
      assertThat(map.replace("KEY", "first", "second")).isTrue()
      assertThat(map.replace("KEY", "third")).isEqualTo("second")
      assertThat(map.remove("KEY", "wrong")).isFalse()
      assertThat(map.remove("KEY", "third")).isTrue()
      assertThat(map.replace("missing", "value")).isNull()
      map["key"] = "value"
      assertThat(map.remove("KEY")).isEqualTo("value")
      assertThat(map).isEmpty()
    }
  }

  @Test
  fun `default concurrent operations preserve map semantics`() {
    for (map in maps<String, Int>(HashingStrategy.canonical())) {
      assertThat(map.computeIfAbsent("key") { 1 }).isEqualTo(1)
      assertThat(map.computeIfAbsent("key") { 2 }).isEqualTo(1)
      assertThat(map.computeIfPresent("key") { _, value -> value + 1 }).isEqualTo(2)
      assertThat(map.compute("key") { _, value -> value!! + 1 }).isEqualTo(3)
      assertThat(map.merge("key", 4, Int::plus)).isEqualTo(7)
      map.replaceAll { _, value -> value + 1 }
      assertThat(map["key"]).isEqualTo(8)
      assertThat(map.compute("key") { _, _ -> null }).isNull()
      assertThat(map).isEmpty()
    }
  }

  @Test
  fun `cleared references do not match live keys`() {
    for (map in lookupMaps<Key, String>(keyStrategy)) {
      val key1 = Key(0)
      val key2 = Key(8)
      map[key1] = "first"
      map[key2] = "second"
      val reference = reference(map, key1)
      reference.clear()

      assertThat(map[key1]).isNull()
      assertThat(map[Key(8)]).isEqualTo("second")
      assertThat(reference.enqueue()).isTrue()
      assertThat(processQueue(map)).isTrue()
      assertThat(processQueue(map)).isFalse()
      assertThat(map).hasSize(1)
      Reference.reachabilityFence(key2)
    }
  }

  @Test
  fun `queue cleanup preserves a replacement with an equal key`() {
    for (map in maps<String, String>(HashingStrategy.caseInsensitive())) {
      map["key"] = "first"
      val reference = reference(map, "key")
      assertThat(map.remove("key")).isEqualTo("first")
      map["KEY"] = "second"
      reference.clear()
      assertThat(reference.enqueue()).isTrue()

      assertThat(processQueue(map)).isTrue()
      assertThat(map["key"]).isEqualTo("second")
      assertThat(map).hasSize(1)
    }
  }

  @Test
  fun `reference value queue cleanup preserves a replacement with an equal key`() {
    for (map in referenceValueMaps<String, String>(HashingStrategy.caseInsensitive())) {
      map["key"] = "first"
      val reference = reference(map, "key")
      assertThat(map.remove("key")).isEqualTo("first")
      map["KEY"] = "second"
      reference.clear()
      assertThat(reference.enqueue()).isTrue()

      processQueue(map)
      assertThat(map["key"]).isEqualTo("second")
      assertThat(map).hasSize(1)
    }
  }

  @Test
  fun `collected values remove their entries`() {
    for (map in referenceValueMaps<Any, Any>(HashingStrategy.canonical())) {
      val key = Any()
      val value = Any()
      map[key] = value
      val valueReference = valueReference(map)
      valueReference.clear()
      assertThat(valueReference.enqueue()).isTrue()

      assertThat(processQueue(map)).isTrue()
      assertThat(map[key]).isNull()
      assertThat(map).isEmpty()
      Reference.reachabilityFence(key)
      Reference.reachabilityFence(value)
    }
  }

  @Test
  fun `views skip cleared references and support updates`() {
    for (map in maps<String, String>(HashingStrategy.canonical())) {
      map["cleared"] = "first"
      map["live"] = "second"
      val reference = reference(map, "cleared")
      reference.clear()
      assertThat(map.entries).hasSize(1)
      assertThat(map.keys).containsExactly("live")
      assertThat(map.values).containsExactly("second")

      val iterator = map.entries.iterator()
      assertThat(iterator.hasNext()).isTrue()
      assertThat(iterator.hasNext()).isTrue()
      val entry = iterator.next()
      assertThat(entry.key).isEqualTo("live")
      assertThat(entry.setValue("updated")).isEqualTo("second")
      assertThat(map["live"]).isEqualTo("updated")
      assertThat(iterator.hasNext()).isFalse()
      iterator.remove()
      assertThat(map["live"]).isNull()
      assertThatThrownBy { iterator.remove() }.isInstanceOf(IllegalStateException::class.java)

      reference.enqueue()
      processQueue(map)
      assertThat(map).isEmpty()
    }
  }

  @Test
  fun `iterator removal removes the last returned entry`() {
    for (map in maps<String, String>(HashingStrategy.canonical())) {
      map.putAll(mapOf("first" to "one", "second" to "two", "third" to "three"))
      val iterator = map.entries.iterator()
      val first = iterator.next()
      assertThat(iterator.hasNext()).isTrue()
      iterator.remove()
      assertThat(map[first.key]).isNull()
      val second = iterator.next()
      assertThat(map[second.key]).isEqualTo(second.value)
      assertThat(map.entries.remove(SimpleEntry(second.key, "wrong"))).isFalse()
      assertThat(map.entries.remove(SimpleEntry(second.key, second.value))).isTrue()
      assertThat(map).hasSize(1)
      map.keys.clear()
      assertThat(map).isEmpty()
    }
  }

  @Test
  @Suppress("UNCHECKED_CAST")
  fun `entry views reject absent entries with null values`() {
    for (map in maps<String, String>(HashingStrategy.canonical())) {
      val entry = SimpleEntry("missing", null) as MutableMap.MutableEntry<String, String>
      assertThat(map.entries.contains(entry)).isFalse()
      assertThat(map.entries.remove(entry)).isFalse()
    }
  }

  @Test
  fun `reentrant equality preserves the outer lookup`() {
    lateinit var map: ConcurrentMap<String, String>
    var enabled = false
    var nested = false
    val strategy = object : HashingStrategy<String> {
      override fun hashCode(key: String): Int = 0

      override fun equals(key1: String, key2: String): Boolean {
        if (enabled && !nested) {
          nested = true
          try {
            assertThat(map["nested"]).isEqualTo("nested value")
          }
          finally {
            nested = false
          }
        }
        return key1 == key2
      }
    }
    for (candidate in lookupMaps<String, String>(strategy)) {
      map = candidate
      map["nested"] = "nested value"
      map["target"] = "target value"
      enabled = true
      assertThat(map["target"]).isEqualTo("target value")
      enabled = false
    }
  }

  @Test
  fun `reference objects can be original keys`() {
    for (map in lookupMaps<WeakReference<Any>, String>(HashingStrategy.identity())) {
      val key = WeakReference(Any())
      map[key] = "value"
      key.clear()
      assertThat(map[key]).isEqualTo("value")
      assertThat(map.remove(key)).isEqualTo("value")
    }
  }

  @Test
  fun `concurrent updates preserve lookups during resizing`() {
    for (map in lookupMaps<Key, Int>(keyStrategy)) {
      val keys = List(256) { Key(it) }
      val start = CountDownLatch(1)
      val executor = Executors.newFixedThreadPool(4) { task ->
        Thread(task, "ConcurrentRefHashMapTest").apply { isDaemon = true }
      }
      try {
        val futures = List(4) {
          executor.submit {
            check(start.await(10, TimeUnit.SECONDS))
            for (key in keys) {
              map.compute(key) { _, value -> (value ?: 0) + 1 }
              check(map[Key(key.id)] != null) { "The map lost key ${key.id}" }
            }
          }
        }
        start.countDown()
        futures.forEach { it.get(10, TimeUnit.SECONDS) }
        assertThat(map).hasSize(keys.size)
        for (key in keys) {
          assertThat(map[Key(key.id)]).isEqualTo(4)
        }
        for (key in keys.take(248)) {
          assertThat(map.remove(Key(key.id))).isEqualTo(4)
        }
        for (key in keys.drop(248)) {
          assertThat(map[Key(key.id)]).isEqualTo(4)
        }
      }
      finally {
        start.countDown()
        executor.shutdownNow()
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue()
        Reference.reachabilityFence(keys)
      }
    }
  }

  @Test
  fun `get allocates no lookup state on a new thread`() {
    val bean = ManagementFactory.getThreadMXBean() as? ThreadMXBean
    assumeTrue(bean != null && bean.isThreadAllocatedMemorySupported && bean.isThreadAllocatedMemoryEnabled)
    for (map in lookupMaps<String, String>(HashingStrategy.canonical())) {
      map["key"] = "value"
      repeat(10_000) { check(map["key"] == "value") }
      val allocated = AtomicLong(-1)
      val thread = Thread {
        val id = Thread.currentThread().threadId()
        val before = bean!!.getThreadAllocatedBytes(id)
        check(map["key"] == "value")
        allocated.set(bean.getThreadAllocatedBytes(id) - before)
      }
      thread.start()
      thread.join(10_000)
      assertThat(thread.isAlive).isFalse()
      assertThat(allocated.get()).isZero()
    }
  }

  private fun <K : Any, V : Any> reference(map: ConcurrentMap<K, V>, key: K): Reference<K> {
    return storage(map).keys.single { it.get() === key }
  }

  @Suppress("UNCHECKED_CAST")
  private fun <K : Any, V : Any> valueReference(map: ConcurrentMap<K, V>): Reference<V> {
    return storage(map).values.single() as Reference<V>
  }

  @Suppress("UNCHECKED_CAST")
  private fun <K : Any, V : Any> storage(map: ConcurrentMap<K, V>): Map<Reference<K>, Any> {
    var type: Class<*>? = map.javaClass
    while (type != null && type.declaredFields.none { it.name == "myMap" }) {
      type = type.superclass
    }
    val field = checkNotNull(type).getDeclaredField("myMap")
    field.isAccessible = true
    return field.get(map) as Map<Reference<K>, Any>
  }

  private fun processQueue(map: ConcurrentMap<*, *>): Boolean = (map as ReferenceQueueable).processQueue()

  private fun <K : Any, V : Any> maps(strategy: HashingStrategy<in K>): List<ConcurrentMap<K, V>> = listOf(
    ConcurrentCollectionFactory.createConcurrentWeakMap(strategy),
    ConcurrentCollectionFactory.createConcurrentSoftMap(16, 0.75f, strategy),
  )

  private fun <K : Any, V : Any> referenceValueMaps(strategy: HashingStrategy<in K>): List<ConcurrentMap<K, V>> = listOf(
    ConcurrentCollectionFactory.createConcurrentWeakKeySoftValueMap(strategy),
    ConcurrentCollectionFactory.createConcurrentWeakKeyWeakValueMap(strategy),
    ConcurrentCollectionFactory.createConcurrentSoftKeySoftValueMap(strategy),
  )

  private fun <K : Any, V : Any> lookupMaps(strategy: HashingStrategy<in K>): List<ConcurrentMap<K, V>> =
    maps<K, V>(strategy) + referenceValueMaps(strategy)

  private class Key(val id: Int) : Comparable<Key> {
    override fun compareTo(other: Key): Int = id.compareTo(other.id)
  }

  companion object {
    private val keyStrategy: HashingStrategy<Key> = object : HashingStrategy<Key> {
      override fun hashCode(key: Key): Int = key.id % 8
      override fun equals(key1: Key, key2: Key): Boolean = key1.id == key2.id
    }
  }
}

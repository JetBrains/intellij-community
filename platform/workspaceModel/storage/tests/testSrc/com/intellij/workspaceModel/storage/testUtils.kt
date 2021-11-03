// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage

import com.google.common.collect.HashBiMap
import com.intellij.workspaceModel.storage.impl.*
import com.intellij.workspaceModel.storage.impl.containers.BidirectionalLongMultiMap
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import junit.framework.TestCase.*
import org.junit.Assert
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.function.BiPredicate
import kotlin.reflect.full.memberProperties

class TestEntityTypesResolver : EntityTypesResolver {
  private val pluginPrefix = "PLUGIN___"

  override fun getPluginId(clazz: Class<*>): String? = pluginPrefix + clazz.name
  override fun resolveClass(name: String, pluginId: String?): Class<*> {
    Assert.assertEquals(pluginPrefix + name, pluginId)
    if (name.startsWith("[")) return Class.forName(name)
    return javaClass.classLoader.loadClass(name)
  }
}

object SerializationRoundTripChecker {
  fun verifyPSerializationRoundTrip(storage: WorkspaceEntityStorage, virtualFileManager: VirtualFileUrlManager): ByteArray {
    storage as WorkspaceEntityStorageImpl
    storage.assertConsistency()

    val serializer = EntityStorageSerializerImpl(TestEntityTypesResolver(), virtualFileManager)

    val stream = ByteArrayOutputStream()
    serializer.serializeCache(stream, storage)

    val byteArray = stream.toByteArray()
    val deserialized = (serializer.deserializeCache(ByteArrayInputStream(byteArray)) as WorkspaceEntityStorageBuilderImpl).toStorage()
    deserialized.assertConsistency()

    assertStorageEquals(storage, deserialized)

    storage.assertConsistency()

    return byteArray
  }

  private fun assertStorageEquals(expected: WorkspaceEntityStorageImpl, actual: WorkspaceEntityStorageImpl) {
    // Assert entity data
    assertEquals(expected.entitiesByType.size(), actual.entitiesByType.size())
    for ((clazz, expectedEntityFamily) in expected.entitiesByType.entityFamilies.withIndex()) {
      val actualEntityFamily = actual.entitiesByType.entityFamilies[clazz]

      if (expectedEntityFamily == null || actualEntityFamily == null) {
        assertNull(expectedEntityFamily)
        assertNull(actualEntityFamily)
        continue
      }

      val expectedEntities = expectedEntityFamily.entities
      val actualEntities = actualEntityFamily.entities

      assertOrderedEquals(expectedEntities, actualEntities) { a, b -> a == null && b == null || a != null && b != null && a.equalsIgnoringEntitySource(b) }
    }

    // Assert refs
    assertMapsEqual(expected.refs.oneToOneContainer, actual.refs.oneToOneContainer)
    assertMapsEqual(expected.refs.oneToManyContainer, actual.refs.oneToManyContainer)
    assertMapsEqual(expected.refs.abstractOneToOneContainer, actual.refs.abstractOneToOneContainer)
    assertMapsEqual(expected.refs.oneToAbstractManyContainer, actual.refs.oneToAbstractManyContainer)
    assertMapsEqual(expected.indexes.virtualFileIndex.entityId2VirtualFileUrl, actual.indexes.virtualFileIndex.entityId2VirtualFileUrl)
    assertMapsEqual(expected.indexes.virtualFileIndex.vfu2EntityId, actual.indexes.virtualFileIndex.vfu2EntityId)
    // Just checking that all properties have been asserted
    assertEquals(4, RefsTable::class.memberProperties.size)

    // Assert indexes
    assertBiLongMultiMap(expected.indexes.softLinks.index, actual.indexes.softLinks.index)
    assertBiMap(expected.indexes.persistentIdIndex.index, actual.indexes.persistentIdIndex.index)
    // External index should not be persisted
    assertTrue(actual.indexes.externalMappings.isEmpty())
    // Just checking that all properties have been asserted
    assertEquals(5, StorageIndexes::class.memberProperties.size)
  }

  // Use UsefulTestCase.assertOrderedEquals in case it'd be used in this module
  private fun <T> assertOrderedEquals(actual: Iterable<T?>, expected: Iterable<T?>, comparator: (T?, T?) -> Boolean) {
    if (!equals(actual, expected, BiPredicate(comparator))) {
      val expectedString: String = expected.toString()
      val actualString: String = actual.toString()
      Assert.assertEquals("", expectedString, actualString)
      Assert.fail(
        "Warning! 'toString' does not reflect the difference.\nExpected: $expectedString\nActual: $actualString")
    }
  }

  private fun <T> equals(a1: Iterable<T?>, a2: Iterable<T?>, predicate: BiPredicate<in T?, in T?>): Boolean {
    val it1 = a1.iterator()
    val it2 = a2.iterator()
    while (it1.hasNext() || it2.hasNext()) {
      if (!it1.hasNext() || !it2.hasNext() || !predicate.test(it1.next(), it2.next())) {
        return false
      }
    }
    return true
  }

  private fun assertMapsEqual(expected: Map<*, *>, actual: Map<*, *>) {
    val local = HashMap(expected)
    for ((key, value) in actual) {
      val expectedValue = local.remove(key)
      if (expectedValue == null) {
        Assert.fail(String.format("Expected to find '%s' -> '%s' mapping but it doesn't exist", key, value))
      }
      if (expectedValue != value) {
        Assert.fail(
          String.format("Expected to find '%s' value for the key '%s' but got '%s'", expectedValue, key, value))
      }
    }
    if (local.isNotEmpty()) {
      Assert.fail("No mappings found for the following keys: " + local.keys)
    }
  }

  private fun <B> assertBiLongMultiMap(expected: BidirectionalLongMultiMap<B>, actual: BidirectionalLongMultiMap<B>) {
    val local = expected.copy()
    actual.keys.forEach { key ->
      val value = actual.getValues(key)
      val expectedValue = local.getValues(key)
      local.removeKey(key)

      assertOrderedEquals(expectedValue.sortedBy { it.toString() }, value.sortedBy { it.toString() }) { a, b -> a == b }
    }
    if (!local.isEmpty()) {
      Assert.fail("No mappings found for the following keys: " + local.keys)
    }
  }

  private fun <T> assertBiMap(expected: HashBiMap<EntityId, T>, actual: HashBiMap<EntityId, T>) {
    val local = HashBiMap.create(expected)
    for (key in actual.keys) {
      val value = actual.getValue(key)
      val expectedValue = local.remove(key)

      if (expectedValue == null) {
        Assert.fail(String.format("Expected to find '%s' -> '%s' mapping but it doesn't exist", key, value))
      }

      if (expectedValue != value) {
        Assert.fail(
          String.format("Expected to find '%s' value for the key '%s' but got '%s'", expectedValue, key, value))
      }
    }
    if (local.isNotEmpty()) {
      Assert.fail("No mappings found for the following keys: " + local.keys)
    }
  }
}

// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage

import com.intellij.util.containers.BidirectionalMap
import com.intellij.util.containers.BidirectionalMultiMap
import com.intellij.workspaceModel.storage.impl.*
import com.intellij.workspaceModel.storage.impl.containers.copy
import junit.framework.TestCase.*
import org.junit.Assert
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.*
import java.util.function.BiPredicate
import kotlin.reflect.full.memberProperties

class TestEntityTypesResolver : EntityTypesResolver {
  private val pluginPrefix = "PLUGIN___"

  override fun getPluginId(clazz: Class<*>): String? = pluginPrefix + clazz.name
  override fun resolveClass(name: String, pluginId: String?): Class<*> {
    Assert.assertEquals(pluginPrefix + name, pluginId)
    return javaClass.classLoader.loadClass(name)
  }
}

object SerializationRoundTripChecker {
  fun verifyPSerializationRoundTrip(storage: WorkspaceEntityStorage, virtualFileManager: VirtualFileUrlManager): ByteArray {
    storage as WorkspaceEntityStorageImpl

    val serializer = EntityStorageSerializerImpl(
      TestEntityTypesResolver(),
      virtualFileManager)

    val stream = ByteArrayOutputStream()
    serializer.serializeCache(stream, storage)

    val byteArray = stream.toByteArray()
    val deserialized = (serializer.deserializeCache(ByteArrayInputStream(byteArray)) as WorkspaceEntityStorageBuilderImpl).toStorage()

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

      assertOrderedEquals(expectedEntities, actualEntities)
    }

    // Assert refs
    assertMapsEqual(expected.refs.oneToOneContainer, actual.refs.oneToOneContainer)
    assertMapsEqual(expected.refs.oneToManyContainer, actual.refs.oneToManyContainer)
    assertMapsEqual(expected.refs.abstractOneToOneContainer, actual.refs.abstractOneToOneContainer)
    assertMapsEqual(expected.refs.oneToAbstractManyContainer, actual.refs.oneToAbstractManyContainer)
    // Just checking that all properties have been asserted
    assertEquals(4, RefsTable::class.memberProperties.size)

    // Assert indexes
    assertBiMultiMap(expected.indexes.softLinks, actual.indexes.softLinks)
    assertBiMultiMap(expected.indexes.virtualFileIndex.index, actual.indexes.virtualFileIndex.index)
    assertBiMap(expected.indexes.entitySourceIndex.index, actual.indexes.entitySourceIndex.index)
    assertBiMap(expected.indexes.persistentIdIndex.index, actual.indexes.persistentIdIndex.index)
    // External index should not be persisted
    assertTrue(actual.indexes.externalMappings.isEmpty())
    // Just checking that all properties have been asserted
    assertEquals(5, StorageIndexes::class.memberProperties.size)
  }

  // Use UsefulTestCase.assertOrderedEquals in case it'd be used in this module
  private fun <T> assertOrderedEquals(actual: Iterable<T?>, expected: Iterable<T?>) {
    if (!equals(actual, expected, BiPredicate { a: T?, b: T? -> a == b })) {
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

  private fun <A, B> assertBiMultiMap(expected: BidirectionalMultiMap<A, B>, actual: BidirectionalMultiMap<A, B>) {
    val local = expected.copy()
    for (key in actual.keys) {
      val value = actual.getValues(key)
      val expectedValue = local.getValues(key)
      local.removeKey(key)

      assertOrderedEquals(expectedValue, value)
    }
    if (!local.isEmpty) {
      Assert.fail("No mappings found for the following keys: " + local.keys)
    }
  }

  private fun <A, B> assertBiMap(expected: BidirectionalMap<A, B>, actual: BidirectionalMap<A, B>) {
    val local = expected.copy()
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

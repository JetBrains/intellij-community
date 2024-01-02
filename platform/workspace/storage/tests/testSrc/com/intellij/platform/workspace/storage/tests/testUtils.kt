// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests

import com.google.common.collect.HashBiMap
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.impl.*
import com.intellij.platform.workspace.storage.impl.containers.BidirectionalLongMultiMap
import com.intellij.platform.workspace.storage.impl.serialization.EntityStorageSerializerImpl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.workspaceModel.ide.impl.WorkspaceModelCacheSerializer.PluginAwareEntityTypesResolver
import junit.framework.TestCase.*
import org.junit.Assert
import java.nio.file.Files
import java.util.function.BiPredicate
import kotlin.reflect.full.memberProperties

abstract class BaseSerializationChecker {

  open fun verifyPSerializationRoundTrip(storage: EntityStorage, virtualFileManager: VirtualFileUrlManager): ByteArray {
    storage as ImmutableEntityStorageImpl
    storage.assertConsistency()

    val serializer = EntityStorageSerializerImpl(PluginAwareEntityTypesResolver, virtualFileManager)

    val file = Files.createTempFile("", "")
    try {
      serializer.serializeCache(file, storage)

      val deserialized = (serializer.deserializeCache(file).getOrThrow() as MutableEntityStorageImpl)
        .toSnapshot() as ImmutableEntityStorageImpl
      deserialized.assertConsistency()

      assertStorageEquals(storage, deserialized)

      storage.assertConsistency()
      return Files.readAllBytes(file)
    }
    finally {
      Files.deleteIfExists(file)
    }
  }

  internal fun assertStorageEquals(expected: ImmutableEntityStorageImpl, actual: ImmutableEntityStorageImpl) {
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

      assertEntitiesEqual(expectedEntities, actualEntities)
    }

    // Assert refs
    assertMapsEqual(expected.refs.oneToOneContainer.getInternalStructure(),
                    actual.refs.oneToOneContainer.getInternalStructure())
    assertMapsEqual(expected.refs.oneToManyContainer.getInternalStructure(),
                    actual.refs.oneToManyContainer.getInternalStructure())
    assertMapsEqual(expected.refs.abstractOneToOneContainer.getInternalStructure(),
                    actual.refs.abstractOneToOneContainer.getInternalStructure())
    assertMapsEqual(expected.refs.oneToAbstractManyContainer.getInternalStructure(),
                    actual.refs.oneToAbstractManyContainer.getInternalStructure())
    assertMapsEqual(expected.indexes.virtualFileIndex.entityId2VirtualFileUrl, actual.indexes.virtualFileIndex.entityId2VirtualFileUrl)
    assertMapsEqual(expected.indexes.virtualFileIndex.vfu2EntityId, actual.indexes.virtualFileIndex.vfu2EntityId)
    // Just checking that all properties have been asserted
    assertEquals(4, RefsTable::class.memberProperties.size)

    // Assert indexes
    assertBiLongMultiMap(expected.indexes.softLinks.index, actual.indexes.softLinks.index)
    assertBiMap(expected.indexes.symbolicIdIndex.index, actual.indexes.symbolicIdIndex.index)
    // External index should not be persisted
    assertTrue(actual.indexes.externalMappings.isEmpty())
    // Just checking that all properties have been asserted
    assertEquals(5, StorageIndexes::class.memberProperties.size)
  }

  protected abstract fun assertEntitiesEqual(expected: List<WorkspaceEntityData<*>?>, actual: List<WorkspaceEntityData<*>?>)

  // Use UsefulTestCase.assertOrderedEquals in case it'd be used in this module
  protected fun <T> assertOrderedEquals(actual: Iterable<T?>, expected: Iterable<T?>, comparator: (T?, T?) -> Boolean) {
    if (!equals(actual, expected, BiPredicate(comparator))) {
      val expectedString: String = expected.toString()
      val actualString: String = actual.toString()
      Assert.assertEquals("", expectedString, actualString)
      Assert.fail(
        "Warning! 'toString' does not reflect the difference.\nExpected: $expectedString\nActual: $actualString")
    }
  }

  protected fun <T> equals(a1: Iterable<T?>, a2: Iterable<T?>, predicate: BiPredicate<in T?, in T?>): Boolean {
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
      if (!valuesComparator(expectedValue, value)) {
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

      assertOrderedEquals(expectedValue.sortedBy { it.toString() }, value.sortedBy { it.toString() }, ::valuesComparator)
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

      if (!valuesComparator(expectedValue, value)) {
        Assert.fail(
          String.format("Expected to find '%s' value for the key '%s' but got '%s'", expectedValue, key, value))
      }
    }
    if (local.isNotEmpty()) {
      Assert.fail("No mappings found for the following keys: " + local.keys)
    }
  }

  protected open fun <T> valuesComparator(expected: T, actual: T): Boolean = expected == actual
}

object SerializationRoundTripChecker: BaseSerializationChecker() {
  override fun assertEntitiesEqual(expected: List<WorkspaceEntityData<*>?>, actual: List<WorkspaceEntityData<*>?>) =
    assertOrderedEquals(actual, expected) { a, b -> a == null && b == null || a != null && b != null && a.equalsIgnoringEntitySource(b) }
}

/**
 * Return same entity, but in different entity storage. Fail if no entity
 */
internal fun <T : WorkspaceEntity> T.from(storage: EntityStorage): T {
  return this.createReference<T>().resolve(storage)!!
}
